package com.example.fear_of_sound.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FearSignals {
    public static class Trigger {
        public final BlockPos pos;
        public final int range;
        public final long expiresAtTick;
        public Trigger(BlockPos pos, int range, long expiresAtTick) {
            this.pos = pos;
            this.range = range;
            this.expiresAtTick = expiresAtTick;
        }
    }

    private static final ConcurrentHashMap<UUID, Trigger> TRIGGERS = new ConcurrentHashMap<>();
    private static final long DEFAULT_TTL_TICKS = 10L;

    public static void trigger(PathfinderMob mob, BlockPos pos, int range, long nowTick) {
        TRIGGERS.put(mob.getUUID(), new Trigger(pos, range, nowTick + DEFAULT_TTL_TICKS));
    }

    public static Trigger poll(PathfinderMob mob, long nowTick) {
        Trigger t = TRIGGERS.remove(mob.getUUID());
        if (t == null) return null;
        if (t.expiresAtTick < nowTick) return null;
        return t;
    }
}
