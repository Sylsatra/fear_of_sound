package com.example.fear_of_sound.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.example.fear_of_sound.FearofSoundMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.*;

public class FearofSoundConfig {
    public static volatile long CONFIG_GENERATION = 0L;

    public static class Common {
        public final ForgeConfigSpec.ConfigValue<List<? extends CommentedConfig>> bypassedEntities;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> affectedEntities;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> playDeadEntities;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> soundConfigsRaw;
        public final ForgeConfigSpec.IntValue soundLifetimeTicks;
        public final ForgeConfigSpec.IntValue scanCooldownTicks;
        public final ForgeConfigSpec.DoubleValue arrivalDistance;
        public final ForgeConfigSpec.DoubleValue mobMoveSpeed;
        public final ForgeConfigSpec.BooleanValue enableHideAndFreeze;
        public final ForgeConfigSpec.IntValue recoveryQuietTicks;
        public final ForgeConfigSpec.BooleanValue enableScentAwareness;
        public final ForgeConfigSpec.IntValue scentAwarenessRadius;
        public final ForgeConfigSpec.IntValue scentMaxAgeTicks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerStepSounds;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerSpeedConfigsRaw;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> fearofsoundadvance;
        public final ForgeConfigSpec.BooleanValue enableSoundBasedDetection;
        public final ForgeConfigSpec.BooleanValue enableMovementBasedDetection;
        public final ForgeConfigSpec.IntValue movementCheckFrequencyTicks;
        public final ForgeConfigSpec.BooleanValue enableVoiceChatIntegration;
        public final ForgeConfigSpec.IntValue voiceChatWhisperRange;
        public final ForgeConfigSpec.IntValue voiceChatNormalRange;
        public final ForgeConfigSpec.DoubleValue voiceChatWeight;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> voiceChatDbThresholdMap;
        public final ForgeConfigSpec.BooleanValue enableTaczIntegration;
        public final ForgeConfigSpec.DoubleValue taczReloadRange;
        public final ForgeConfigSpec.IntValue taczReloadWeight;
        public final ForgeConfigSpec.DoubleValue taczShootRange;
        public final ForgeConfigSpec.IntValue taczShootWeight;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> taczGunShootDecibels;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> taczAttachmentReductions;
        public final ForgeConfigSpec.DoubleValue soundSwitchRatio;
        public final ForgeConfigSpec.IntValue panicMinTicks;
        public final ForgeConfigSpec.IntValue panicMaxTicks;
        public final ForgeConfigSpec.IntValue woolBlockRangeReduction;
        public final ForgeConfigSpec.DoubleValue woolBlockWeightReduction;
        public final ForgeConfigSpec.IntValue solidBlockRangeReduction;
        public final ForgeConfigSpec.DoubleValue solidBlockWeightReduction;
        public final ForgeConfigSpec.IntValue nonSolidBlockRangeReduction;
        public final ForgeConfigSpec.DoubleValue nonSolidBlockWeightReduction;
        public final ForgeConfigSpec.IntValue thinBlockRangeReduction;
        public final ForgeConfigSpec.DoubleValue thinBlockWeightReduction;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customWoolBlocks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customSolidBlocks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customNonSolidBlocks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> customThinBlocks;
        public final ForgeConfigSpec.BooleanValue woolMufflingEnabled;

        public final ForgeConfigSpec.IntValue losRange;
        public final ForgeConfigSpec.IntValue losCooldown;
        public final ForgeConfigSpec.IntValue losCheckIntervalTicks;
        public final ForgeConfigSpec.IntValue losPerTickCap;
        public final ForgeConfigSpec.IntValue losScanRangeCap;

        public final ForgeConfigSpec.IntValue maxSoundsTracked;
        public final ForgeConfigSpec.BooleanValue enableQuantifiedIntegration;
        public final ForgeConfigSpec.IntValue raycastCacheTtlTicks;
        public final ForgeConfigSpec.IntValue raycastCacheMaxEntries;
        public final ForgeConfigSpec.IntValue spatialGridSize;
        public final ForgeConfigSpec.IntValue mergeRadius;

        Common(ForgeConfigSpec.Builder builder) {
            builder.push("General");

            bypassedEntities = builder
                .comment(
                    "A list of mobs to be BYPASSED by this mod's AI. Mobs matching ANY definition in this list will be ignored.",
                    "Each definition is a table, e.g., [[{...}]]. You can define 'custom_name' and/or 'nbt'.",
                    "The 'nbt' check is a partial match. The mob only needs to have the tags you specify."
                )
                .defineList("bypassedEntities",
                    createDefaultBypassedEntities(),
                    obj -> obj instanceof CommentedConfig
                );

            affectedEntities = builder
                .comment("List of mobs that will be affected by sounds. Example: ['minecraft:zombie', 'minecraft:skeleton'].")
                .defineList("affectedEntities", Arrays.asList(
                    "minecraft:bat", "minecraft:cat", "minecraft:chicken", "minecraft:cod", "minecraft:cow",
                    "minecraft:dolphin", "minecraft:donkey", "minecraft:horse", "minecraft:mooshroom", "minecraft:mule",
                    "minecraft:ocelot", "minecraft:parrot", "minecraft:pig", "minecraft:rabbit", "minecraft:salmon",
                    "minecraft:sheep", "minecraft:squid", "minecraft:tropical_fish", "minecraft:turtle", "minecraft:glow_squid",
                    "minecraft:pufferfish", "minecraft:axolotl", "minecraft:fox", "minecraft:llama", "minecraft:panda", "minecraft:polar_bear"
                ), obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);

            playDeadEntities = builder
                .comment("Legacy immediate-faint profile for selected mobs; interrupted by damage and bounded by the maximum freeze duration.")
                .defineList("playDeadEntities", List.of("minecraft:goat"),
                    obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);

            soundConfigsRaw = builder
                .comment("List of sounds that scare mobs. Format: 'soundId;range;weight'. Example: 'minecraft:block.bell.use;20;5' (range in blocks, weight = priority, higher means more scary).")
                .defineList("soundConfigs", Arrays.asList(
                    "minecraft:block.lever.click;5;3",
                    "minecraft:block.wooden_trapdoor.open;8;3",
                    "minecraft:block.wooden_trapdoor.close;8;3",
                    "minecraft:block.bamboo_wood_trapdoor.open;10;3",
                    "minecraft:block.bamboo_wood_trapdoor.close;10;3",
                    "minecraft:block.cherry_wood_trapdoor.open;10;3",
                    "minecraft:block.cherry_wood_trapdoor.close;10;3",
                    "minecraft:block.iron_trapdoor.open;15;4",
                    "minecraft:block.iron_trapdoor.close;15;4",
                    "minecraft:block.wooden_door.open;12;4",
                    "minecraft:block.wooden_door.close;12;4",
                    "minecraft:block.bamboo_wood_door.open;15;4",
                    "minecraft:block.bamboo_wood_door.close;15;4",
                    "minecraft:block.cherry_wood_door.open;15;4",
                    "minecraft:block.cherry_wood_door.close;15;4",
                    "minecraft:block.iron_door.open;20;5",
                    "minecraft:block.iron_door.close;20;5",
                    "minecraft:block.fence_gate.open;10;3",
                    "minecraft:block.fence_gate.close;10;3",
                    "minecraft:block.piston.extend;20;4",
                    "minecraft:block.piston.contract;20;4",
                    "minecraft:block.dispenser.dispense;12;4",
                    "minecraft:block.dispenser.launch;12;4",
                    "minecraft:block.anvil.land;25;5",
                    "minecraft:block.anvil.use;25;5",
                    "minecraft:block.anvil.destroy;25;5",
                    "minecraft:block.sand.fall;6;3",
                    "minecraft:block.gravel.fall;6;3",
                    "minecraft:block.grass.break;3;2",
                    "minecraft:block.scaffolding.break;3;2",
                    "tacz:target_block_hit;6;3",
                    "minecraft:entity.boat.paddle_water;8;3",
                    "minecraft:ambient.underwater.enter;4;2",
                    "minecraft:ambient.underwater.exit;4;2",
                    "minecraft:block.chest.open;5;2",
                    "minecraft:block.chest.close;5;2",
                    "minecraft:block.barrel.open;5;2",
                    "minecraft:block.barrel.close;5;2",
                    "minecraft:block.ender_chest.open;6;2",
                    "minecraft:block.ender_chest.close;6;2",
                    "minecraft:block.shulker_box.open;6;2",
                    "minecraft:block.shulker_box.close;6;2",
                    "minecraft:block.bell.use;30;5",
                    "minecraft:block.bell.resonate;15;4",
                    "minecraft:block.furnace.fire_crackle;8;3",
                    "minecraft:entity.generic.explode;50;7",
                    "minecraft:entity.firework_rocket.launch;10;3",
                    "minecraft:entity.firework_rocket.blast;20;5",
                    "minecraft:entity.firework_rocket.large_blast;30;6",
                    "minecraft:entity.lightning_bolt.impact;70;7",
                    "minecraft:item.trident.thunder;70;7",
                    "minecraft:entity.lightning_bolt.thunder;80;8",
                    "minecraft:block.note_block.xylophone;15;4",
                    "minecraft:block.note_block.banjo;15;4",
                    "minecraft:block.note_block.pling;15;4",
                    "minecraft:block.note_block.bass;15;4",
                    "minecraft:block.note_block.bell;15;4",
                    "minecraft:block.note_block.chime;15;4",
                    "minecraft:block.note_block.flute;15;4",
                    "minecraft:block.note_block.guitar;15;4",
                    "minecraft:block.note_block.harp;15;4",
                    "minecraft:block.note_block.snare;15;4",
                    "minecraft:block.note_block.hat;15;4",
                    "minecraft:block.note_block.cow_bell;15;4",
                    "minecraft:block.note_block.didgeridoo;15;4",
                    "minecraft:block.note_block.bit;15;4",
                    "minecraft:block.note_block.bell;15;4",
                    "minecraft:block.note_block.basedrum;15;4", 
                    "minecraft:block.note_block.iron_xylophone;15;4",
                    "minecraft:music_disc.11;30;5",
                    "minecraft:music_disc.13;30;5",
                    "minecraft:music_disc.cat;30;5",
                    "minecraft:music_disc.chirp;30;5",
                    "minecraft:music_disc.far;30;5",
                    "minecraft:music_disc.mall;30;5",
                    "minecraft:music_disc.mellohi;30;5",
                    "minecraft:music_disc.stal;30;5",
                    "minecraft:music_disc.strad;30;5",
                    "minecraft:music_disc.wait;30;5",
                    "minecraft:music_disc.ward;30;5",
                    "minecraft:music_disc.pigstep;30;5",
                    "minecraft:music_disc.blocks;30;5",
                    "minecraft:music_disc.5;30;5",
                    "minecraft:music_disc.otherside;30;5",
                    "minecraft:music_disc.relic;30;5",
                    "minecraft:item.goat_horn.sound.0;40;3",
                    "minecraft:item.goat_horn.sound.1;40;3",
                    "minecraft:item.goat_horn.sound.2;40;3",
                    "minecraft:item.goat_horn.sound.3;40;3",
                    "minecraft:item.goat_horn.sound.4;40;3",
                    "minecraft:item.goat_horn.sound.5;40;3",
                    "minecraft:item.goat_horn.sound.6;40;3",
                    "minecraft:item.goat_horn.sound.7;40;3",
                    "minecraft:entity.fox.aggro;8;4",
                    "minecraft:entity.fox.screech;16;3",
                    "minecraft:entity.wolf.growl;10;4",
                    "minecraft:entity.wolf.howl;32;5",
                    "minecraft:entity.polar_bear.warning;20;4",
                    "minecraft:entity.ravager.roar;40;5"
                ), obj -> {
                    if (!(obj instanceof String str)) return false;
                    String[] parts = str.split(";", 3);
                    if (parts.length != 3) return false;
                    if (ResourceLocation.tryParse(parts[0]) == null) return false;
                    try {
                        int range = Integer.parseInt(parts[1]);
                        int weight = Integer.parseInt(parts[2]);
                        return range > 0 && weight > 0;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });

            soundLifetimeTicks = builder
                .comment("How long (in ticks) a sound remains scary. 20 ticks = 1 second. Example: 400 = 20 seconds.")
                .defineInRange("soundLifetimeTicks", 40, 20, 1200);

            scanCooldownTicks = builder
                .comment("How often (in ticks) mobs scan for new sounds. 20 = once per second. Lower = more responsive, higher = less lag.")
                .defineInRange("scanCooldownTicks", 10, 5, 100);

            arrivalDistance = builder
                .comment("Desired clearance from a threat when scoring refuges. Larger values favor cover farther from the threat source.")
                .defineInRange("arrivalDistance", 4.0, 1.0, 64.0);

            mobMoveSpeed = builder
                .comment("Movement speed modifier while a mob seeks cover or escapes. 1.0 = normal mob speed, 2.0 = twice as fast.")
                .defineInRange("mobMoveSpeed", 1.5, 0.5, 2.0);

            enableHideAndFreeze = builder
                .comment("Enable adaptive fear: affected mobs seek cover, freeze when concealed, then recover after threats fade.")
                .define("enableHideAndFreeze", true);

            recoveryQuietTicks = builder
                .comment("How long a mob waits without a threat before returning to calm. 20 ticks = 1 second.")
                .defineInRange("recoveryQuietTicks", 60, 10, 600);

            enableScentAwareness = builder
                .comment("Use nearby Sound Attract scent as evidence: unfamiliar trails cause brief caution; fresh trails from known threats can trigger relocation.")
                .define("enableScentAwareness", true);

            scentAwarenessRadius = builder
                .comment("Maximum radius for local scent evidence used by adaptive fear. The optional provider reads only nearby loaded scent chunks.")
                .defineInRange("scentAwarenessRadius", 16, 4, 16);

            scentMaxAgeTicks = builder
                .comment("Maximum age for scent evidence used by adaptive fear. 20 ticks = 1 second.")
                .defineInRange("scentMaxAgeTicks", 600, 20, 2400);

            woolBlockRangeReduction = builder
                .comment("How much the range is reduced for each wool block between mob and sound. Example: 30 means wool blocks reduce the sound range by 30 blocks.")
                .defineInRange("woolBlockRangeReduction", 16, 0, 100);

            woolBlockWeightReduction = builder
                .comment("How much the weight is reduced for each wool block between mob and sound. Example: 1 means wool blocks make sounds less scary.")
                .defineInRange("woolBlockWeightReduction", 3.0, 0.0, 10.0);

            solidBlockRangeReduction = builder
                .comment("How much the range is reduced for each solid block (not wool) between mob and sound.")
                .defineInRange("solidBlockRangeReduction", 12, 0, 100);

            solidBlockWeightReduction = builder
                .comment("How much the weight is reduced for each solid block (not wool) between mob and sound.")
                .defineInRange("solidBlockWeightReduction", 2.0, 0.0, 10.0);

            nonSolidBlockRangeReduction = builder
                .comment("How much the range is reduced for each non-solid block (like glass) between mob and sound.")
                .defineInRange("nonSolidBlockRangeReduction", 8, 0, 50);

            nonSolidBlockWeightReduction = builder
                .comment("How much the weight is reduced for each non-solid block (like glass) between mob and sound.")
                .defineInRange("nonSolidBlockWeightReduction", 1.0, 0.0, 5.0);

            thinBlockRangeReduction = builder
                .comment("How much the range is reduced for each thin block (like panes, fences) between mob and sound.")
                .defineInRange("thinBlockRangeReduction", 4, 0, 25);

            thinBlockWeightReduction = builder
                .comment("How much the weight is reduced for each thin block (like panes, fences) between mob and sound.")
                .defineInRange("thinBlockWeightReduction", 0.5, 0.0, 2.0);

            woolMufflingEnabled = builder
                .comment("Enable wool muffling.")
                .define("woolMufflingEnabled", true);

            builder.pop();

            builder.push("Player Movement Sounds");

            enableSoundBasedDetection = builder
                .comment("Use real in-game player sound events (steps, land/fall, block step) to trigger detection using configured ranges/weights. Disable to ignore vanilla/emitted sounds.")
                .define("enableSoundBasedDetection", true);

            enableMovementBasedDetection = builder
                .comment("Use polled movement states (sneak, crawl, etc.) every 'movementCheckFrequencyTicks' to synthesize detection even when no sound plays. Disable to stop state-based checks.")
                .define("enableMovementBasedDetection", true);

            movementCheckFrequencyTicks = builder
                .comment("How often (in ticks) to check player movement state. Lower = more accurate, higher = less lag. Example: 4 = 5 times/sec.")
                .defineInRange("movementCheckFrequencyTicks", 10, 1, 20);

            playerStepSounds = builder
                .comment("List of sound event IDs considered player steps (e.g., walking, running, sneaking). Only change if you add new sounds.")
                .defineList("playerStepSounds", Arrays.asList(
                    "minecraft:block.stone.step",
                    "minecraft:block.wood.step",
                    "minecraft:block.grass.step",
                    "minecraft:block.deepslate.step",
                    "minecraft:block.deepslate_bricks.step",
                    "minecraft:block.deepslate_tiles.step",
                    "minecraft:block.mud_bricks.step",
                    "minecraft:block.nether_bricks.step",
                    "minecraft:block.netherrack.step",
                    "minecraft:block.sand.step",
                    "minecraft:block.gravel.step",
                    "minecraft:block.snow.step",
                    "minecraft:block.suspicious_gravel.step",
                    "minecraft:block.suspicious_sand.step",
                    "minecraft:block.polished_deepslate.step",
                    "minecraft:block.packed_mud.step",
                    "minecraft:block.nylium.step",
                    "minecraft:block.roots.step",
                    "minecraft:block.tuff.step",
                    "minecraft:block.soul_sand.step",
                    "minecraft:block.soul_soil.step",
                    "minecraft:block.pointed_dripstone.step",
                    "minecraft:block.sculk.step",
                    "minecraft:block.sculk_sensor.step",
                    "minecraft:block.sculk_catalyst.step",
                    "minecraft:block.sculk_shrieker.step",
                    "minecraft:block.sculk_vein.step",
                    "minecraft:block.shroomlight.step",
                    "minecraft:block.spore_blossom.step",
                    "minecraft:block.stem.step",
                    "minecraft:block.lodestone.step",
                    "minecraft:block.scaffolding.step",
                    "minecraft:block.wet_grass.step",
                    "minecraft:block.wet_sponge.step",
                    "minecraft:block.ladder.step",
                    "minecraft:block.lantern.step",
                    "minecraft:block.wool.step"
                ), obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);

            playerSpeedConfigsRaw = builder
                .comment("List of player speed configs for step sounds. Format: 'minSpeed;maxSpeed;range;weight'. Example: '0.1;1.0;2;1'.")
                .defineList("playerSpeedConfigs", Arrays.asList(
                    "0.1;1.0;2;1",
                    "0.1;1.5;4;1",    
                    "1.51;4.5;8;1",  
                    "4.51;6.0;16;1",  
                    "6.01;10.0;24;1" 
                ), obj -> obj instanceof String);

            builder.pop();

            builder.push("Custom Block Muffling");
            customWoolBlocks = builder.defineList("customWoolBlocks", java.util.Collections.emptyList(), o -> o instanceof String);
            customSolidBlocks = builder.defineList("customSolidBlocks", java.util.Collections.emptyList(), o -> o instanceof String);
            customNonSolidBlocks = builder.defineList("customNonSolidBlocks", java.util.Collections.emptyList(), o -> o instanceof String);
            customThinBlocks = builder.defineList("customThinBlocks", java.util.Collections.emptyList(), o -> o instanceof String);
            builder.pop();

            builder.push("Voice Chat Integration (Requires Simple Voice Chat mod)");
            enableVoiceChatIntegration = builder
                .define("enableVoiceChatIntegration", true);
                
            voiceChatWhisperRange = builder
                .comment("The range (in blocks) at which voice chat is considered a whisper.")
                .defineInRange("voiceChatWhisperRange", 4, 0, 64);
                
            voiceChatNormalRange = builder
                .comment("The range (in blocks) at which voice chat is considered normal.")
                .defineInRange("voiceChatNormalRange", 16, 0, 128);
                
            voiceChatWeight = builder
                .comment("The weight of voice chat sounds. Higher means more scary to mobs.")
                .defineInRange("voiceChatWeight", 10.0, 0.0, 100.0);
            voiceChatDbThresholdMap = builder
                .comment(
                    "Mapping from normalized dB thresholds to range multipliers for Simple Voice Chat.",
                    "Normalized dB is in [0..127], where 0 = silence and 127 = max peak (0 dBFS).",
                    "Each entry format: 'threshold:multiplier'. Entries are evaluated from highest threshold to lowest.",
                    "Defaults: 110->2.0, 90->1.8, 75->1.5, 50->1.0, 30->0.7, 10->0.3, 0->0.05"
                )
                .defineList(
                    "voiceChatDbThresholdMap",
                    Arrays.asList("110:2.0", "90:1.8", "75:1.5", "50:1.0", "30:0.7", "10:0.3", "0:0.05"),
                    obj -> {
                        if (!(obj instanceof String s)) return false;
                        String[] parts = s.split(":", 2);
                        if (parts.length != 2) return false;
                        try {
                            Double.parseDouble(parts[0].trim());
                            Double.parseDouble(parts[1].trim());
                            return true;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                );
            
            builder.pop(); 

            builder.push("Sound Behavior");
            soundSwitchRatio = builder
                .comment("When multiple sounds overlap, only switch if the new sound's weight is at least this ratio times the current one.")
                .defineInRange("soundSwitchRatio", 1.1, 1.0, 10.0);

            panicMinTicks = builder
                .comment("Initial panic delay minimum (ticks) before a mob starts fleeing. Set 0 for immediate flee.")
                .defineInRange("panicMinTicks", 0, 0, 200);
            panicMaxTicks = builder
                .comment("Initial panic delay maximum (ticks). If max > min, a random value in [min, max) is used.")
                .defineInRange("panicMaxTicks", 0, 0, 200);
            builder.pop();

            builder.push("Advanced Fear Overrides");
            fearofsoundadvance = builder
                .comment(
                    "Advanced conditional rules that gate whether a mob fears a sound.",
                    "Format: name;entities;nbt_or_predicates;sounds",
                    "- entities/sounds: comma-separated resource locations or '*'",
                    "- nbt_or_predicates: OR list of SNBT objects or predicates (health<=X, health>=X, health<X, health>X, health_pct<=p)",
                    "Examples:",
                    "  low_health_any;*;health_pct<=0.30;*",
                    "  low_health_bell_fear;minecraft:zombie, minecraft:skeleton;health<=8;minecraft:block.bell.use",
                    "  baby_cows_bell;minecraft:cow;{Age:-2147483648};minecraft:block.bell.use",
                    "  zombie_low_hp_tacz;minecraft:zombie;health_pct<0.30;tacz:gun"
                )
                .defineList(
                    "fearofsoundadvance",
                    java.util.Collections.emptyList(),
                    obj -> obj instanceof String
                );
            builder.pop();

            builder.push("Line-of-sight fear parameters");

            losRange = builder
                .comment("Fallback LOS radius (in blocks) used when Sound Attract does not provide a detection range.")
                .defineInRange("losRange", 32, 1, 256);

            losCooldown = builder
                .comment("Lifetime in ticks for a LOS-triggered synthetic sound (acts as a cooldown).")
                .defineInRange("losCooldown", 10, 1, 200);
            losCheckIntervalTicks = builder
                .comment("How often each mob is checked for LOS detection (in ticks). Higher reduces CPU.")
                .defineInRange("losCheckIntervalTicks", 10, 1, 200);
            losPerTickCap = builder
                .comment("Maximum number of mobs processed by LOS per server tick. 0 or negative disables the cap.")
                .defineInRange("losPerTickCap", 24, 0, 2048);
            losScanRangeCap = builder
                .comment("Maximum scan radius (in blocks) around each player when Sound Attract is present. Bounds AABB size. Lower reduces CPU. Applies only with SA present.")
                .defineInRange("losScanRangeCap", 48, 16, 256);
            builder.pop();

            builder.push("TaCz Gun Integration");

            enableTaczIntegration = builder
                .define("enableTaczIntegration", true);

            taczReloadRange = builder
                .comment("The range (in blocks) at which TaCz gun reload sounds are heard.")
                .defineInRange("taczReloadRange", 8.0, 0.0, 256.0);

            taczReloadWeight = builder
                .comment("The weight of TaCz gun reload sounds. Higher means more scary to mobs.")
                .defineInRange("taczReloadWeight", 3, 0, 100);

            taczShootRange = builder
                .comment("The range (in blocks) at which TaCz gun shoot sounds are heard.")
                .defineInRange("taczShootRange", 128.0, 0.0, 512.0);

            taczShootWeight = builder
                .comment("The weight of TaCz gun shoot sounds. Higher means more scary to mobs.")
                .defineInRange("taczShootWeight", 10, 0, 100);

            taczGunShootDecibels = builder
                .comment("List of TaCz gun shoot decibel levels. Format: 'soundId;decibel'. Example: 'tacz:ai_awp;166.5'.")
                .defineList("taczGunShootDecibels", Arrays.asList(
                    "tacz:ai_awp;166.5", "tacz:cz75;163", "tacz:glock_17;163", "tacz:p320;163", "tacz:uzi;159.8",
                    "tacz:hk_mp5a5;159.8", "tacz:m95;159.7", "tacz:deagle;159.7", "tacz:ak47;158.9", "tacz:m4a1;158.9",
                    "tacz:m16a1;158.9", "tacz:m16a4;158.9", "tacz:hk416d;158.9", "tacz:aug;158.9", "tacz:mk14;158.9",
                    "tacz:m249;158.9", "tacz:rpk;158.9", "tacz:ump45;157.0", "tacz:vector45;157.0", "tacz:db_short;156.3",
                    "tacz:db_long;156.3", "tacz:aa12;156.3", "tacz:scar_h;156.2", "tacz:hk_g3;156.2", "tacz:sks_tactical;156.0"
                ), obj -> {
                    if (!(obj instanceof String str)) return false;
                    String[] parts = str.split(";", 2);
                    if (parts.length != 2) return false;
                    if (ResourceLocation.tryParse(parts[0]) == null) return false;
                    try { Double.parseDouble(parts[1]); return true; } catch (NumberFormatException e) { return false; }
                });

            taczAttachmentReductions = builder
                .comment("List of TaCz gun attachment decibel reductions. Format: 'soundId;reduction'. Example: 'tacz:muzzle_silencer_phantom_s1;35'.")
                .defineList("taczAttachmentReductions", Arrays.asList(
                    "tacz:muzzle_silencer_phantom_s1;35", "tacz:muzzle_silencer_vulture;32", "tacz:muzzle_silencer_mirage;30",
                    "tacz:muzzle_silencer_knight_qd;28", "tacz:muzzle_silencer_ursus;25", "tacz:muzzle_silencer_ptilopsis;20",
                    "tacz:deagle_golden_long_barrel;-5"
                ), obj -> {
                    if (!(obj instanceof String str)) return false;
                    String[] parts = str.split(";", 2);
                    if (parts.length != 2) return false;
                    if (ResourceLocation.tryParse(parts[0]) == null) return false;
                    try { Double.parseDouble(parts[1]); return true; } catch (NumberFormatException e) { return false; }
                });
            builder.pop(); 

            builder.push("Performance");
            maxSoundsTracked = builder
                .comment("Maximum number of sounds to keep in memory when Sound Attract is absent. Older/weaker sounds are evicted when exceeded.")
                .defineInRange("maxSoundsTracked", 512, 64, 8192);
            enableQuantifiedIntegration = builder.define("enableQuantifiedIntegration", true);
            raycastCacheTtlTicks = builder
                .comment("How long (ticks) to keep block muffling (raycast) results cached.")
                .defineInRange("raycastCacheTtlTicks", 40, 0, 600);
            raycastCacheMaxEntries = builder
                .comment("Maximum LRU size for block muffling cache entries across all dimensions.")
                .defineInRange("raycastCacheMaxEntries", 4096, 64, 65536);
            spatialGridSize = builder
                .comment("Cell size in blocks for the simple spatial index used when Sound Attract is absent.")
                .defineInRange("spatialGridSize", 32, 8, 128);
            mergeRadius = builder
                .comment("Radius in blocks within which identical sounds are de-duplicated by refreshing lifetime instead of adding a new record.")
                .defineInRange("mergeRadius", 3, 0, 8);
            builder.pop();
        }
    }

    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        final Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }
    private static List<CommentedConfig> createDefaultBypassedEntities() {
        List<CommentedConfig> defaults = new ArrayList<>();
        CommentedConfig petRule = CommentedConfig.inMemory();
        petRule.set("custom_name", "Pet");
        petRule.setComment("custom_name", "Bypasses any mob named 'Pet'");
        defaults.add(petRule);
        CommentedConfig silentRule = CommentedConfig.inMemory();
        silentRule.set("nbt", "{Silent:1b}");
        silentRule.setComment("nbt", "Bypasses any mob that is silent");
        defaults.add(silentRule);
        return defaults;
    }
    public static List<BypassDefinition> BYPASSED_ENTITIES_CACHE = new ArrayList<>();
    public static java.util.Set<String> AFFECTED_ENTITIES_CACHE = new java.util.HashSet<>();
    public static java.util.Set<net.minecraft.world.entity.EntityType<?>> AFFECTED_ENTITY_TYPES_CACHE = new java.util.HashSet<>();
    public static List<String> PLAY_DEAD_ENTITIES_CACHE = new ArrayList<>();
    public static Map<SoundEvent, SoundConfig> SOUND_CONFIGS_CACHE = new HashMap<>();
    public static List<SoundEvent> PLAYER_STEP_SOUNDS_CACHE = new ArrayList<>();
    public static List<PlayerSpeedConfig> PLAYER_SPEED_CONFIGS_CACHE = new ArrayList<>();
    public static boolean SOUND_BASED_DETECTION_ENABLED_CACHE = true;
    public static boolean MOVEMENT_BASED_DETECTION_ENABLED_CACHE = true;
    public static int MOVEMENT_CHECK_FREQUENCY_TICKS_CACHE = 4;
    public static boolean VOICE_CHAT_ENABLED_CACHE = false;
    public static boolean VOICE_CHAT_DETECTION_ENABLED_CACHE = false;
    public static int VOICE_CHAT_WHISPER_RANGE_CACHE = 4;
    public static int VOICE_CHAT_NORMAL_RANGE_CACHE = 16;
    public static double VOICE_CHAT_WEIGHT_CACHE = 1.0;
    public static boolean TACZ_ENABLED_CACHE = false;
    public static double TACZ_RELOAD_RANGE_CACHE = 8.0;
    public static int TACZ_RELOAD_WEIGHT_CACHE = 1;
    public static double TACZ_SHOOT_RANGE_CACHE = 128.0;
    public static int TACZ_SHOOT_WEIGHT_CACHE = 5;
    public static Map<ResourceLocation, Double> TACZ_GUN_SHOOT_DB_CACHE = new HashMap<>();
    public static Map<ResourceLocation, Double> TACZ_ATTACHMENT_REDUCTION_DB_CACHE = new HashMap<>();
    public static double SOUND_SWITCH_RATIO_CACHE = 1.1;
    public static int PANIC_MIN_TICKS_CACHE = 0;
    public static int PANIC_MAX_TICKS_CACHE = 0;
    public static boolean ENABLE_HIDE_AND_FREEZE_CACHE = true;
    public static int RECOVERY_QUIET_TICKS_CACHE = 60;
    public static double ARRIVAL_DISTANCE_CACHE = 4.0;
    public static boolean SCENT_AWARENESS_ENABLED_CACHE = true;
    public static int SCENT_AWARENESS_RADIUS_CACHE = 16;
    public static int SCENT_MAX_AGE_TICKS_CACHE = 600;
    public static int LOS_RANGE_CACHE = 32;
    public static int LOS_COOLDOWN_CACHE = 20;
    public static int LOS_CHECK_INTERVAL_TICKS_CACHE = 20;
    public static int LOS_PER_TICK_CAP_CACHE = 64;
    public static int LOS_SCAN_RANGE_CAP_CACHE = 96;
    public static int MAX_SOUNDS_TRACKED_CACHE = 512;
    public static boolean QUANTIFIED_INTEGRATION_ENABLED_CACHE = true;
    public static int RAYCAST_CACHE_TTL_TICKS_CACHE = 40;
    public static int RAYCAST_CACHE_MAX_ENTRIES_CACHE = 4096;
    public static int SPATIAL_GRID_SIZE_CACHE = 32;
    public static int MERGE_RADIUS_BLOCKS_CACHE = 3;
    public static int WOOL_BLOCK_RANGE_REDUCTION_CACHE = 50;
    public static double WOOL_BLOCK_WEIGHT_REDUCTION_CACHE = 5.0;
    public static int SOLID_BLOCK_RANGE_REDUCTION_CACHE = 30;
    public static double SOLID_BLOCK_WEIGHT_REDUCTION_CACHE = 3.0;
    public static int NON_SOLID_BLOCK_RANGE_REDUCTION_CACHE = 10;
    public static double NON_SOLID_BLOCK_WEIGHT_REDUCTION_CACHE = 1.0;
    public static int THIN_BLOCK_RANGE_REDUCTION_CACHE = 5;
    public static double THIN_BLOCK_WEIGHT_REDUCTION_CACHE = 0.5;
    public static List<String> CUSTOM_WOOL_BLOCKS_CACHE = new ArrayList<>();
    public static List<String> CUSTOM_SOLID_BLOCKS_CACHE = new ArrayList<>();
    public static List<String> CUSTOM_NON_SOLID_BLOCKS_CACHE = new ArrayList<>();
    public static List<String> CUSTOM_THIN_BLOCKS_CACHE = new ArrayList<>();
    public static class SoundConfig {
        public final int range;
        public final double weight;
        public SoundConfig(int range, double weight) { this.range = range; this.weight = weight; }
    }
    public static class PlayerSpeedConfig {
        public final double minSpeed;
        public final double maxSpeed;
        public final int range;
        public final double weight;
        public PlayerSpeedConfig(double minSpeed, double maxSpeed, int range, double weight) {
            this.minSpeed = minSpeed; this.maxSpeed = maxSpeed; this.range = range; this.weight = weight;
        }
    }
    public record BypassDefinition(@Nullable String customName, @Nullable CompoundTag nbt) {
        public static BypassDefinition fromConfig(CommentedConfig configMap) {
            String name = configMap.getOptional("custom_name").map(String::valueOf).orElse(null);
            CompoundTag nbtData = null;
            if (configMap.contains("nbt")) {
                try {
                    nbtData = TagParser.parseTag(configMap.get("nbt"));
                } catch (Exception e) {
                    FearofSoundMod.LOGGER.error("Invalid NBT string in bypassedEntities config: {}", configMap.get("nbt").toString(), e);
                }
            }
            if (name != null || nbtData != null) {
                return new BypassDefinition(name, nbtData);
            }
            return null;
        }
    }
    public static void bakeConfig() {
        BYPASSED_ENTITIES_CACHE.clear();
        List<? extends CommentedConfig> rawBypassed = COMMON.bypassedEntities.get();
        for (CommentedConfig rawEntry : rawBypassed) {
            BypassDefinition definition = BypassDefinition.fromConfig(rawEntry);
            if (definition != null) {
                BYPASSED_ENTITIES_CACHE.add(definition);
            }
        }
        
        AFFECTED_ENTITIES_CACHE = new java.util.HashSet<>(COMMON.affectedEntities.get());
        AFFECTED_ENTITY_TYPES_CACHE.clear();
        for (String id : AFFECTED_ENTITIES_CACHE) {
            try {
                var rl = net.minecraft.resources.ResourceLocation.tryParse(id);
                if (rl != null) {
                    var et = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(rl);
                    if (et != null) AFFECTED_ENTITY_TYPES_CACHE.add(et);
                }
            } catch (Exception ignored) {}
        }
        PLAY_DEAD_ENTITIES_CACHE = new ArrayList<>(COMMON.playDeadEntities.get());
        SOUND_CONFIGS_CACHE.clear();
        Map<SoundEvent, SoundConfig> bakedSounds = new HashMap<>();
        List<? extends String> rawSounds = COMMON.soundConfigsRaw.get(); 

        for (String rawEntry : rawSounds) {
            try {
                String[] parts = rawEntry.split(";", 3);
                if (parts.length == 3) {
                    ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                    if (rl != null) {
                         SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(rl);
                         if (se != null) {
                            int range = Integer.parseInt(parts[1]);
                            double weight = Double.parseDouble(parts[2]);
                            if (range > 0 && weight > 0) {
                                bakedSounds.put(se, new SoundConfig(range, weight));
                            } 
                         } 
                    } 
                } 
            } catch (Exception e) {
            }
        }
        SOUND_CONFIGS_CACHE = bakedSounds;

        SOUND_BASED_DETECTION_ENABLED_CACHE = COMMON.enableSoundBasedDetection.get();
        MOVEMENT_BASED_DETECTION_ENABLED_CACHE = COMMON.enableMovementBasedDetection.get();
        MOVEMENT_CHECK_FREQUENCY_TICKS_CACHE = COMMON.movementCheckFrequencyTicks.get();

        PLAYER_STEP_SOUNDS_CACHE.clear();
        List<SoundEvent> bakedPlayerSounds = new ArrayList<>();
        for (String soundIdStr : COMMON.playerStepSounds.get()) {
            try {
                ResourceLocation rl = ResourceLocation.tryParse(soundIdStr);
                if (rl != null) {
                    SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(rl);
                    if (se != null) {
                        bakedPlayerSounds.add(se);
                    } 
                } 
            } catch (Exception e) {
            }
        }
        PLAYER_STEP_SOUNDS_CACHE = bakedPlayerSounds;

        PLAYER_SPEED_CONFIGS_CACHE.clear();
        List<PlayerSpeedConfig> bakedSpeedConfigs = new ArrayList<>();
        List<? extends String> rawSpeedConfigs = COMMON.playerSpeedConfigsRaw.get(); 

        for (String rawEntry : rawSpeedConfigs) {
            try {
                String[] parts = rawEntry.split(";", 4);
                if (parts.length == 4) {
                    double minSpeed = Double.parseDouble(parts[0]);
                    double maxSpeed = Double.parseDouble(parts[1]);
                    int range = Integer.parseInt(parts[2]);
                    double weight = Double.parseDouble(parts[3]);
                    if (minSpeed >= 0 && maxSpeed >= minSpeed && range > 0 && weight > 0) {
                        bakedSpeedConfigs.add(new PlayerSpeedConfig(minSpeed, maxSpeed, range, weight));
                    } 
                } 
            } catch (Exception e) {
            }
        }

        bakedSpeedConfigs.sort(Comparator.comparingDouble(c -> c.minSpeed));
        PLAYER_SPEED_CONFIGS_CACHE = bakedSpeedConfigs;

        try {
            AdvancedFearOverrideRegistry.rebuild(COMMON.fearofsoundadvance.get());
        } catch (Throwable t) {
            FearofSoundMod.LOGGER.warn("[AdvancedFear] Failed to rebuild overrides: {}", t.toString());
            AdvancedFearOverrideRegistry.clear();
        }

        VOICE_CHAT_ENABLED_CACHE = ModList.get().isLoaded("voicechat") && COMMON.enableVoiceChatIntegration.get();
        VOICE_CHAT_DETECTION_ENABLED_CACHE = VOICE_CHAT_ENABLED_CACHE;
        VOICE_CHAT_WHISPER_RANGE_CACHE = COMMON.voiceChatWhisperRange.get();
        VOICE_CHAT_NORMAL_RANGE_CACHE = COMMON.voiceChatNormalRange.get();
        VOICE_CHAT_WEIGHT_CACHE = COMMON.voiceChatWeight.get();

        TACZ_ENABLED_CACHE = ModList.get().isLoaded("tacz") && COMMON.enableTaczIntegration.get();
        TACZ_RELOAD_RANGE_CACHE = COMMON.taczReloadRange.get();
        TACZ_RELOAD_WEIGHT_CACHE = COMMON.taczReloadWeight.get();
        TACZ_SHOOT_RANGE_CACHE = COMMON.taczShootRange.get();
        TACZ_SHOOT_WEIGHT_CACHE = COMMON.taczShootWeight.get();

        SOUND_SWITCH_RATIO_CACHE = COMMON.soundSwitchRatio.get();
        PANIC_MIN_TICKS_CACHE = COMMON.panicMinTicks.get();
        PANIC_MAX_TICKS_CACHE = Math.max(COMMON.panicMaxTicks.get(), PANIC_MIN_TICKS_CACHE);
        ENABLE_HIDE_AND_FREEZE_CACHE = COMMON.enableHideAndFreeze.get();
        RECOVERY_QUIET_TICKS_CACHE = COMMON.recoveryQuietTicks.get();
        ARRIVAL_DISTANCE_CACHE = COMMON.arrivalDistance.get();
        SCENT_AWARENESS_ENABLED_CACHE = COMMON.enableScentAwareness.get();
        SCENT_AWARENESS_RADIUS_CACHE = COMMON.scentAwarenessRadius.get();
        SCENT_MAX_AGE_TICKS_CACHE = COMMON.scentMaxAgeTicks.get();
        LOS_RANGE_CACHE = COMMON.losRange.get();
        LOS_COOLDOWN_CACHE = COMMON.losCooldown.get();
        LOS_CHECK_INTERVAL_TICKS_CACHE = COMMON.losCheckIntervalTicks.get();
        LOS_PER_TICK_CAP_CACHE = COMMON.losPerTickCap.get();
        LOS_SCAN_RANGE_CAP_CACHE = COMMON.losScanRangeCap.get();

        MAX_SOUNDS_TRACKED_CACHE = COMMON.maxSoundsTracked.get();
        QUANTIFIED_INTEGRATION_ENABLED_CACHE = COMMON.enableQuantifiedIntegration.get();
        RAYCAST_CACHE_TTL_TICKS_CACHE = COMMON.raycastCacheTtlTicks.get();
        RAYCAST_CACHE_MAX_ENTRIES_CACHE = COMMON.raycastCacheMaxEntries.get();
        SPATIAL_GRID_SIZE_CACHE = COMMON.spatialGridSize.get();
        MERGE_RADIUS_BLOCKS_CACHE = COMMON.mergeRadius.get();

        WOOL_BLOCK_RANGE_REDUCTION_CACHE = COMMON.woolBlockRangeReduction.get();
        WOOL_BLOCK_WEIGHT_REDUCTION_CACHE = COMMON.woolBlockWeightReduction.get();
        SOLID_BLOCK_RANGE_REDUCTION_CACHE = COMMON.solidBlockRangeReduction.get();
        SOLID_BLOCK_WEIGHT_REDUCTION_CACHE = COMMON.solidBlockWeightReduction.get();
        NON_SOLID_BLOCK_RANGE_REDUCTION_CACHE = COMMON.nonSolidBlockRangeReduction.get();
        NON_SOLID_BLOCK_WEIGHT_REDUCTION_CACHE = COMMON.nonSolidBlockWeightReduction.get();
        THIN_BLOCK_RANGE_REDUCTION_CACHE = COMMON.thinBlockRangeReduction.get();
        THIN_BLOCK_WEIGHT_REDUCTION_CACHE = COMMON.thinBlockWeightReduction.get();

        CUSTOM_WOOL_BLOCKS_CACHE = new ArrayList<>(COMMON.customWoolBlocks.get());
        CUSTOM_SOLID_BLOCKS_CACHE = new ArrayList<>(COMMON.customSolidBlocks.get());
        CUSTOM_NON_SOLID_BLOCKS_CACHE = new ArrayList<>(COMMON.customNonSolidBlocks.get());
        CUSTOM_THIN_BLOCKS_CACHE = new ArrayList<>(COMMON.customThinBlocks.get());

        TACZ_GUN_SHOOT_DB_CACHE.clear();
        List<? extends String> rawShoot = COMMON.taczGunShootDecibels.get();
        for (String raw : rawShoot) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                double db = Double.parseDouble(parts[1]);
                if (rl != null && db >= 0) TACZ_GUN_SHOOT_DB_CACHE.put(rl, db);
            } catch (Exception e) {
            }
        }

        TACZ_ATTACHMENT_REDUCTION_DB_CACHE.clear();
        List<? extends String> rawAtt = COMMON.taczAttachmentReductions.get();
        for (String raw : rawAtt) {
            try {
                String[] parts = raw.split(";", 2);
                ResourceLocation rl = ResourceLocation.tryParse(parts[0]);
                double db = Double.parseDouble(parts[1]);
                if (rl != null) TACZ_ATTACHMENT_REDUCTION_DB_CACHE.put(rl, db);
            } catch (Exception e) {
            }
        }
        CONFIG_GENERATION++;
    }

}
