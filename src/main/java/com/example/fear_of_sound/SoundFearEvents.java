package com.example.fear_of_sound;

import com.example.fear_of_sound.ai.FearGoal;
import com.example.fear_of_sound.ai.PlayDeadGoal;
import com.example.fear_of_sound.config.AdvancedFearOverrideRegistry;
import com.example.fear_of_sound.config.FearofSoundConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = FearofSoundMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoundFearEvents {
    private static int attachScanTicker = 0;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!FearofSoundMod.isSoundAttractLoaded && event.phase == TickEvent.Phase.END && !event.level.isClientSide()) {
            if (event.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                SoundTracker.pruneIrrelevantSounds(serverLevel);
            }
        }

        if (event.phase == TickEvent.Phase.END && !event.level.isClientSide() && event.level instanceof net.minecraft.server.level.ServerLevel server) {
            attachScanTicker++;
            if (attachScanTicker % 40 == 0) { // every 2 seconds
                java.util.List<PathfinderMob> mobs = server.getEntitiesOfClass(PathfinderMob.class, server.getWorldBorder().getCollisionShape().bounds());
                int processed = 0;
                for (PathfinderMob pMob : mobs) {
                    if (processed++ > 128) break; // cap per scan
                    // skip bypassed
                    boolean bypassed = false;
                    for (FearofSoundConfig.BypassDefinition bypass : FearofSoundConfig.BYPASSED_ENTITIES_CACHE) {
                        if (bypass.customName() != null && pMob.hasCustomName() && pMob.getCustomName().getString().equals(bypass.customName())) { bypassed = true; break; }
                        if (bypass.nbt() != null) {
                            CompoundTag mobNbt = new CompoundTag();
                            pMob.saveWithoutId(mobNbt);
                            if (NbtUtils.compareNbt(bypass.nbt(), mobNbt, true)) { bypassed = true; break; }
                        }
                    }
                    if (bypassed) continue;
                    ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(pMob.getType());
                    if (entityId == null) continue;
                    String entityIdStr = entityId.toString();
                    boolean needFear = FearofSoundConfig.AFFECTED_ENTITIES_CACHE.contains(entityIdStr) || AdvancedFearOverrideRegistry.hasAnyRuleFor(pMob.getType());
                    if (!needFear) continue;
                    boolean goalExists = pMob.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof FearGoal);
                    if (!goalExists) {
                        double moveSpeed = FearofSoundConfig.COMMON.mobMoveSpeed.get();
                        pMob.goalSelector.addGoal(0, new FearGoal(pMob, moveSpeed));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof PathfinderMob pMob) {
            for (FearofSoundConfig.BypassDefinition bypass : FearofSoundConfig.BYPASSED_ENTITIES_CACHE) {
                if (bypass.customName() != null && pMob.hasCustomName() && pMob.getCustomName().getString().equals(bypass.customName())) {
                    return;
                }
                if (bypass.nbt() != null) {
                    CompoundTag mobNbt = new CompoundTag();
                    pMob.saveWithoutId(mobNbt);
                    if (NbtUtils.compareNbt(bypass.nbt(), mobNbt, true)) {
                        return;
                    }
                }
            }
            ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(pMob.getType());
            if (entityId == null) return;
            String entityIdStr = entityId.toString();
            if (FearofSoundConfig.PLAY_DEAD_ENTITIES_CACHE.contains(entityIdStr)) {
                boolean goalExists = pMob.goalSelector.getAvailableGoals().stream()
                        .anyMatch(g -> g.getGoal() instanceof PlayDeadGoal);
                if (!goalExists) {
                    pMob.goalSelector.addGoal(0, new PlayDeadGoal(pMob));
                }
            }
            else if (FearofSoundConfig.AFFECTED_ENTITIES_CACHE.contains(entityIdStr) || AdvancedFearOverrideRegistry.hasAnyRuleFor(pMob.getType())) {
                boolean goalExists = pMob.goalSelector.getAvailableGoals().stream()
                        .anyMatch(g -> g.getGoal() instanceof FearGoal);
                if (!goalExists) {
                    double moveSpeed = FearofSoundConfig.COMMON.mobMoveSpeed.get();
                    pMob.goalSelector.addGoal(0, new FearGoal(pMob, moveSpeed));
                }
            }
        }
    }
}