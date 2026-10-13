package com.example.fear_of_sound.integration;

import com.example.fear_of_sound.FearofSoundMod;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScentAttractIntegration {
    public record Evidence(BlockPos position, UUID owner, String sourceType, float strength, long timestamp) {
    }

    private record QueryKey(ResourceLocation dimension, long chunk) {
    }

    private record Binding(Method getCapability, Method resolveCapability, Field managerCapability, Method nodesInArea,
                           Method position, Method timestamp, Method strength, Method owner, Method sourceType) {
    }

    private static final Cache<QueryKey, List<Evidence>> CACHE = Caffeine.newBuilder()
            .maximumSize(8192)
            .expireAfterWrite(250, TimeUnit.MILLISECONDS)
            .build();
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static volatile Binding binding;
    private static volatile boolean resolutionAttempted;

    private ScentAttractIntegration() {
    }

    public static void clearCache() {
        CACHE.invalidateAll();
    }

    public static List<Evidence> sample(Mob mob, long nowTick, int maxAgeTicks, double radius) {
        if (mob == null || !mob.isAlive() || mob.level().isClientSide || radius <= 0.0
                || !Double.isFinite(radius) || maxAgeTicks <= 0
                || !FearofSoundMod.isSoundAttractLoaded || !ModList.get().isLoaded("soundattract")) {
            return List.of();
        }
        BlockPos mobPos = mob.blockPosition();
        QueryKey key = new QueryKey(mob.level().dimension().location(), new ChunkPos(mobPos).toLong());
        List<Evidence> evidence = CACHE.get(key, ignored -> read(mob));
        double radiusSqr = radius * radius;
        List<Evidence> fresh = new ArrayList<>(4);
        for (Evidence node : evidence) {
            long age = nowTick - node.timestamp();
            if (age < 0 || age > maxAgeTicks || mob.position().distanceToSqr(Vec3.atCenterOf(node.position())) > radiusSqr) continue;
            fresh.add(node);
        }
        fresh.sort(Comparator.comparingDouble(Evidence::strength).reversed());
        List<Evidence> result = fresh.size() <= 4 ? List.copyOf(fresh) : List.copyOf(fresh.subList(0, 4));
        return result;
    }

    private static List<Evidence> read(Mob mob) {
        Binding resolved = resolve();
        if (resolved == null) return List.of();
        try {
            Object optional = resolved.getCapability().invoke(mob.level(), resolved.managerCapability().get(null));
            Object resolvedOptional = resolved.resolveCapability().invoke(optional);
            if (!(resolvedOptional instanceof Optional<?> value) || value.isEmpty()) return List.of();
            Object manager = value.get();
            Object rawNodes = resolved.nodesInArea().invoke(manager, new ChunkPos(mob.blockPosition()));
            if (!(rawNodes instanceof Iterable<?> nodes)) return List.of();

            List<Evidence> collected = new ArrayList<>(8);
            int inspected = 0;
            for (Object node : nodes) {
                if (node == null || inspected++ >= 256) break;
                Object rawPosition = resolved.position().invoke(node);
                Object rawTimestamp = resolved.timestamp().invoke(node);
                Object rawStrength = resolved.strength().invoke(node);
                Object rawOwner = resolved.owner().invoke(node);
                Object rawSource = resolved.sourceType().invoke(node);
                if (!(rawPosition instanceof Vec3 position) || !(rawTimestamp instanceof Long timestamp)
                        || !(rawStrength instanceof Number strengthValue) || !Double.isFinite(position.x)
                        || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
                    continue;
                }
                float strength = strengthValue.floatValue();
                if (!Float.isFinite(strength) || strength <= 0.0F) continue;
                UUID owner = rawOwner instanceof UUID id ? id : null;
                String source = rawSource == null ? "unknown" : rawSource.toString();
                collected.add(new Evidence(BlockPos.containing(position).immutable(), owner, source, strength, timestamp));
            }
            collected.sort(Comparator.comparingDouble(Evidence::strength).reversed());
            return collected.size() <= 64 ? List.copyOf(collected) : List.copyOf(collected.subList(0, 64));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warnOnce(e);
            return List.of();
        }
    }

    private static Binding resolve() {
        if (binding != null || resolutionAttempted) return binding;
        synchronized (ScentAttractIntegration.class) {
            if (binding != null || resolutionAttempted) return binding;
            resolutionAttempted = true;
            try {
                ClassLoader loader = ScentAttractIntegration.class.getClassLoader();
                Class<?> managerClass = Class.forName("com.example.soundattract.scents.ScentManager", false, loader);
                Class<?> nodeClass = Class.forName("com.example.soundattract.scents.ScentNode", false, loader);
                Field capability = managerClass.getField("INSTANCE");
                Method getCapability = mobLevelGetCapability();
                Class<?> lazyOptionalClass = Class.forName("net.minecraftforge.common.util.LazyOptional", false, loader);
                binding = new Binding(getCapability, lazyOptionalClass.getMethod("resolve"), capability,
                        managerClass.getMethod("getNodesInArea", ChunkPos.class),
                        nodeClass.getMethod("getPosition"), nodeClass.getMethod("getTimestamp"),
                        nodeClass.getMethod("getStrength"), nodeClass.getMethod("getOwnerUUID"),
                        nodeClass.getMethod("getSourceType"));
            } catch (ReflectiveOperationException | LinkageError e) {
                warnOnce(e);
            }
            return binding;
        }
    }

    private static Method mobLevelGetCapability() throws NoSuchMethodException {
        return net.minecraft.world.level.Level.class.getMethod("getCapability", Capability.class);
    }

    private static void warnOnce(Throwable error) {
        if (WARNED.compareAndSet(false, true)) {
            FearofSoundMod.LOGGER.warn("Sound Attract scent adapter unavailable; scent awareness is disabled safely: {}", error.toString());
        }
    }
}
