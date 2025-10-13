package com.example.fear_of_sound.config;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class NbtMatchers {
    private NbtMatchers() {}

    public static boolean partialMatch(CompoundTag mobNbt, CompoundTag matcherNbt) {
        if (mobNbt == null || matcherNbt == null) return false;
        for (String key : matcherNbt.getAllKeys()) {
            if (!mobNbt.contains(key, matcherNbt.getTagType(key))) {
                return false;
            }
            byte type = matcherNbt.getTagType(key);
            if (type == Tag.TAG_COMPOUND) {
                if (!partialMatch(mobNbt.getCompound(key), matcherNbt.getCompound(key))) {
                    return false;
                }
            } else if (type == Tag.TAG_LIST) {
                // Basic list equality check as in Sound Attract's implementation
                if (!mobNbt.getList(key, matcherNbt.getList(key, 0).getElementType())
                        .equals(matcherNbt.getList(key, 0))) {
                    // Do not fail hard; treat as non-match to be explicit
                    return false;
                }
            } else {
                if (!mobNbt.get(key).equals(matcherNbt.get(key))) {
                    return false;
                }
            }
        }
        return true;
    }
}
