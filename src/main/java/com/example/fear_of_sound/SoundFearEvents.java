package com.example.fear_of_sound;

import com.example.fear_of_sound.ai.FearGoal;
import com.example.fear_of_sound.ai.FearSignals;
import com.example.fear_of_sound.config.AdvancedFearOverrideRegistry;
import com.example.fear_of_sound.config.FearofSoundConfig;
import com.example.fear_of_sound.integration.ScentAttractIntegration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mod.EventBusSubscriber(modid = FearofSoundMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoundFearEvents {
    private record MobKey(UUID id, ResourceLocation dimension) {
    }

    private static final int RECONCILE_PER_TICK = 64;
    private static final ConcurrentHashMap<MobKey, WeakReference<PathfinderMob>> LOADED_MOBS = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<MobKey> RECONCILE_QUEUE = new ConcurrentLinkedQueue<>();
    private static final java.util.Set<MobKey> QUEUED = ConcurrentHashMap.newKeySet();

    private SoundFearEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof PathfinderMob mob) || !mob.isAlive()) return;
        MobKey key = new MobKey(mob.getUUID(), level.dimension().location());
        LOADED_MOBS.put(key, new WeakReference<>(mob));
        reconcile(mob);
        enqueue(key);
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof PathfinderMob mob) || !(event.getLevel() instanceof ServerLevel level)) return;
        mob.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(goal -> goal instanceof FearGoal)
                .toList()
                .forEach(mob.goalSelector::removeGoal);
        FearSignals.clear(mob);
        MobKey key = new MobKey(mob.getUUID(), level.dimension().location());
        LOADED_MOBS.remove(key);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (int i = 0; i < RECONCILE_PER_TICK; i++) {
            MobKey key = RECONCILE_QUEUE.poll();
            if (key == null) return;
            QUEUED.remove(key);
            WeakReference<PathfinderMob> reference = LOADED_MOBS.get(key);
            if (reference == null) continue;
            PathfinderMob mob = reference.get();
            if (mob == null || !mob.isAlive() || mob.level().isClientSide) {
                LOADED_MOBS.remove(key, reference);
                continue;
            }
            reconcile(mob);
            enqueue(key);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOADED_MOBS.clear();
        RECONCILE_QUEUE.clear();
        QUEUED.clear();
        FearSignals.clear();
        FearGoal.clearTransientCaches();
        ScentAttractIntegration.clearCache();
        SoundTracker.clearTransientState();
    }

    private static void enqueue(MobKey key) {
        if (QUEUED.add(key)) RECONCILE_QUEUE.offer(key);
    }

    private static void reconcile(PathfinderMob mob) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (entityId == null) return;
        String id = entityId.toString();
        boolean bypassed = isBypassed(mob);
        boolean eligible = !bypassed && (FearofSoundConfig.PLAY_DEAD_ENTITIES_CACHE.contains(id)
                || FearofSoundConfig.AFFECTED_ENTITIES_CACHE.contains(id)
                || AdvancedFearOverrideRegistry.hasAnyRuleFor(mob.getType()));

        List<Goal> existing = mob.goalSelector.getAvailableGoals().stream()
                .map(wrapped -> wrapped.getGoal())
                .filter(goal -> goal instanceof FearGoal)
                .toList();
        if (!eligible) {
            existing.forEach(mob.goalSelector::removeGoal);
            return;
        }
        double configuredSpeed = FearofSoundConfig.COMMON.mobMoveSpeed.get();
        boolean matchingGoal = existing.stream().anyMatch(goal -> goal instanceof FearGoal fearGoal
                && fearGoal.usesMoveSpeed(configuredSpeed));
        if (matchingGoal && existing.size() == 1) return;
        existing.forEach(mob.goalSelector::removeGoal);
        mob.goalSelector.addGoal(0, new FearGoal(mob, configuredSpeed));
    }

    private static boolean isBypassed(PathfinderMob mob) {
        for (FearofSoundConfig.BypassDefinition bypass : FearofSoundConfig.BYPASSED_ENTITIES_CACHE) {
            if (bypass.customName() != null && mob.hasCustomName()
                    && bypass.customName().equals(mob.getCustomName().getString())) return true;
            if (bypass.nbt() != null) {
                CompoundTag mobNbt = new CompoundTag();
                mob.saveWithoutId(mobNbt);
                if (NbtUtils.compareNbt(bypass.nbt(), mobNbt, true)) return true;
            }
        }
        return false;
    }
}
