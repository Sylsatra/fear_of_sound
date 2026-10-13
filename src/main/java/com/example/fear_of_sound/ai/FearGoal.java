package com.example.fear_of_sound.ai;

import com.example.fear_of_sound.SoundSystemFacade;
import com.example.fear_of_sound.SoundTracker;
import com.example.fear_of_sound.config.FearofSoundConfig;
import com.example.fear_of_sound.integration.SoundAttractLOSIntegration;
import com.example.fear_of_sound.integration.ScentAttractIntegration;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FearGoal extends Goal {
    private enum State {
        CALM,
        ALERT,
        SEEK_COVER,
        FREEZE,
        LEGACY_FAINT,
        ESCAPE,
        RECOVER
    }

    private record Threat(String key, Vec3 position, double weight, long expiresAt, boolean visible) {
    }

    private record SightKey(ResourceLocation dimension, long fromX, long fromY, long fromZ,
                            long toX, long toY, long toZ) {
    }

    private record SightValue(boolean clear, long tick) {
    }

    private record RefugeKey(ResourceLocation dimension, long position) {
    }

    private record RefugeReservation(java.util.UUID owner, long expiresAtTick) {
    }

    private record ScoredCover(Vec3 position, Path path, double score) {
    }

    private static final Cache<SightKey, SightValue> SIGHT_CACHE = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterWrite(3, TimeUnit.SECONDS)
            .build();
    private static final Map<RefugeKey, RefugeReservation> REFUGE_RESERVATIONS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_THREATS = 8;
    private static final int MAX_SIGHT_TARGETS = 24;
    private static final int MAX_COVER_CANDIDATES = 12;
    private static final long THREAT_MEMORY_TICKS = 120;
    private static final long MAX_SOUND_MEMORY_TICKS = 600;
    private static final long MAX_EPISODE_TICKS = 400;
    private static final long MAX_TRAVEL_TICKS = 160;
    private static final long MAX_FREEZE_TICKS = 200;
    private static final long SCENT_MEMORY_TICKS = 40;
    private static final int SCENT_ESCALATION_OBSERVATIONS = 3;

    private final PathfinderMob mob;
    private final double moveSpeed;
    private final Map<String, Threat> threats = new LinkedHashMap<>();
    private final Map<RefugeKey, Long> excludedCovers = new LinkedHashMap<>();
    private State state = State.CALM;
    @Nullable private Vec3 destination;
    @Nullable private RefugeKey reservedCover;
    @Nullable private Pose previousPose;
    private boolean ownsSwimmingPose;
    private boolean legacyPlayDead;
    private long nextObservationTick = Long.MIN_VALUE;
    private int stateTicks;
    private int travelTicks;
    private int noProgressTicks;
    private int failedCandidates;
    private int lastPathNodeIndex = -1;
    private int alertDuration;
    private long scentSuspicionUntil;
    @Nullable private Vec3 scentBearing;
    private long scentBearingUntil;
    private int strongScentObservations;
    private long lastHurtTimestampAtStart;
    private long retryAfterTick;
    @Nullable private ResourceLocation observedDimension;
    private long episodeStartedAt;
    private long stateStartedAt;
    private long lastProgressCheck;
    private Vec3 lastProgressPosition;
    private long lastThreatRevision;
    private long plannedThreatRevision;

    public FearGoal(PathfinderMob mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    public boolean usesMoveSpeed(double speed) {
        return Double.compare(this.moveSpeed, speed) == 0;
    }

    public static void clearTransientCaches() {
        SIGHT_CACHE.invalidateAll();
        REFUGE_RESERVATIONS.clear();
    }

    @Override
    public boolean canUse() {
        if (!this.mob.isAlive() || this.mob.level().isClientSide || this.state != State.CALM) {
            return false;
        }
        long now = this.mob.level().getGameTime();
        if (now < this.retryAfterTick) return false;
        observeThreats();
        if (!hasActiveThreat() && now >= this.scentSuspicionUntil) return false;
        this.legacyPlayDead = FearofSoundConfig.PLAY_DEAD_ENTITIES_CACHE.contains(
                String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(this.mob.getType())));
        if (!this.legacyPlayDead && !FearofSoundConfig.ENABLE_HIDE_AND_FREEZE_CACHE) {
            return false;
        }
        if (!this.legacyPlayDead && !supportsHideNavigation()) {
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.mob.isAlive() || this.mob.level().isClientSide || this.state == State.CALM) {
            return false;
        }
        if (!this.legacyPlayDead && !supportsHideNavigation()) {
            return false;
        }
        long now = this.mob.level().getGameTime();
        return now - this.episodeStartedAt < MAX_EPISODE_TICKS;
    }

    private boolean supportsHideNavigation() {
        return !(this.mob.getNavigation() instanceof FlyingPathNavigation)
                && !(this.mob.getNavigation() instanceof WaterBoundPathNavigation)
                && !this.mob.isPassenger();
    }

    @Override
    public void start() {
        long now = this.mob.level().getGameTime();
        this.episodeStartedAt = now;
        this.stateStartedAt = now;
        this.stateTicks = 0;
        this.travelTicks = 0;
        this.noProgressTicks = 0;
        this.failedCandidates = 0;
        this.lastHurtTimestampAtStart = this.mob.getLastHurtByMobTimestamp();
        this.destination = null;
        releaseCoverReservation();
        this.lastProgressPosition = this.mob.position();
        this.lastProgressCheck = now;
        this.lastPathNodeIndex = -1;
        this.plannedThreatRevision = -1L;
        this.mob.getNavigation().stop();
        if (this.legacyPlayDead && hasConfirmedThreat()) {
            enter(State.LEGACY_FAINT, now);
            if (this.mob.getPose() == Pose.STANDING) {
                this.previousPose = this.mob.getPose();
                this.mob.setPose(Pose.SWIMMING);
                this.ownsSwimmingPose = true;
                this.mob.playSound(net.minecraft.sounds.SoundEvents.GENERIC_HURT, 1.0F, 1.0F);
            }
            return;
        }
        int minimum = FearofSoundConfig.PANIC_MIN_TICKS_CACHE;
        int maximum = Math.max(minimum, FearofSoundConfig.PANIC_MAX_TICKS_CACHE);
        int delay = hasConfirmedThreat()
                ? (maximum > minimum ? minimum + this.mob.getRandom().nextInt(maximum - minimum + 1) : minimum)
                : Math.max(40, Math.max(1, FearofSoundConfig.COMMON.scanCooldownTicks.get()) * 2 + 5);
        this.alertDuration = delay;
        enter(delay > 0 ? State.ALERT : State.SEEK_COVER, now);
        if (delay == 0) {
            planCover(now);
        }
        clearPlayerTarget();
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        releaseCoverReservation();
        if (this.ownsSwimmingPose && this.mob.getPose() == Pose.SWIMMING && this.previousPose != null) {
            this.mob.setPose(this.previousPose);
        }
        this.ownsSwimmingPose = false;
        this.previousPose = null;
        this.destination = null;
        this.threats.clear();
        this.excludedCovers.clear();
        this.state = State.CALM;
        this.failedCandidates = 0;
        this.retryAfterTick = this.mob.level().getGameTime() + Math.max(100, FearofSoundConfig.RECOVERY_QUIET_TICKS_CACHE);
        this.nextObservationTick = Long.MIN_VALUE;
        this.observedDimension = null;
        this.scentSuspicionUntil = 0L;
        clearScentMemory();
    }

    @Override
    public void tick() {
        if (!this.mob.isAlive() || !(this.mob.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        ResourceLocation dimension = level.dimension().location();
        if (this.observedDimension != null && !this.observedDimension.equals(dimension)) {
            releaseCoverReservation();
            this.mob.getNavigation().stop();
            if (this.ownsSwimmingPose && this.mob.getPose() == Pose.SWIMMING && this.previousPose != null) {
                this.mob.setPose(this.previousPose);
            }
            this.ownsSwimmingPose = false;
            this.destination = null;
            this.threats.clear();
            clearScentMemory();
            this.lastThreatRevision++;
            this.state = State.RECOVER;
            this.stateStartedAt = now;
            this.episodeStartedAt = now;
            this.nextObservationTick = now + Math.max(1, FearofSoundConfig.COMMON.scanCooldownTicks.get());
            this.observedDimension = dimension;
            return;
        }
        this.observedDimension = dimension;
        boolean damaged = this.mob.getLastHurtByMobTimestamp() != this.lastHurtTimestampAtStart;
        if (damaged) this.nextObservationTick = Long.MIN_VALUE;
        long oldRevision = this.lastThreatRevision;
        observeThreats();
        if (damaged) {
            this.lastHurtTimestampAtStart = this.mob.getLastHurtByMobTimestamp();
            if (this.state == State.LEGACY_FAINT) {
                finishEpisode(level, now);
                return;
            }
            if (this.state == State.FREEZE || this.state == State.RECOVER || this.state == State.ALERT) {
                enter(State.SEEK_COVER, now);
            }
            if (this.state == State.SEEK_COVER) {
                invalidateDestination();
                if (!planCover(now)) beginEscape(now);
            } else if (this.state == State.ESCAPE && !setEscapePath()) {
                this.failedCandidates++;
            }
        }
        if (this.state == State.FREEZE && this.lastThreatRevision != oldRevision) {
            leaveFreeze(level, now);
        }
        if (this.state == State.LEGACY_FAINT && this.lastThreatRevision != oldRevision) {
            finishEpisode(level, now);
            return;
        }
        if (now - this.episodeStartedAt >= MAX_EPISODE_TICKS) {
            finishEpisode(level, now);
            return;
        }
        switch (this.state) {
            case ALERT -> tickAlert(level, now);
            case SEEK_COVER -> tickSeeking(level, now);
            case FREEZE -> tickFreeze(level, now);
            case LEGACY_FAINT -> tickLegacyFaint(level, now);
            case ESCAPE -> tickEscape(level, now);
            case RECOVER -> tickRecover(level, now);
            case CALM -> {
            }
        }
    }

    private void tickAlert(ServerLevel level, long now) {
        this.stateTicks++;
        lookTowardThreat();
        if (!hasConfirmedThreat()) {
            if (this.strongScentObservations >= SCENT_ESCALATION_OBSERVATIONS) {
                enter(State.SEEK_COVER, now);
                if (!planCover(now)) beginEscape(now);
                return;
            }
            if (now < this.scentSuspicionUntil && this.stateTicks < this.alertDuration) return;
            enter(State.RECOVER, now);
        } else if (this.stateTicks >= this.alertDuration) {
            enter(State.SEEK_COVER, now);
            planCover(now);
        }
    }

    private void tickSeeking(ServerLevel level, long now) {
        if (!hasActiveThreat()) {
            this.mob.getNavigation().stop();
            this.destination = null;
            releaseCoverReservation();
            enter(State.RECOVER, now);
            return;
        }
        this.travelTicks++;
        if (this.travelTicks >= MAX_TRAVEL_TICKS) {
            beginEscape(now);
            return;
        }
        if (this.destination == null || this.plannedThreatRevision != this.lastThreatRevision) {
            if (!planCover(now)) {
                beginEscape(now);
                return;
            }
        }
        if (this.destination == null) {
            beginEscape(now);
            return;
        }
        updateProgressWindow(now);
        if (this.noProgressTicks >= 40 || this.mob.getNavigation().isStuck()) {
            invalidateDestination();
            if (this.failedCandidates >= 2) {
                beginEscape(now);
            } else {
                planCover(now);
            }
            return;
        }
        if (arrivedAtDestination()) {
            if (isCurrentCoverSafe(level, this.mob.position())) {
                this.mob.getNavigation().stop();
                enter(State.FREEZE, now);
                return;
            }
            invalidateDestination();
            planCover(now);
            return;
        }
        if (this.mob.getNavigation().isDone()) {
            invalidateDestination();
            if (this.failedCandidates >= 2 || !planCover(now)) {
                beginEscape(now);
            }
        }
        lookTowardThreat();
    }

    private void tickFreeze(ServerLevel level, long now) {
        this.mob.getNavigation().stop();
        if (this.destination == null || !isCurrentCoverSafe(level, this.mob.position())) {
            leaveFreeze(level, now);
            return;
        }
        if (now - this.stateStartedAt >= MAX_FREEZE_TICKS) {
            beginEscape(now);
            return;
        }
        if (!hasActiveThreat()) {
            enter(State.RECOVER, now);
        }
    }

    private void tickLegacyFaint(ServerLevel level, long now) {
        this.mob.getNavigation().stop();
        if (this.ownsSwimmingPose && this.mob.getPose() != Pose.SWIMMING) {
            this.ownsSwimmingPose = false;
        }
        if (this.mob.getLastHurtByMobTimestamp() != this.lastHurtTimestampAtStart || !hasActiveThreat()
                || now - this.stateStartedAt >= MAX_FREEZE_TICKS) {
            finishEpisode(level, now);
        }
    }

    private void tickEscape(ServerLevel level, long now) {
        if (!hasActiveThreat()) {
            this.mob.getNavigation().stop();
            enter(State.RECOVER, now);
            return;
        }
        this.travelTicks++;
        if (this.travelTicks >= MAX_TRAVEL_TICKS || this.failedCandidates >= 3) {
            finishEpisode(level, now);
            return;
        }
        updateProgressWindow(now);
        if (this.noProgressTicks >= 40 || this.mob.getNavigation().isDone() || this.mob.getNavigation().isStuck()) {
            if (!setEscapePath()) {
                this.failedCandidates++;
            }
            this.noProgressTicks = 0;
        }
        lookTowardThreat();
    }

    private void tickRecover(ServerLevel level, long now) {
        this.mob.getNavigation().stop();
        if (hasConfirmedThreat()) {
            enter(State.SEEK_COVER, now);
            planCover(now);
            return;
        }
        if (now - this.stateStartedAt >= FearofSoundConfig.RECOVERY_QUIET_TICKS_CACHE) {
            finishEpisode(level, now);
        }
    }

    private void leaveFreeze(ServerLevel level, long now) {
        if (!hasActiveThreat()) {
            releaseCoverReservation();
            enter(State.RECOVER, now);
            return;
        }
        enter(State.SEEK_COVER, now);
        this.travelTicks = 0;
        this.noProgressTicks = 0;
        planCover(now);
        if (this.destination == null) {
            beginEscape(now);
        }
    }

    private void observeThreats() {
        if (!(this.mob.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (now < this.nextObservationTick) {
            return;
        }
        this.nextObservationTick = now + Math.max(1, FearofSoundConfig.COMMON.scanCooldownTicks.get());
        Iterator<Map.Entry<String, Threat>> iterator = this.threats.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Threat> entry = iterator.next();
            if (entry.getValue().expiresAt() < now) {
                iterator.remove();
                this.lastThreatRevision++;
            }
        }

        Set<String> visibleEntities = new HashSet<>();
        Entity hurtBy = this.mob.getLastHurtByMob();
        if (hurtBy instanceof LivingEntity living && living.isAlive() && living.level() == level) {
            long hurtAt = this.mob.getLastHurtByMobTimestamp();
            if (this.mob.tickCount - hurtAt <= 200) {
                boolean visible = SoundAttractLOSIntegration.canDetect(this.mob, living);
                String key = "entity:" + living.getUUID();
                remember(key, living.position(), 1_000_000.0, now + THREAT_MEMORY_TICKS, visible);
                if (visible) visibleEntities.add(key);
            }
        }
        LivingEntity target = this.mob.getTarget();
        if (target != null && target.isAlive() && target.level() == level && target instanceof Player) {
            boolean visible = SoundAttractLOSIntegration.canDetect(this.mob, target);
            String key = "entity:" + target.getUUID();
                remember(key, target.position(), 10_000.0, now + THREAT_MEMORY_TICKS, visible);
            if (visible) visibleEntities.add(key);
        }

        int sightRange = Mth.clamp(FearofSoundConfig.LOS_SCAN_RANGE_CAP_CACHE, 8, 32);
        AABB search = this.mob.getBoundingBox().inflate(sightRange);
        List<Player> players = level.getEntitiesOfClass(Player.class, search,
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator());
        int checked = 0;
        for (Player player : players) {
            if (checked++ >= MAX_SIGHT_TARGETS) {
                break;
            }
            if (this.mob.distanceToSqr(player) <= (double) sightRange * sightRange
                    && SoundAttractLOSIntegration.canDetect(this.mob, player)) {
                String key = "entity:" + player.getUUID();
                remember(key, player.position(), 10_000.0, now + THREAT_MEMORY_TICKS, true);
                visibleEntities.add(key);
            }
        }

        List<net.minecraft.world.entity.Mob> attackers = level.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class, search,
                other -> other.isAlive() && other.getTarget() == this.mob
        );
        int attackersChecked = 0;
        for (net.minecraft.world.entity.Mob attacker : attackers) {
            if (attackersChecked++ >= 8) break;
            String key = "entity:" + attacker.getUUID();
            boolean visible = SoundAttractLOSIntegration.canDetect(this.mob, attacker);
            remember(key, attacker.position(), 100_000.0, now + THREAT_MEMORY_TICKS, visible);
            if (visible) visibleEntities.add(key);
        }

        for (Map.Entry<String, Threat> entry : this.threats.entrySet()) {
            Threat threat = entry.getValue();
            if (entry.getKey().startsWith("entity:") && threat.visible() && !visibleEntities.contains(entry.getKey())) {
                entry.setValue(new Threat(threat.key(), threat.position(), threat.weight(), threat.expiresAt(), false));
                this.lastThreatRevision++;
            }
        }

        SoundTracker.SoundRecord sound = SoundSystemFacade.findNearestSoundFor(this.mob);
        if (sound != null && sound.pos != null && sound.dimensionKey != null
                && sound.dimensionKey.equals(level.dimension().location().toString())
                && Double.isFinite(sound.range) && sound.range > 0.0
                && Double.isFinite(sound.weight) && sound.weight > 0.0
                && this.mob.position().distanceToSqr(Vec3.atCenterOf(sound.pos)) <= sound.range * sound.range) {
            int lifetime = Mth.clamp(sound.ticksRemaining, 0, (int) MAX_SOUND_MEMORY_TICKS);
            ResourceLocation registeredSoundId = sound.sound == null ? null : ForgeRegistries.SOUND_EVENTS.getKey(sound.sound);
            String soundIdentity = sound.soundId != null && !sound.soundId.isBlank()
                    ? sound.soundId : String.valueOf(registeredSoundId);
            String key = "sound:" + soundIdentity + ":" + level.dimension().location() + ":" + sound.pos.asLong();
            if (lifetime > 0) {
                remember(key, Vec3.atCenterOf(sound.pos), Math.min(1000.0, sound.weight), now + lifetime, false);
            }
        }

        FearSignals.Trigger trigger = FearSignals.consume(this.mob, now);
        if (trigger != null && trigger.pos != null && trigger.range > 0) {
            remember("signal:" + trigger.pos.asLong(), Vec3.atCenterOf(trigger.pos), 80.0,
                    Math.min(trigger.expiresAtTick, now + THREAT_MEMORY_TICKS), true);
        }

        boolean unknownScentObserved = false;
        boolean strongNearbyScentObserved = false;
        double scentWeightTotal = 0.0;
        double scentX = 0.0;
        double scentY = 0.0;
        double scentZ = 0.0;
        if (FearofSoundConfig.SCENT_AWARENESS_ENABLED_CACHE) {
            for (ScentAttractIntegration.Evidence scent : ScentAttractIntegration.sample(this.mob, now,
                    FearofSoundConfig.SCENT_MAX_AGE_TICKS_CACHE, FearofSoundConfig.SCENT_AWARENESS_RADIUS_CACHE)) {
                if (scent == null || scent.position() == null || !Float.isFinite(scent.strength()) || scent.strength() <= 0.0F) continue;
                long age = now - scent.timestamp();
                if (age < 0 || age > FearofSoundConfig.SCENT_MAX_AGE_TICKS_CACHE) continue;
                double decay = Math.max(0.1, 1.0 - (double) age / FearofSoundConfig.SCENT_MAX_AGE_TICKS_CACHE);
                String ownerKey = scent.owner() == null ? null : "entity:" + scent.owner();
                Threat knownOwner = ownerKey == null ? null : this.threats.get(ownerKey);
                if (knownOwner != null && knownOwner.expiresAt() >= now) {
                    String key = "scent-known:" + scent.owner() + ":" + scent.position().asLong() + ":" + scent.sourceType();
                    double weight = Mth.clamp(scent.strength() * decay * 10.0, 1.0, 100.0);
                    remember(key, Vec3.atCenterOf(scent.position()), weight, now + 40, false);
                } else {
                    unknownScentObserved = true;
                    this.scentSuspicionUntil = Math.max(this.scentSuspicionUntil, now + 40);
                    Vec3 scentPosition = Vec3.atCenterOf(scent.position());
                    double weight = Math.max(0.05, scent.strength() * decay);
                    scentX += scentPosition.x * weight;
                    scentY += scentPosition.y * weight;
                    scentZ += scentPosition.z * weight;
                    scentWeightTotal += weight;
                    if (scent.strength() >= 0.75F && age <= 40
                            && this.mob.position().distanceToSqr(scentPosition) <= 64.0) {
                        strongNearbyScentObserved = true;
                    }
                }
            }
        }
        if (unknownScentObserved && scentWeightTotal > 0.0) {
            Vec3 bearing = new Vec3(scentX / scentWeightTotal, scentY / scentWeightTotal, scentZ / scentWeightTotal);
            this.scentBearing = bearing;
            this.scentBearingUntil = now + SCENT_MEMORY_TICKS;
            remember("scent-ambiguous:local", bearing, 8.0, this.scentBearingUntil, false);
            this.strongScentObservations = strongNearbyScentObserved
                    ? Math.min(SCENT_ESCALATION_OBSERVATIONS, this.strongScentObservations + 1)
                    : 0;
        } else {
            this.strongScentObservations = 0;
            this.scentBearing = null;
            this.scentBearingUntil = 0L;
            if (this.threats.remove("scent-ambiguous:local") != null) {
                this.lastThreatRevision++;
            }
        }
    }

    private void remember(String key, Vec3 position, double weight, long expiresAt, boolean visible) {
        if (key == null || position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y)
                || !Double.isFinite(position.z) || !Double.isFinite(weight) || weight <= 0.0) {
            return;
        }
        Threat old = this.threats.get(key);
        Threat next = new Threat(key, position, weight, expiresAt, visible);
        this.threats.put(key, next);
        if (old == null || old.position().distanceToSqr(position) >= 1.0
                || old.visible() != visible || Math.abs(old.weight() - weight) >= 1.0) {
            this.lastThreatRevision++;
        }
        while (this.threats.size() > MAX_THREATS) {
            String weakest = null;
            double weakestWeight = Double.MAX_VALUE;
            for (Map.Entry<String, Threat> entry : this.threats.entrySet()) {
                if (entry.getValue().weight() < weakestWeight) {
                    weakestWeight = entry.getValue().weight();
                    weakest = entry.getKey();
                }
            }
            if (weakest == null || weakest.equals(key) && this.threats.size() == 1) {
                break;
            }
            this.threats.remove(weakest);
            this.lastThreatRevision++;
        }
    }

    private boolean hasActiveThreat() {
        long now = this.mob.level().getGameTime();
        return this.threats.values().stream().anyMatch(threat -> threat.expiresAt() >= now);
    }

    private boolean hasConfirmedThreat() {
        long now = this.mob.level().getGameTime();
        return this.threats.values().stream()
                .anyMatch(threat -> threat.expiresAt() >= now && !threat.key().startsWith("scent-ambiguous:"));
    }

    private boolean planCover(long now) {
        releaseCoverReservation();
        if (!(this.mob.level() instanceof ServerLevel level) || this.mob.getNavigation() instanceof FlyingPathNavigation
                || this.mob.getNavigation() instanceof WaterBoundPathNavigation || this.mob.isPassenger()) {
            this.destination = null;
            return false;
        }
        PathNavigation navigation = this.mob.getNavigation();
        List<Threat> activeThreats = this.threats.values().stream()
                .filter(threat -> threat.expiresAt() >= now)
                .limit(MAX_THREATS)
                .toList();
        if (activeThreats.isEmpty()) {
            this.destination = null;
            return false;
        }
        List<Vec3> candidates = candidatePositions(level);
        List<ScoredCover> scoredCovers = new ArrayList<>(MAX_COVER_CANDIDATES);
        this.excludedCovers.entrySet().removeIf(entry -> entry.getValue() <= now);
        int tested = 0;
        for (Vec3 candidate : candidates) {
            if (tested++ >= MAX_COVER_CANDIDATES) {
                break;
            }
            Path path = navigation.createPath(BlockPos.containing(candidate), 0);
            if (path == null || !path.canReach() || path.getTarget() == null) {
                excludeCover(level, candidate, now);
                continue;
            }
            Vec3 endpoint = path.getTarget().getCenter();
            if (endpoint.distanceToSqr(candidate) > 9.0 || !hasSafeClearance(level, endpoint)) {
                excludeCover(level, candidate, now);
                continue;
            }
            double exposure = 0.0;
            for (Threat threat : activeThreats) {
                double distanceSq = endpoint.distanceToSqr(threat.position());
                if (threat.visible() && hasClearSight(level, endpoint, threat.position())) {
                    exposure += Math.max(1.0, Math.min(1000.0, threat.weight()));
                } else if (threat.key().startsWith("scent-known:")) {
                    exposure += threat.weight() * 12.0 / (distanceSq + 4.0);
                } else {
                    double desiredClearance = Math.max(1.0, FearofSoundConfig.ARRIVAL_DISTANCE_CACHE);
                    exposure += Math.min(1000.0, Math.max(1.0, threat.weight()))
                            * desiredClearance * desiredClearance / (distanceSq + 16.0);
                }
            }
            double routeCost = this.mob.position().distanceToSqr(endpoint);
            double score = exposure * 100.0 + routeCost;
            scoredCovers.add(new ScoredCover(endpoint, path, score));
        }
        scoredCovers.sort(java.util.Comparator.comparingDouble(ScoredCover::score));
        for (ScoredCover cover : scoredCovers) {
            if (!hasSafeClearance(level, cover.position()) || !reserveCover(level, cover.position(), now)) continue;
            if (!navigation.moveTo(cover.path(), this.moveSpeed * 1.15)) {
                excludeCover(level, cover.position(), now);
                releaseCoverReservation();
                continue;
            }
            this.destination = cover.position();
            this.plannedThreatRevision = this.lastThreatRevision;
            this.travelTicks = 0;
            this.noProgressTicks = 0;
            this.lastProgressCheck = now;
            this.lastProgressPosition = this.mob.position();
            this.lastPathNodeIndex = -1;
            return true;
        }
        this.destination = null;
        return false;
    }

    private List<Vec3> candidatePositions(ServerLevel level) {
        List<Vec3> candidates = new ArrayList<>(MAX_COVER_CANDIDATES);
        BlockPos origin = this.mob.blockPosition();
        double baseAngle = this.mob.getRandom().nextDouble() * Math.PI * 2.0;
        int[] distances = {4, 7, 10};
        for (int distance : distances) {
            for (int angleIndex = 0; angleIndex < 4; angleIndex++) {
                double angle = baseAngle + angleIndex * Math.PI / 2.0;
                int x = origin.getX() + Mth.floor(Math.cos(angle) * distance);
                int z = origin.getZ() + Mth.floor(Math.sin(angle) * distance);
                BlockPos column = new BlockPos(x, origin.getY(), z);
                if (!level.hasChunkAt(column)) continue;
                for (int yOffset = 2; yOffset >= -3; yOffset--) {
                    BlockPos feet = new BlockPos(x, origin.getY() + yOffset, z);
                    if (feet.getY() <= level.getMinBuildHeight() || feet.getY() >= level.getMaxBuildHeight() - 1
                            || !level.hasChunkAt(feet) || !level.isEmptyBlock(feet) || !level.isEmptyBlock(feet.above())
                            || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                            || !level.getFluidState(feet).isEmpty() || isHazardous(level, feet)) continue;
                    candidates.add(Vec3.atBottomCenterOf(feet));
                    break;
                }
            }
        }
        return candidates;
    }

    private boolean hasSafeClearance(ServerLevel level, Vec3 position) {
        if (position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
            return false;
        }
        BlockPos feet = BlockPos.containing(position);
        if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.below())) {
            return false;
        }
        BlockState floor = level.getBlockState(feet.below());
        if (!floor.isFaceSturdy(level, feet.below(), Direction.UP) || !level.getFluidState(feet).isEmpty()
                || isHazardous(level, feet)) {
            return false;
        }
        AABB movedBox = this.mob.getBoundingBox().move(position.subtract(this.mob.position()));
        return level.noCollision(this.mob, movedBox);
    }

    private boolean isHazardous(ServerLevel level, BlockPos feet) {
        BlockState feetState = level.getBlockState(feet);
        BlockState floor = level.getBlockState(feet.below());
        return feetState.is(BlockTags.FIRE) || feetState.is(Blocks.CACTUS) || feetState.is(Blocks.MAGMA_BLOCK)
                || feetState.is(Blocks.CAMPFIRE) || feetState.is(Blocks.SOUL_CAMPFIRE)
                || floor.is(Blocks.CACTUS) || floor.is(Blocks.MAGMA_BLOCK)
                || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE);
    }

    private boolean isCurrentCoverSafe(ServerLevel level, Vec3 position) {
        if (!hasSafeClearance(level, position)) {
            return false;
        }
        for (Threat threat : this.threats.values()) {
            if (threat.expiresAt() < level.getGameTime()) continue;
            if (threat.visible() && hasClearSight(level, position, threat.position())) return false;
            if (threat.key().startsWith("scent-known:") && position.distanceToSqr(threat.position()) <= 12.25) {
                return false;
            }
        }
        return true;
    }

    private boolean hasClearSight(ServerLevel level, Vec3 mobPosition, Vec3 threatPosition) {
        Vec3 start = mobPosition.add(0.0, Math.max(0.1, this.mob.getEyeHeight() * 0.8), 0.0);
        Vec3 end = threatPosition.add(0.0, 0.9, 0.0);
        ResourceLocation dimension = level.dimension().location();
        SightKey key = new SightKey(dimension, quantize(start.x), quantize(start.y), quantize(start.z),
                quantize(end.x), quantize(end.y), quantize(end.z));
        long now = level.getGameTime();
        SightValue cached = SIGHT_CACHE.getIfPresent(key);
        if (cached != null && now - cached.tick() >= 0 && now - cached.tick() <= 2) {
            return cached.clear();
        }
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.mob));
        boolean clear = hit.getType() == BlockHitResult.Type.MISS;
        SIGHT_CACHE.put(key, new SightValue(clear, now));
        return clear;
    }

    private static long quantize(double coordinate) {
        return Math.round(coordinate * 4.0);
    }

    private boolean arrivedAtDestination() {
        return this.destination != null && this.mob.position().distanceToSqr(this.destination) <= 1.0;
    }

    private void beginEscape(long now) {
        releaseCoverReservation();
        this.destination = null;
        this.travelTicks = 0;
        this.noProgressTicks = 0;
        this.lastProgressCheck = now;
        this.lastProgressPosition = this.mob.position();
        this.lastPathNodeIndex = -1;
        enter(State.ESCAPE, now);
        if (!setEscapePath()) {
            this.failedCandidates++;
        }
    }

    private boolean reserveCover(ServerLevel level, Vec3 position, long now) {
        RefugeKey key = new RefugeKey(level.dimension().location(), BlockPos.containing(position).asLong());
        Long excludedUntil = this.excludedCovers.get(key);
        if (excludedUntil != null && excludedUntil > now) return false;
        cleanupExpiredReservations(now, 32);
        AtomicBoolean accepted = new AtomicBoolean(false);
        REFUGE_RESERVATIONS.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAtTick() <= now || existing.owner().equals(this.mob.getUUID())) {
                accepted.set(true);
                return new RefugeReservation(this.mob.getUUID(), now + 600);
            }
            return existing;
        });
        if (accepted.get()) this.reservedCover = key;
        return accepted.get();
    }

    private void releaseCoverReservation() {
        RefugeKey key = this.reservedCover;
        this.reservedCover = null;
        if (key == null) return;
        RefugeReservation current = REFUGE_RESERVATIONS.get(key);
        if (current != null && current.owner().equals(this.mob.getUUID())) {
            REFUGE_RESERVATIONS.remove(key, current);
        }
    }

    private static void cleanupExpiredReservations(long now, int limit) {
        int checked = 0;
        for (Map.Entry<RefugeKey, RefugeReservation> entry : REFUGE_RESERVATIONS.entrySet()) {
            if (checked++ >= limit) break;
            if (entry.getValue().expiresAtTick() <= now) {
                REFUGE_RESERVATIONS.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private boolean setEscapePath() {
        List<Threat> activeThreats = this.threats.values().stream()
                .filter(candidate -> candidate.expiresAt() >= this.mob.level().getGameTime())
                .limit(MAX_THREATS)
                .toList();
        if (activeThreats.isEmpty()) return false;

        Vec3 bestPosition = null;
        double bestRisk = Double.POSITIVE_INFINITY;
        for (Threat threat : activeThreats) {
            Vec3 candidate = LandRandomPos.getPosAway(this.mob, 12, 6, threat.position());
            if (candidate == null || !Double.isFinite(candidate.x) || !Double.isFinite(candidate.y)
                    || !Double.isFinite(candidate.z) || candidate.distanceToSqr(this.mob.position()) < 4.0) continue;
            double risk = 0.0;
            for (Threat other : activeThreats) {
                double distanceSq = candidate.distanceToSqr(other.position());
                risk += Math.max(1.0, other.weight()) / (distanceSq + 4.0);
            }
            risk -= Math.min(25.0, candidate.distanceToSqr(this.mob.position())) * 0.001;
            if (risk < bestRisk) {
                bestRisk = risk;
                bestPosition = candidate;
            }
        }
        if (bestPosition == null) return false;
        Path path = this.mob.getNavigation().createPath(BlockPos.containing(bestPosition), 0);
        if (path == null || !path.canReach() || !this.mob.getNavigation().moveTo(path, this.moveSpeed * 1.15)) return false;
        this.lastPathNodeIndex = -1;
        this.lastProgressPosition = this.mob.position();
        this.lastProgressCheck = this.mob.level().getGameTime();
        return true;
    }

    private void updateProgressWindow(long now) {
        if (now - this.lastProgressCheck < 10) return;
        double movedSq = this.lastProgressPosition == null
                ? 0.0 : this.mob.position().distanceToSqr(this.lastProgressPosition);
        Path activePath = this.mob.getNavigation().getPath();
        int pathNodeIndex = activePath == null ? -1 : activePath.getNextNodeIndex();
        boolean pathProgress = pathNodeIndex > this.lastPathNodeIndex;
        this.noProgressTicks = movedSq >= 0.09 || pathProgress ? 0 : this.noProgressTicks + 10;
        this.lastPathNodeIndex = pathNodeIndex;
        this.lastProgressPosition = this.mob.position();
        this.lastProgressCheck = now;
    }

    private void invalidateDestination() {
        if (this.destination != null && this.mob.level() instanceof ServerLevel level) {
            excludeCover(level, this.destination, level.getGameTime());
        }
        releaseCoverReservation();
        this.destination = null;
        this.mob.getNavigation().stop();
        this.failedCandidates++;
    }

    private void excludeCover(ServerLevel level, Vec3 position, long now) {
        RefugeKey key = new RefugeKey(level.dimension().location(), BlockPos.containing(position).asLong());
        this.excludedCovers.put(key, now + 100);
        while (this.excludedCovers.size() > 24) {
            this.excludedCovers.remove(this.excludedCovers.keySet().iterator().next());
        }
    }

    private void lookTowardThreat() {
        Threat threat = this.threats.values().stream()
                .filter(candidate -> candidate.expiresAt() >= this.mob.level().getGameTime())
                .max(java.util.Comparator.comparingDouble(Threat::weight))
                .orElse(null);
        if (threat != null) {
            this.mob.getLookControl().setLookAt(threat.position().x, threat.position().y, threat.position().z);
        } else if (this.scentBearing != null && this.scentBearingUntil >= this.mob.level().getGameTime()) {
            this.mob.getLookControl().setLookAt(this.scentBearing.x, this.scentBearing.y, this.scentBearing.z);
        }
    }

    private void clearScentMemory() {
        this.scentBearing = null;
        this.scentBearingUntil = 0L;
        this.strongScentObservations = 0;
    }

    private void clearPlayerTarget() {
        LivingEntity target = this.mob.getTarget();
        if (target instanceof Player && target.isAlive()) {
            this.mob.setTarget(null);
        }
    }

    private void finishEpisode(ServerLevel level, long now) {
        this.mob.getNavigation().stop();
        releaseCoverReservation();
        if (this.ownsSwimmingPose && this.mob.getPose() == Pose.SWIMMING && this.previousPose != null) {
            this.mob.setPose(this.previousPose);
        }
        this.ownsSwimmingPose = false;
        this.destination = null;
        this.threats.clear();
        this.scentSuspicionUntil = 0L;
        clearScentMemory();
        enter(State.CALM, now);
    }

    private void enter(State next, long now) {
        if (this.state == next) {
            return;
        }
        this.state = next;
        this.stateStartedAt = now;
        this.stateTicks = 0;
        if (next == State.FREEZE || next == State.LEGACY_FAINT || next == State.RECOVER) {
            this.mob.getNavigation().stop();
        }
    }
}
