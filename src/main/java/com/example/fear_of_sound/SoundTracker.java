package com.example.fear_of_sound;

import com.example.fear_of_sound.config.FearofSoundConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class SoundTracker {

    public static class SoundRecord {
        public final SoundEvent sound;
        public final BlockPos pos;
        public int ticksRemaining;
        public final String dimensionKey;
        public final double range;
        public final double weight;

        public SoundRecord(SoundEvent sound, BlockPos pos, int lifetime, String dimensionKey, double range, double weight) {
            this.sound = sound;
            this.pos = pos;
            this.ticksRemaining = lifetime;
            this.dimensionKey = dimensionKey;
            this.range = range;
            this.weight = weight;
        }
    }

    private static class CellKey {
        public final String dimension;
        public final int cx;
        public final int cy;
        public final int cz;
        public CellKey(String dimension, int cx, int cy, int cz) {
            this.dimension = dimension; this.cx = cx; this.cy = cy; this.cz = cz;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CellKey other)) return false;
            return cx == other.cx && cy == other.cy && cz == other.cz && dimension.equals(other.dimension);
        }
        @Override
        public int hashCode() {
            return dimension.hashCode() * 31 * 31 * 31 + (cx * 73856093) ^ (cy * 19349663) ^ (cz * 83492791);
        }
    }

    private static int CURRENT_TICK = 0;

    private static final List<SoundRecord> RECENT_SOUNDS = new ArrayList<>();
    private static final Map<CellKey, List<SoundRecord>> SPATIAL_INDEX = new HashMap<>();
    private static int MAX_RANGE_SEEN = 0;

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey) {
        FearofSoundConfig.SoundConfig config = FearofSoundConfig.SOUND_CONFIGS_CACHE.get(se);
        if (config == null) return;

        int lifetime = FearofSoundConfig.COMMON.soundLifetimeTicks.get();
        addSound(se, pos, dimensionKey, config.range, config.weight, lifetime);
    }

    public static synchronized void addSound(SoundEvent se, BlockPos pos, String dimensionKey, double range, double weight, int lifetime) {

        String soundId = (se != null ? String.valueOf(ForgeRegistries.SOUND_EVENTS.getKey(se)) : "fear_of_sound:voicechat");
        List<SoundRecord> candidates = getCandidatesAround(pos, dimensionKey, 1);
        for (SoundRecord r : candidates) {
            ResourceLocation rId = (r.sound != null ? ForgeRegistries.SOUND_EVENTS.getKey(r.sound) : null);
            String rSoundId = (rId != null ? rId.toString() : "fear_of_sound:voicechat");
            if (!r.dimensionKey.equals(dimensionKey)) continue;
            if (!rSoundId.equals(soundId)) continue;
            int mergeRadius = FearofSoundConfig.MERGE_RADIUS_BLOCKS_CACHE;
            int mergeRadiusSqr = mergeRadius * mergeRadius;
            if (r.pos.distSqr(pos) <= mergeRadiusSqr) {
                r.ticksRemaining = Math.max(r.ticksRemaining, lifetime);
                return;
            }
        }

        SoundRecord rec = new SoundRecord(se, pos, lifetime, dimensionKey, range, weight);
        RECENT_SOUNDS.add(rec);
        indexAdd(rec);
        MAX_RANGE_SEEN = Math.max(MAX_RANGE_SEEN, (int) Math.ceil(range));

        if (RECENT_SOUNDS.size() > FearofSoundConfig.MAX_SOUNDS_TRACKED_CACHE) {

            SoundRecord toRemove = null;
            double best = Double.MAX_VALUE;
            for (SoundRecord r : RECENT_SOUNDS) {
                if (r.weight < best) { best = r.weight; toRemove = r; }
            }
            if (toRemove != null) {
                RECENT_SOUNDS.remove(toRemove);
                indexRemove(toRemove);
            }
        }
    }

    public static synchronized void tick() {
        CURRENT_TICK++;
        Iterator<SoundRecord> iter = RECENT_SOUNDS.iterator();
        while (iter.hasNext()) {
            SoundRecord r = iter.next();
            r.ticksRemaining--;
            if (r.ticksRemaining <= 0) {
                iter.remove();
                indexRemove(r);
            }
        }
    }

    private static class RaycastCacheKey {
        public final BlockPos mobPos;
        public final BlockPos soundPos;
        public final String soundId;
        public final String dimensionKey;
        public RaycastCacheKey(BlockPos mobPos, BlockPos soundPos, String soundId, String dimensionKey) {
            this.mobPos = mobPos;
            this.soundPos = soundPos;
            this.soundId = soundId;
            this.dimensionKey = dimensionKey;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RaycastCacheKey other)) return false;
            return mobPos.equals(other.mobPos) && soundPos.equals(other.soundPos) && soundId.equals(other.soundId) && dimensionKey.equals(other.dimensionKey);
        }
        @Override
        public int hashCode() {
            return mobPos.hashCode() ^ soundPos.hashCode() ^ soundId.hashCode() ^ dimensionKey.hashCode();
        }
    }
    private static class RaycastCacheValue {
        public final double[] result;
        public final int tick;
        public RaycastCacheValue(double[] result, int tick) { this.result = result; this.tick = tick; }
    }
    private static final LinkedHashMap<RaycastCacheKey, RaycastCacheValue> RAYCAST_CACHE = new LinkedHashMap<>(128, 0.75f, true);

    public static double[] applyBlockMuffling(Level level, BlockPos src, BlockPos dst, double origRange, double origWeight, String soundId) {
        String dimKey = level.dimension().location().toString();
        RaycastCacheKey key = new RaycastCacheKey(dst, src, soundId, dimKey);
        synchronized (RAYCAST_CACHE) {
            RaycastCacheValue cached = RAYCAST_CACHE.get(key);
            if (cached != null && (CURRENT_TICK - cached.tick) <= FearofSoundConfig.RAYCAST_CACHE_TTL_TICKS_CACHE) {
                return cached.result;
            }
        }
        double range = origRange;
        double weight = origWeight;
        int x0 = src.getX(), y0 = src.getY(), z0 = src.getZ();
        int x1 = dst.getX(), y1 = dst.getY(), z1 = dst.getZ();
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), dz = Math.abs(z1 - z0);
        int sx = Integer.compare(x1, x0), sy = Integer.compare(y1, y0), sz = Integer.compare(z1, z0);
        int n = 1 + dx + dy + dz;
        int x = x0, y = y0, z = z0;
        int err1 = dx - dy, err2 = dx - dz;
        for (int i = 0; i < n && range > 0 && weight > 0; i++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (!state.isAir()) {
                if (isCustomWool(state, block)) {
                    range -= FearofSoundConfig.WOOL_BLOCK_RANGE_REDUCTION_CACHE;
                    weight -= FearofSoundConfig.WOOL_BLOCK_WEIGHT_REDUCTION_CACHE;
                } else if (isCustomThin(state, block)) {
                    range -= FearofSoundConfig.THIN_BLOCK_RANGE_REDUCTION_CACHE;
                    weight -= FearofSoundConfig.THIN_BLOCK_WEIGHT_REDUCTION_CACHE;
                } else if (isCustomNonSolid(state, block)) {
                    range -= FearofSoundConfig.NON_SOLID_BLOCK_RANGE_REDUCTION_CACHE;
                    weight -= FearofSoundConfig.NON_SOLID_BLOCK_WEIGHT_REDUCTION_CACHE;
                } else if (isCustomSolid(state, block)) {
                    range -= FearofSoundConfig.SOLID_BLOCK_RANGE_REDUCTION_CACHE;
                    weight -= FearofSoundConfig.SOLID_BLOCK_WEIGHT_REDUCTION_CACHE;
                }
            }
            if (x == x1 && y == y1 && z == z1) break;
            int e2 = 2 * err1;
            int e3 = 2 * err2;
            if (e2 > -dy) { err1 -= dy; x += sx; }
            if (e2 < dx)  { err1 += dx; y += sy; }
            if (e3 > -dz) { err2 -= dz; x += sx; }
            if (e3 < dx)  { err2 += dx; z += sz; }
        }
        if (range < 0) range = 0;
        if (weight < 0) weight = 0;
        double[] result = new double[]{range, weight};
        synchronized (RAYCAST_CACHE) {
            RAYCAST_CACHE.put(key, new RaycastCacheValue(result, CURRENT_TICK));

            while (RAYCAST_CACHE.size() > FearofSoundConfig.RAYCAST_CACHE_MAX_ENTRIES_CACHE) {
                Iterator<Map.Entry<RaycastCacheKey, RaycastCacheValue>> it = RAYCAST_CACHE.entrySet().iterator();
                if (it.hasNext()) { it.next(); it.remove(); } else break;
            }
        }
        return result;
    }

    private static boolean isBlockInConfigList(BlockState state, Block block, java.util.List<String> configList) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id != null && configList.contains(id.toString())) return true;
        for (String entry : configList) {
            if (entry.startsWith("#")) {
                String tagName = entry.substring(1);
                TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, new ResourceLocation(tagName));
                if (state.is(tagKey)) return true;
            }
        }
        return false;
    }
    private static boolean isCustomWool(BlockState state, Block block) {
        return isBlockInConfigList(state, block, FearofSoundConfig.CUSTOM_WOOL_BLOCKS_CACHE) || state.is(BlockTags.WOOL);
    }
    private static boolean isCustomSolid(BlockState state, Block block) {
        return isBlockInConfigList(state, block, FearofSoundConfig.CUSTOM_SOLID_BLOCKS_CACHE) || state.isSolid();
    }
    private static boolean isCustomNonSolid(BlockState state, Block block) {
        return isBlockInConfigList(state, block, FearofSoundConfig.CUSTOM_NON_SOLID_BLOCKS_CACHE) || !state.isSolid();
    }
    private static boolean isCustomThin(BlockState state, Block block) {
        if (isBlockInConfigList(state, block, FearofSoundConfig.CUSTOM_THIN_BLOCKS_CACHE)) return true;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("pane") || path.contains("iron_bars") || path.contains("painting") || path.contains("fence") || path.contains("trapdoor") || path.contains("door") || path.contains("ladder") || path.contains("scaffolding") || path.contains("rail");
    }

    private static CellKey cellKey(BlockPos pos, String dimensionKey) {
        int gs = Math.max(1, FearofSoundConfig.SPATIAL_GRID_SIZE_CACHE);
        int cx = Math.floorDiv(pos.getX(), gs);
        int cy = Math.floorDiv(pos.getY(), gs);
        int cz = Math.floorDiv(pos.getZ(), gs);
        return new CellKey(dimensionKey, cx, cy, cz);
    }

    private static synchronized void indexAdd(SoundRecord r) {
        CellKey key = cellKey(r.pos, r.dimensionKey);
        SPATIAL_INDEX.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
    }

    private static synchronized void indexRemove(SoundRecord r) {
        CellKey key = cellKey(r.pos, r.dimensionKey);
        List<SoundRecord> list = SPATIAL_INDEX.get(key);
        if (list != null) {
            list.remove(r);
            if (list.isEmpty()) SPATIAL_INDEX.remove(key);
        }
    }

    private static List<SoundRecord> getCandidatesAround(BlockPos pos, String dimensionKey, int radiusCells) {
        List<SoundRecord> out = new ArrayList<>();
        int gs = Math.max(1, FearofSoundConfig.SPATIAL_GRID_SIZE_CACHE);
        int cx = Math.floorDiv(pos.getX(), gs);
        int cy = Math.floorDiv(pos.getY(), gs);
        int cz = Math.floorDiv(pos.getZ(), gs);
        for (int dx = -radiusCells; dx <= radiusCells; dx++) {
            for (int dy = -radiusCells; dy <= radiusCells; dy++) {
                for (int dz = -radiusCells; dz <= radiusCells; dz++) {
                    List<SoundRecord> list = SPATIAL_INDEX.get(new CellKey(dimensionKey, cx + dx, cy + dy, cz + dz));
                    if (list != null) out.addAll(list);
                }
            }
        }
        return out;
    }

    public static void pruneIrrelevantSounds(Level level) {
        if (RECENT_SOUNDS.isEmpty()) return;
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, level.getWorldBorder().getCollisionShape().bounds());
        Iterator<SoundRecord> iter = RECENT_SOUNDS.iterator();
        while (iter.hasNext()) {
            SoundRecord sound = iter.next();
            if (sound.ticksRemaining > 5) continue;
            boolean inRange = false;
            for (Mob mob : mobs) {
                if (!mob.isAlive()) continue;
                BlockPos mobPos = mob.blockPosition();
                ResourceLocation soundLoc = sound.sound != null ? ForgeRegistries.SOUND_EVENTS.getKey(sound.sound) : null;
                String soundId = soundLoc != null ? soundLoc.toString() : "fear_of_sound:voicechat";
                double[] muffled = applyBlockMuffling(level, sound.pos, mobPos, sound.range, sound.weight, soundId);
                double range = muffled[0];
                double distSqr = mobPos.distSqr(sound.pos);
                if (distSqr <= range * range) {
                    inRange = true;
                    break;
                }
            }
            if (!inRange) { iter.remove(); indexRemove(sound); }
        }
    }

    public static synchronized SoundRecord findNearestSound(Level level, BlockPos mobPos) {
        String dimensionKey = level.dimension().location().toString();

        SoundRecord bestSound = null;
        double closestDistSqr = Double.MAX_VALUE;
        double highestWeight = -Double.MAX_VALUE;

        int gs = Math.max(1, FearofSoundConfig.SPATIAL_GRID_SIZE_CACHE);
        int radiusCells = Math.max(1, Math.min(8, (MAX_RANGE_SEEN + gs - 1) / gs));
        List<SoundRecord> candidates = getCandidatesAround(mobPos, dimensionKey, radiusCells);
        if (candidates.isEmpty()) candidates = RECENT_SOUNDS;

        for (SoundRecord r : candidates) {
            if (!r.dimensionKey.equals(dimensionKey)) continue;

            ResourceLocation soundLoc = r.sound != null ? ForgeRegistries.SOUND_EVENTS.getKey(r.sound) : null;
            String soundId = soundLoc != null ? soundLoc.toString() : "fear_of_sound:voicechat";
            double[] muffled = applyBlockMuffling(level, r.pos, mobPos, r.range, r.weight, soundId);
            double muffledRange = muffled[0];
            double muffledWeight = muffled[1];

            double rangeSqr = muffledRange * muffledRange;
            double distSqr = mobPos.distSqr(r.pos);

            if (distSqr <= rangeSqr) {
                if (muffledWeight > highestWeight || (Math.abs(muffledWeight - highestWeight) < 0.001 && distSqr < closestDistSqr)) {
                    highestWeight = muffledWeight;
                    closestDistSqr = distSqr;
                    bestSound = r;
                }
            }
        }

        if (bestSound != null) {
             FearofSoundMod.LOGGER.trace("[SoundTracker] Found best sound for mob at {}: {}", mobPos, bestSound);
        }
        return bestSound;
    }
}