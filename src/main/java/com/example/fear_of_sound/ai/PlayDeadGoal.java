package com.example.fear_of_sound.ai;

import com.example.fear_of_sound.SoundTracker;
import com.example.fear_of_sound.config.FearofSoundConfig;
import com.example.fear_of_sound.SoundSystemFacade;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class PlayDeadGoal extends Goal {
    protected final PathfinderMob mob;
    private SoundTracker.SoundRecord activeSound;
    private int soundCheckCooldown;

    public PlayDeadGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.getTarget() != null || this.mob.getLastHurtByMob() != null || !this.mob.onGround()) {
            return false;
        }

        if (this.soundCheckCooldown > 0) {
            this.soundCheckCooldown--;
            return false;
        }

        this.soundCheckCooldown = FearofSoundConfig.COMMON.scanCooldownTicks.get();
        this.activeSound = SoundSystemFacade.findNearestSoundFor(this.mob);
        return this.activeSound != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.getLastHurtByMob() != null) {
            return false;
        }
        return this.activeSound != null && this.activeSound.ticksRemaining > 0;
    }

    @Override
    public void start() {
        this.mob.getNavigation().stop();
        this.mob.setPose(Pose.SWIMMING);
        this.mob.playSound(SoundEvents.GENERIC_HURT, 1.0F, 1.0F);
    }

    @Override
    public void stop() {
        this.mob.setPose(Pose.STANDING);
        this.activeSound = null;
    }

    @Override
    public void tick() {

        this.mob.getNavigation().stop();

        this.mob.setYHeadRot(this.mob.getYRot());
        this.mob.setXRot(this.mob.getXRot()); 
    }
}