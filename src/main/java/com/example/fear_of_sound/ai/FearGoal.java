package com.example.fear_of_sound.ai;

import com.example.fear_of_sound.SoundTracker;
import com.example.fear_of_sound.SoundSystemFacade;
import com.example.fear_of_sound.config.FearofSoundConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.AirRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class FearGoal extends Goal {

    protected final PathfinderMob mob;
    private final double moveSpeed;

    private static final int HORIZONTAL_FLEE_RANGE = 20; 
    private static final int VERTICAL_FLEE_RANGE = 8;
    private static final int MIN_PANIC_TICKS = 20;
    private static final int MAX_PANIC_TICKS = 40;
    private static final double[] FALLBACK_ANGLES = {0.0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2, 3 * Math.PI / 4, -3 * Math.PI / 4, Math.PI};

    private enum FleeState {
        NOT_FLEEING,
        PANICKING,
        FLEEING,
        PAUSING
    }

    private FleeState state = FleeState.NOT_FLEEING;
    @Nullable
    private BlockPos targetSoundPos;
    private double safeDistSq;
    private int stateTicks;
    private int fleeingTicks; 
    private int nearestCheckCooldown = 0;
    private int fleeSearchCooldown = 0;

    public FearGoal(PathfinderMob mob, double moveSpeed) {
        this.mob = mob;
        this.moveSpeed = moveSpeed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null || mob.getLastHurtByMob() != null) {
            return false;
        }

        FearSignals.Trigger instant = FearSignals.poll(this.mob, this.mob.level().getGameTime());
        if (instant != null) {
            this.targetSoundPos = instant.pos;
            double safeDistance = calculateSafeDistance(instant.range);
            this.safeDistSq = safeDistance * safeDistance;
            return true;
        }
        if (this.nearestCheckCooldown > 0) {
            this.nearestCheckCooldown--;
            return false;
        }
        this.nearestCheckCooldown = FearofSoundConfig.COMMON.scanCooldownTicks.get();
        SoundTracker.SoundRecord record = findNearestSound();
        if (record == null) {
            return false;
        }
        double safeDistance = calculateSafeDistance(record.range);
        if (mob.position().distanceToSqr(Vec3.atCenterOf(record.pos)) >= safeDistance * safeDistance) {
            return false;
        }
        this.targetSoundPos = record.pos;
        this.safeDistSq = safeDistance * safeDistance;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetSoundPos != null && mob.getTarget() == null && mob.getLastHurtByMob() == null;
    }

    @Override
    public void start() {
        this.mob.getNavigation().stop();
        int min = FearofSoundConfig.PANIC_MIN_TICKS_CACHE;
        int max = FearofSoundConfig.PANIC_MAX_TICKS_CACHE;
        int duration = (max > min) ? (min + this.mob.getRandom().nextInt(max - min)) : min;
        if (duration <= 0) {
            this.state = FleeState.FLEEING;
            findAndSetPath();
        } else {
            this.state = FleeState.PANICKING;
            this.stateTicks = duration;
        }
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.targetSoundPos = null;
        this.state = FleeState.NOT_FLEEING;
    }

    @Override
    public void tick() {
        if (this.targetSoundPos == null) return;
        switch (this.state) {
            case PANICKING -> handlePanicking();
            case FLEEING -> handleFleeing();
            case PAUSING -> handlePausing();
        }
    }

    private void handlePanicking() {
        this.stateTicks--;
        Vec3 directionAway = this.mob.position().subtract(Vec3.atCenterOf(this.targetSoundPos)).normalize();
        this.mob.getLookControl().setLookAt(this.mob.getX() + directionAway.x, this.mob.getEyeY(), this.mob.getZ() + directionAway.z);
        if (this.stateTicks <= 0) {
            this.state = FleeState.FLEEING;
            findAndSetPath();
        }
    }

    private void handleFleeing() {
        this.fleeingTicks--;
        PathNavigation nav = this.mob.getNavigation();
        
        if (nav.isDone() || nav.isStuck() || this.fleeingTicks <= 0) {
            if (mob.position().distanceToSqr(Vec3.atCenterOf(this.targetSoundPos)) >= this.safeDistSq) {
                this.targetSoundPos = null; 
                return;
            }

            if (this.fleeSearchCooldown > 0) {
                this.fleeSearchCooldown--;
                return;
            }
            findAndSetPath();
            this.fleeSearchCooldown = Math.max(5, FearofSoundConfig.COMMON.scanCooldownTicks.get() / 2);
        }
    }

    private void handlePausing() {
        this.stateTicks--;
        Vec3 directionAway = this.mob.position().subtract(Vec3.atCenterOf(this.targetSoundPos)).normalize();
        this.mob.getLookControl().setLookAt(this.mob.getX() + directionAway.x, this.mob.getEyeY(), this.mob.getZ() + directionAway.z);
        if (this.nearestCheckCooldown > 0) {
            this.nearestCheckCooldown--;
        } else {
            updateSoundSource();
            this.nearestCheckCooldown = FearofSoundConfig.COMMON.scanCooldownTicks.get();
        }
        if (this.stateTicks <= 0) {
            if (this.fleeSearchCooldown > 0) {
                this.fleeSearchCooldown--;
                this.stateTicks = Math.max(this.stateTicks, 2);
            } else {
                this.state = FleeState.FLEEING;
                findAndSetPath();
                this.fleeSearchCooldown = Math.max(5, FearofSoundConfig.COMMON.scanCooldownTicks.get() / 2);
            }
        }
    }

    private void findAndSetPath() {
        if (this.targetSoundPos == null) return;
        Vec3 fleePos = findFleePosition(this.targetSoundPos);
        if (fleePos != null) {
            if (this.mob.getNavigation().moveTo(fleePos.x, fleePos.y, fleePos.z, this.moveSpeed * 1.33)) {
                this.fleeingTicks = 60 + this.mob.getRandom().nextInt(40);
            }
        } else {

            this.fleeSearchCooldown = Math.max(5, FearofSoundConfig.COMMON.scanCooldownTicks.get() / 2);
        }
    }

    @Nullable
    private Vec3 findFleePosition(BlockPos soundBlockPos) {
        Vec3 soundCenter = Vec3.atCenterOf(soundBlockPos);
        PathNavigation nav = this.mob.getNavigation();
        Vec3 directionAway = this.mob.position().subtract(soundCenter).normalize();

        Vec3 primaryCandidate = null;
        if (nav instanceof FlyingPathNavigation) {
            primaryCandidate = AirRandomPos.getPosTowards(this.mob, HORIZONTAL_FLEE_RANGE, VERTICAL_FLEE_RANGE, 0, directionAway, Math.PI);
        } else if (nav instanceof WaterBoundPathNavigation) {
            primaryCandidate = AirAndWaterRandomPos.getPos(this.mob, HORIZONTAL_FLEE_RANGE, VERTICAL_FLEE_RANGE, -2, directionAway.x, directionAway.z, Math.PI / 2.0D);
        } else {
            primaryCandidate = LandRandomPos.getPosAway(this.mob, HORIZONTAL_FLEE_RANGE, VERTICAL_FLEE_RANGE, soundCenter);
        }
        if (primaryCandidate != null && isPositionFurther(primaryCandidate, soundCenter)) {
            return primaryCandidate;
        }

        int tries = 0;
        for (double angle : FALLBACK_ANGLES) {
            Vec3 rotatedDir = directionAway.yRot((float) angle);
            Vec3 potentialTargetPos = this.mob.position().add(rotatedDir.scale(HORIZONTAL_FLEE_RANGE / 2.0 + mob.getRandom().nextInt(HORIZONTAL_FLEE_RANGE / 2)));
            Path path = nav.createPath(BlockPos.containing(potentialTargetPos), 1);
            if (path != null && path.canReach()) {
                return path.getTarget().getCenter();
            }
            if (++tries >= 3) break;
        }
        return null;
    }

    private boolean isPositionFurther(Vec3 candidatePos, Vec3 soundCenter) {
        return candidatePos.distanceToSqr(soundCenter) > this.mob.position().distanceToSqr(soundCenter);
    }

    private double calculateSafeDistance(double currentSoundRange) {
        double arrivalDistanceBuffer = FearofSoundConfig.COMMON.arrivalDistance.get();
        double randomOffset = (this.mob.getRandom().nextDouble() * 0.5) * arrivalDistanceBuffer;
        return Mth.clamp(currentSoundRange + arrivalDistanceBuffer + randomOffset, 1.0, 128.0);
    }

    private void updateSoundSource() {
        SoundTracker.SoundRecord currentNearest = findNearestSound();
        if (currentNearest != null) {
            this.targetSoundPos = currentNearest.pos;
            this.safeDistSq = Math.pow(calculateSafeDistance(currentNearest.range), 2);
        } else {
            this.targetSoundPos = null;
        }
    }

    @Nullable
    private SoundTracker.SoundRecord findNearestSound() {
        Level currentLevel = this.mob.level();
        if (currentLevel.isClientSide) {
            return null;
        }
        return SoundSystemFacade.findNearestSoundFor(this.mob);
    }
}