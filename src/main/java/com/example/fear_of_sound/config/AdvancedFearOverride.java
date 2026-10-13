package com.example.fear_of_sound.config;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class AdvancedFearOverride {
    public final String name;
    public final Set<EntityType<?>> entityTypes;
    public final boolean entityWildcard;
    public final List<CompoundTag> nbtMatchers;
    public final List<Predicate<Mob>> predicates;
    public final Set<ResourceLocation> soundIds;
    public final boolean soundWildcard;

    public AdvancedFearOverride(String name,
                                Set<EntityType<?>> entityTypes,
                                boolean entityWildcard,
                                List<CompoundTag> nbtMatchers,
                                List<Predicate<Mob>> predicates,
                                Set<ResourceLocation> soundIds,
                                boolean soundWildcard) {
        this.name = name;
        this.entityTypes = entityTypes;
        this.entityWildcard = entityWildcard;
        this.nbtMatchers = nbtMatchers;
        this.predicates = predicates;
        this.soundIds = soundIds;
        this.soundWildcard = soundWildcard;
    }
}
