package com.example.fear_of_sound.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class FearSignals {
    public static final class Trigger {
        public final BlockPos pos;
        public final int range;
        public final long expiresAtTick;

        private Trigger(BlockPos pos, int range, long expiresAtTick) {
            this.pos = pos;
            this.range = range;
            this.expiresAtTick = expiresAtTick;
        }
    }

    private static final long DEFAULT_TTL_TICKS = 10L;
    private static final Cache<UUID, Trigger> TRIGGERS = Caffeine.newBuilder()
            .maximumSize(8192)
            .expireAfterWrite(2, TimeUnit.SECONDS)
            .build();

    private FearSignals() {
    }

    public static void trigger(PathfinderMob mob, BlockPos pos, int range, long nowTick) {
        if (mob == null || pos == null || range <= 0) return;
        TRIGGERS.put(mob.getUUID(), new Trigger(pos.immutable(), range, nowTick + DEFAULT_TTL_TICKS));
    }

    public static Trigger consume(PathfinderMob mob, long nowTick) {
        if (mob == null) return null;
        Trigger trigger = TRIGGERS.asMap().remove(mob.getUUID());
        return trigger == null || trigger.expiresAtTick <= nowTick ? null : trigger;
    }

    public static void clear() {
        TRIGGERS.invalidateAll();
    }

    public static void clear(PathfinderMob mob) {
        if (mob != null) TRIGGERS.invalidate(mob.getUUID());
    }
}
