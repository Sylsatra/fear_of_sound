package com.example.fear_of_sound.integration;

import com.example.fear_of_sound.FearofSoundMod;
import com.example.fear_of_sound.SoundTracker;
import com.example.fear_of_sound.config.FearofSoundConfig;
import com.example.fear_of_sound.ai.FearSignals;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
 

import java.util.List;

@Mod.EventBusSubscriber(modid = FearofSoundMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoundAttractLOSIntegration {


    private static final double LOS_SOUND_WEIGHT = 10.0;
    private static boolean SA_MISSING_WARNED = false;
    private static final boolean FOV_EVENTS_PRESENT = isFovEventsPresent();
    private static boolean SA_PRESENT_LOGGED = false;

    private static final boolean USE_MOB_TICK = true;

    private static final java.util.Map<java.util.UUID, Integer> PLAYER_CURSORS = new java.util.concurrent.ConcurrentHashMap<>();

    private static long LAST_TICK_FOR_CAP = Long.MIN_VALUE;
    private static int PROCESSED_THIS_TICK = 0;

    static {

    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (USE_MOB_TICK) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;

        if (FearofSoundConfig.AFFECTED_ENTITIES_CACHE.isEmpty()) return;

        if (!FOV_EVENTS_PRESENT) {
            if (!SA_MISSING_WARNED) {
                SA_MISSING_WARNED = true;
            }
        } else if (!SA_PRESENT_LOGGED) {
            SA_PRESENT_LOGGED = true;
        }


        String dimKey = level.dimension().location().toString();

        int simDistBlocks = level.getServer().getPlayerList().getViewDistance() * 16;
        int SCAN_RANGE = FOV_EVENTS_PRESENT
                ? Math.min(simDistBlocks, Math.max(16, FearofSoundConfig.LOS_SCAN_RANGE_CAP_CACHE))
                : 32;

        int interval = Math.max(1, FearofSoundConfig.LOS_CHECK_INTERVAL_TICKS_CACHE);
        int cap = FearofSoundConfig.LOS_PER_TICK_CAP_CACHE;
        List<net.minecraft.server.level.ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        int mobsFoundTotal = 0;
        int affectedChecked = 0;
        java.util.Set<java.util.UUID> processedMobIds = new java.util.HashSet<>();
        int processedThisTick = 0;

        final int playerCount = Math.max(1, players.size());
        final int perPlayerCap = (cap > 0) ? Math.max(1, cap / playerCount) : -1;

        

        for (net.minecraft.server.level.ServerPlayer p : players) {
            int processedForPlayer = 0;
            net.minecraft.world.phys.AABB box = p.getBoundingBox().inflate(SCAN_RANGE);

            List<PathfinderMob> mobsAll = level.getEntitiesOfClass(
                    PathfinderMob.class,
                    box,
                    m -> FearofSoundConfig.AFFECTED_ENTITY_TYPES_CACHE.contains(m.getType())
            );
            mobsFoundTotal += mobsAll.size();

            int n = mobsAll.size();
            int start = (n > 0) ? PLAYER_CURSORS.getOrDefault(p.getUUID(), 0) % n : 0;
            for (int i = 0; i < n; i++) {
                PathfinderMob mob = mobsAll.get((start + i) % n);
                if (!processedMobIds.add(mob.getUUID())) continue;

            affectedChecked++;

            


            long time = level.getGameTime();
            int hash = mob.getUUID().hashCode() & 0x7fffffff;
            if (((time + hash) % interval) != 0) continue;

            if (cap > 0 && processedThisTick >= cap) break;
            if (perPlayerCap > 0 && processedForPlayer >= perPlayerCap) break;


            int LOS_COOLDOWN = FearofSoundConfig.LOS_COOLDOWN_CACHE;


            Player nearest = p;
            if (nearest == null || !nearest.isAlive()) continue;





            boolean detectable;
            double saEffectiveRange = -1.0;
            if (FOV_EVENTS_PRESENT) {

                double earlyRange = Math.min(32.0, FearofSoundConfig.LOS_SCAN_RANGE_CAP_CACHE);
                if (mob.distanceToSqr(p) > (earlyRange * earlyRange)) continue;

                net.minecraft.world.phys.Vec3 look = mob.getLookAngle().normalize();
                net.minecraft.world.phys.Vec3 toPlayer = p.position().add(0, p.getEyeHeight() / 2.0, 0)
                        .subtract(mob.getEyePosition()).normalize();
                if (look.dot(toPlayer) < 0.5) {
                    continue;
                }
                detectable = com.example.soundattract.StealthDetectionEvents.canMobDetectPlayer((net.minecraft.world.entity.Mob) mob, nearest);
                if (!detectable) continue;
                saEffectiveRange = com.example.soundattract.StealthDetectionEvents.getRealisticStealthDetectionRange(nearest, (net.minecraft.world.entity.Mob) mob, level);
                
            } else {
                detectable = isTargetInFovFallback(mob, nearest, true);
                if (!detectable) continue;
                
            }


            BlockPos playerPos = BlockPos.containing(nearest.position());


            int radiusToUse = FearofSoundConfig.LOS_RANGE_CACHE;
            if (FOV_EVENTS_PRESENT && saEffectiveRange > 0) {
                int saRangeInt = (int) Math.max(0, Math.round(saEffectiveRange));
                radiusToUse = Math.max(1, saRangeInt);
            }

            FearSignals.trigger(mob, playerPos, radiusToUse, level.getGameTime());
            if (!FearofSoundMod.isSoundAttractLoaded) {
                SoundTracker.addSound(
                        null,
                        playerPos,
                        dimKey,
                        radiusToUse,
                        LOS_SOUND_WEIGHT,
                        LOS_COOLDOWN
                );
            } else {
                ResourceLocation fosLosId = new ResourceLocation(FearofSoundMod.MOD_ID, "los");
                ResourceLocation dimId = level.dimension().location();
                SoundAttractIntegration.sendSound(
                        fosLosId,
                        nearest.getX(),
                        nearest.getY(),
                        nearest.getZ(),
                        dimId,
                        java.util.Optional.of(nearest.getUUID()),
                        radiusToUse,
                        LOS_SOUND_WEIGHT
                );
            }
            processedThisTick++;
            processedForPlayer++;
            if (cap > 0 && processedThisTick >= cap) break;
            if (perPlayerCap > 0 && processedForPlayer >= perPlayerCap) break;
            
            }
            if (n > 0) PLAYER_CURSORS.put(p.getUUID(), (start + processedForPlayer) % n);
        }

        if (cap > 0 && processedThisTick >= cap) return;
        
    }

    @SubscribeEvent
    public static void onMobTick(LivingEvent.LivingTickEvent event) {
        if (!USE_MOB_TICK) return;
        if (!(event.getEntity() instanceof PathfinderMob mob)) return;
        if (mob.level().isClientSide()) return;
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (FearofSoundConfig.AFFECTED_ENTITY_TYPES_CACHE.isEmpty()) return;
        if (!FearofSoundConfig.AFFECTED_ENTITY_TYPES_CACHE.contains(mob.getType())) return;


        int interval = Math.max(1, FearofSoundConfig.LOS_CHECK_INTERVAL_TICKS_CACHE);
        int cap = FearofSoundConfig.LOS_PER_TICK_CAP_CACHE;
        long time = level.getGameTime();
        if (LAST_TICK_FOR_CAP != time) { LAST_TICK_FOR_CAP = time; PROCESSED_THIS_TICK = 0; }
        if (cap > 0 && PROCESSED_THIS_TICK >= cap) return;
        int hash = mob.getUUID().hashCode() & 0x7fffffff;
        if (((time + hash) % interval) != 0) return;


        int simDistBlocks = level.getServer().getPlayerList().getViewDistance() * 16;
        int SCAN_RANGE = FOV_EVENTS_PRESENT
                ? Math.min(simDistBlocks, Math.max(16, FearofSoundConfig.LOS_SCAN_RANGE_CAP_CACHE))
                : 32;


        Player nearest = level.getNearestPlayer(mob, SCAN_RANGE);
        if (nearest == null || !nearest.isAlive()) return;


        double earlyRange = Math.min(32.0, FearofSoundConfig.LOS_SCAN_RANGE_CAP_CACHE);
        if (mob.distanceToSqr(nearest) > (earlyRange * earlyRange)) return;


        net.minecraft.world.phys.Vec3 look = mob.getLookAngle().normalize();
        net.minecraft.world.phys.Vec3 toPlayer = nearest.position().add(0, nearest.getEyeHeight() / 2.0, 0)
                .subtract(mob.getEyePosition()).normalize();
        if (look.dot(toPlayer) < 0.5) return;


        boolean detectable;
        double saEffectiveRange = -1.0;
        if (FOV_EVENTS_PRESENT) {
            detectable = com.example.soundattract.StealthDetectionEvents.canMobDetectPlayer((net.minecraft.world.entity.Mob) mob, nearest);
            if (!detectable) return;
            saEffectiveRange = com.example.soundattract.StealthDetectionEvents.getRealisticStealthDetectionRange(nearest, (net.minecraft.world.entity.Mob) mob, level);
        } else {
            detectable = isTargetInFovFallback(mob, nearest, true);
            if (!detectable) return;
        }


        String dimKey = level.dimension().location().toString();
        BlockPos playerPos = BlockPos.containing(nearest.position());
        int radiusToUse = FearofSoundConfig.LOS_RANGE_CACHE;
        if (FOV_EVENTS_PRESENT && saEffectiveRange > 0) {
            int saRangeInt = (int) Math.max(0, Math.round(saEffectiveRange));
            radiusToUse = Math.max(1, saRangeInt);
        }
        int LOS_COOLDOWN = FearofSoundConfig.LOS_COOLDOWN_CACHE;

        FearSignals.trigger(mob, playerPos, radiusToUse, level.getGameTime());
        if (!FearofSoundMod.isSoundAttractLoaded) {
            SoundTracker.addSound(
                    null,
                    playerPos,
                    dimKey,
                    radiusToUse,
                    LOS_SOUND_WEIGHT,
                    LOS_COOLDOWN
            );
        } else {
            ResourceLocation fosLosId = new ResourceLocation(FearofSoundMod.MOD_ID, "los");
            ResourceLocation dimId = level.dimension().location();
            SoundAttractIntegration.sendSound(
                    fosLosId,
                    nearest.getX(),
                    nearest.getY(),
                    nearest.getZ(),
                    dimId,
                    java.util.Optional.of(nearest.getUUID()),
                    radiusToUse,
                    LOS_SOUND_WEIGHT
            );
        }
        PROCESSED_THIS_TICK++;
    }

    private static boolean isFovEventsPresent() {
        try {
            Class.forName("com.example.soundattract.FovEvents", false, SoundAttractLOSIntegration.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }




    private static boolean isTargetInFovFallback(net.minecraft.world.entity.Mob looker,
                                                 net.minecraft.world.entity.Entity target,
                                                 boolean checkObstructions) {

        double horizontalFovDegrees = 120.0;
        double verticalFovDegrees = 90.0;

        if (checkObstructions && !hasVisualLineOfSightFallback(looker, target)) {
            return false;
        }

        return isWithinFieldOfViewFallback(looker, target, horizontalFovDegrees, verticalFovDegrees);
    }

    private static boolean hasVisualLineOfSightFallback(net.minecraft.world.entity.Mob looker,
                                                        net.minecraft.world.entity.Entity target) {
        net.minecraft.world.phys.Vec3 startVec = looker.getEyePosition();
        net.minecraft.world.phys.Vec3 endVec = target.position().add(0, target.getBbHeight() / 2.0, 0);
        net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
                startVec,
                endVec,
                net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                looker
        );
        net.minecraft.world.phys.BlockHitResult hitResult = looker.level().clip(context);
        return hitResult.getType() == net.minecraft.world.phys.BlockHitResult.Type.MISS;
    }

    private static boolean isWithinFieldOfViewFallback(net.minecraft.world.entity.Mob looker,
                                                       net.minecraft.world.entity.Entity target,
                                                       double horizontalFovDegrees,
                                                       double verticalFovDegrees) {
        net.minecraft.world.phys.Vec3 lookVector = looker.getLookAngle();
        net.minecraft.world.phys.Vec3 toTargetVector = target.position()
                .add(0, target.getEyeHeight() / 2.0, 0)
                .subtract(looker.getEyePosition())
                .normalize();


        net.minecraft.world.phys.Vec3 lookHorizontal = new net.minecraft.world.phys.Vec3(lookVector.x, 0, lookVector.z).normalize();
        net.minecraft.world.phys.Vec3 targetHorizontal = new net.minecraft.world.phys.Vec3(toTargetVector.x, 0, toTargetVector.z).normalize();
        double dotHorizontal = lookHorizontal.dot(targetHorizontal);
        double angleHorizontal = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dotHorizontal))));
        if (angleHorizontal > horizontalFovDegrees / 2.0) {
            return false;
        }


        double pitchLook = Math.toDegrees(Math.asin(lookVector.y));
        double pitchTarget = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, toTargetVector.y))));
        double angleVertical = Math.abs(pitchTarget - pitchLook);
        return angleVertical <= verticalFovDegrees / 2.0;
    }
}
