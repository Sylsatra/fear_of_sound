package com.example.fear_of_sound.config;

import com.example.fear_of_sound.FearofSoundMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

public final class AdvancedFearOverrideRegistry {
    private AdvancedFearOverrideRegistry() {}

    private static final Map<EntityType<?>, List<AdvancedFearOverride>> BY_TYPE = new HashMap<>();
    private static final List<AdvancedFearOverride> WILDCARD = new ArrayList<>();

    public static void clear() {
        BY_TYPE.clear();
        WILDCARD.clear();
    }

    public static void rebuild(List<? extends String> raw) {
        clear();
        if (raw == null || raw.isEmpty()) return;

        int ok = 0, bad = 0;
        for (String line : raw) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            try {
                AdvancedFearOverride rule = parseLine(trimmed);
                if (rule == null) { bad++; continue; }
                if (rule.entityWildcard) {
                    WILDCARD.add(rule);
                } else {
                    for (EntityType<?> et : rule.entityTypes) {
                        BY_TYPE.computeIfAbsent(et, k -> new ArrayList<>()).add(rule);
                    }
                }
                ok++;
            } catch (Exception e) {
                bad++;
                FearofSoundMod.LOGGER.warn("[AdvancedFear] Failed to parse '{}': {}", trimmed, e.toString());
            }
        }
        if (ok > 0 || bad > 0) {
            FearofSoundMod.LOGGER.info("[AdvancedFear] Loaded {} advanced overrides ({} malformed).", ok, bad);
        }
    }

    @Nullable
    private static AdvancedFearOverride parseLine(String line) {
        // name;entities;nbt_or_predicates;sounds
        String[] parts = line.split(";", 4);
        if (parts.length != 4) {
            FearofSoundMod.LOGGER.warn("[AdvancedFear] Malformed entry (need 4 ';' sections): {}", line);
            return null;
        }
        String name = parts[0].trim();
        String entitiesPart = parts[1].trim();
        String conditionsPart = parts[2].trim();
        String soundsPart = parts[3].trim();

        boolean entityWildcard = entitiesPart.equals("*");
        Set<EntityType<?>> entityTypes = new HashSet<>();
        if (!entityWildcard) {
            List<String> tokens = TopLevelCommaSplitter.split(entitiesPart);
            for (String t : tokens) {
                ResourceLocation rl = ResourceLocation.tryParse(t.trim());
                if (rl == null) { FearofSoundMod.LOGGER.warn("[AdvancedFear] Invalid entity id '{}' in '{}'.", t, line); continue; }
                EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(rl);
                if (et == null) { FearofSoundMod.LOGGER.warn("[AdvancedFear] Unknown entity '{}' in '{}'.", rl, line); continue; }
                entityTypes.add(et);
            }
            if (entityTypes.isEmpty()) {
                FearofSoundMod.LOGGER.warn("[AdvancedFear] No valid entities; rule will be ignored: {}", line);
                return null;
            }
        }

        List<CompoundTag> nbtMatchers = new ArrayList<>();
        List<Predicate<Mob>> predicates = new ArrayList<>();
        if (!conditionsPart.isEmpty() && !conditionsPart.equals("-")) {
            List<String> tokens = TopLevelCommaSplitter.split(conditionsPart);
            for (String token : tokens) {
                String t = token.trim();
                Predicate<Mob> pred = parseComparatorPredicate(t);
                if (pred != null) { predicates.add(pred); continue; }
                try {
                    CompoundTag tag = TagParser.parseTag(t);
                    nbtMatchers.add(tag);
                } catch (Exception e) {
                    FearofSoundMod.LOGGER.warn("[AdvancedFear] Bad condition token '{}' in '{}': {}", t, line, e.getMessage());
                }
            }
        }

        boolean soundWildcard = soundsPart.equals("*");
        Set<ResourceLocation> soundIds = new HashSet<>();
        if (!soundWildcard) {
            List<String> tokens = TopLevelCommaSplitter.split(soundsPart);
            for (String t : tokens) {
                ResourceLocation rl = ResourceLocation.tryParse(t.trim());
                if (rl == null) { FearofSoundMod.LOGGER.warn("[AdvancedFear] Invalid sound id '{}' in '{}'.", t, line); continue; }
                soundIds.add(rl);
            }
            if (soundIds.isEmpty()) {
                FearofSoundMod.LOGGER.warn("[AdvancedFear] No valid sounds; rule will be ignored: {}", line);
                return null;
            }
        }

        return new AdvancedFearOverride(name, entityTypes, entityWildcard, nbtMatchers, predicates, soundIds, soundWildcard);
    }

    private static Predicate<Mob> parseComparatorPredicate(String t) {
        String s = t.replace(" ", "");
        String[] ops = new String[]{"<=",">=","<",">"};
        String key = null; String op = null; String rhs = null;
        for (String candidate : ops) {
            int idx = s.indexOf(candidate);
            if (idx > 0) {
                key = s.substring(0, idx);
                op = candidate;
                rhs = s.substring(idx + candidate.length());
                break;
            }
        }
        if (key == null || op == null || rhs == null) return null;
        try {
            if ("health".equalsIgnoreCase(key)) {
                double val = Double.parseDouble(rhs);
                switch (op) {
                    case "<=": return mob -> mob.getHealth() <= val;
                    case ">=": return mob -> mob.getHealth() >= val;
                    case "<": return mob -> mob.getHealth() < val;
                    case ">": return mob -> mob.getHealth() > val;
                }
            } else if ("health_pct".equalsIgnoreCase(key)) {
                double val = Double.parseDouble(rhs);
                final double valCaptured = val;
                final String opCaptured = op;
                return mob -> {
                    float max = mob.getMaxHealth();
                    if (max <= 0) return false;
                    double pct = mob.getHealth() / max;
                    return switch (opCaptured) {
                        case "<=" -> pct <= valCaptured;
                        case ">=" -> pct >= valCaptured;
                        case "<" -> pct < valCaptured;
                        case ">" -> pct > valCaptured;
                        default -> false;
                    };
                };
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    public static boolean shouldFear(Mob mob, @Nullable ResourceLocation soundId) {
        // No rules configured -> allow by default
        if (BY_TYPE.isEmpty() && WILDCARD.isEmpty()) return true;
        List<AdvancedFearOverride> rules = new ArrayList<>(WILDCARD);
        List<AdvancedFearOverride> specific = BY_TYPE.get(mob.getType());
        if (specific != null) rules.addAll(specific);
        if (rules.isEmpty()) return true;

        CompoundTag mobNbt = null;
        for (AdvancedFearOverride r : rules) {
            // sound filter
            if (!r.soundWildcard) {
                if (soundId == null || !r.soundIds.contains(soundId)) continue;
            }
            // conditions
            boolean conditionsPass;
            if ((r.nbtMatchers == null || r.nbtMatchers.isEmpty()) && (r.predicates == null || r.predicates.isEmpty())) {
                conditionsPass = true;
            } else {
                boolean any = false;
                if (r.predicates != null) {
                    for (Predicate<Mob> p : r.predicates) { if (p.test(mob)) { any = true; break; } }
                }
                if (!any && r.nbtMatchers != null && !r.nbtMatchers.isEmpty()) {
                    if (mobNbt == null) mobNbt = mob.saveWithoutId(new CompoundTag());
                    for (CompoundTag matcher : r.nbtMatchers) { if (NbtMatchers.partialMatch(mobNbt, matcher)) { any = true; break; } }
                }
                conditionsPass = any;
            }
            if (conditionsPass) return true; // any matching rule allows fear
        }
        return false;
    }

    public static boolean hasAnyRuleFor(EntityType<?> type) {
        if (type == null) return !WILDCARD.isEmpty();
        return !WILDCARD.isEmpty() || BY_TYPE.containsKey(type);
    }
}
