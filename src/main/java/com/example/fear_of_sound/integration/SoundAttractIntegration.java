package com.example.fear_of_sound.integration;

import com.example.fear_of_sound.SoundTracker.SoundRecord;
import com.example.fear_of_sound.config.AdvancedFearOverrideRegistry;
import com.example.fear_of_sound.config.FearofSoundConfig;
import com.example.fear_of_sound.config.FearofSoundConfig.SoundConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class SoundAttractIntegration {
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static volatile SendBinding sendBinding;
    private static volatile boolean sendBindingAttempted;

    private record SendBinding(Object network, Constructor<?> actionMessage, Constructor<?> defaultMessage,
                               Constructor<?> legacyMessage, Constructor<?> extendedLegacyMessage,
                               Method sendToServer) {
    }

    public static boolean sendSound(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension,
                                    Optional<UUID> sourcePlayerUUID, int range, double weight, String playerAction) {
        try {
            SendBinding binding = resolveSendBinding();
            Object msg;
            if (playerAction != null && binding.actionMessage() != null) {
                msg = binding.actionMessage().newInstance(soundId, x, y, z, dimension, sourcePlayerUUID,
                        playerAction, 1.0F, false, null, null, null);
            } else if (playerAction != null && binding.legacyMessage() != null) {
                msg = binding.legacyMessage().newInstance(soundId, x, y, z, dimension, sourcePlayerUUID, range, weight);
            } else if (playerAction != null && binding.extendedLegacyMessage() != null) {
                msg = binding.extendedLegacyMessage().newInstance(
                        soundId, x, y, z, dimension, sourcePlayerUUID, range, weight, null, null, null);
            } else if (binding.defaultMessage() != null) {
                msg = binding.defaultMessage().newInstance(soundId, x, y, z, dimension, sourcePlayerUUID);
            } else if (binding.legacyMessage() != null) {
                msg = binding.legacyMessage().newInstance(soundId, x, y, z, dimension, sourcePlayerUUID, range, weight);
            } else if (binding.extendedLegacyMessage() != null) {
                msg = binding.extendedLegacyMessage().newInstance(
                        soundId, x, y, z, dimension, sourcePlayerUUID, range, weight, null, null, null);
            } else if (binding.actionMessage() != null) {
                msg = binding.actionMessage().newInstance(soundId, x, y, z, dimension, sourcePlayerUUID,
                        null, 1.0F, false, null, null, null);
            } else {
                throw new NoSuchMethodException("No supported Sound Attract SoundMessage constructor was found");
            }
            binding.sendToServer().invoke(binding.network(), msg);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warnOnce(e);
            return false;
        }
    }

    private static SendBinding resolveSendBinding() throws ReflectiveOperationException {
        if (sendBinding != null) return sendBinding;
        synchronized (SoundAttractIntegration.class) {
            if (sendBinding != null) return sendBinding;
            if (sendBindingAttempted) throw new NoSuchMethodException("Sound Attract bindings were unavailable");
            ClassLoader loader = SoundAttractIntegration.class.getClassLoader();
            Class<?> networkClass = Class.forName("com.example.soundattract.network.SoundAttractNetwork", false, loader);
            Object network = networkClass.getField("INSTANCE").get(null);
            if (network == null) throw new NoSuchMethodException("Sound Attract network channel is not initialized");
            sendBindingAttempted = true;
            Class<?> messageClass = Class.forName("com.example.soundattract.network.SoundMessage", false, loader);
            Constructor<?> actionMessage = findConstructor(messageClass, ResourceLocation.class, double.class,
                    double.class, double.class, ResourceLocation.class, Optional.class, String.class, float.class,
                    boolean.class, String.class, String.class, String.class);
            Constructor<?> defaultMessage = findConstructor(messageClass, ResourceLocation.class, double.class,
                    double.class, double.class, ResourceLocation.class, Optional.class);
            Constructor<?> legacyMessage = findConstructor(messageClass, ResourceLocation.class, double.class,
                    double.class, double.class, ResourceLocation.class, Optional.class, int.class, double.class);
            Constructor<?> extendedLegacyMessage = findConstructor(messageClass, ResourceLocation.class, double.class,
                    double.class, double.class, ResourceLocation.class, Optional.class, int.class, double.class,
                    String.class, String.class, String.class);
            Method sendToServer = findMethod(network.getClass(), "sendToServer", Object.class);
            if (sendToServer == null) sendToServer = findMethod(network.getClass(), "sendToServer", messageClass);
            if (sendToServer == null) throw new NoSuchMethodException("Sound Attract network has no sendToServer method");
            if (actionMessage == null && defaultMessage == null && legacyMessage == null && extendedLegacyMessage == null) {
                throw new NoSuchMethodException("Sound Attract has no supported SoundMessage constructor");
            }
            SendBinding resolved = new SendBinding(network, actionMessage, defaultMessage, legacyMessage,
                    extendedLegacyMessage, sendToServer);
            sendBinding = resolved;
            return resolved;
        }
    }

    private static Constructor<?> findConstructor(Class<?> type, Class<?>... parameterTypes) {
        try {
            return type.getConstructor(parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    public static boolean addSoundDirect(SoundEvent sound, BlockPos pos, String dimensionKey, double range, double weight, int lifetime) {
        if (pos == null || dimensionKey == null || !Double.isFinite(range) || !Double.isFinite(weight)
                || range <= 0.0 || weight <= 0.0 || lifetime <= 0) return false;
        try {
            Class<?> trackerClass = Class.forName("com.example.soundattract.tracking.SoundTracker");
            try {
                trackerClass.getMethod(
                        "addSound",
                        SoundEvent.class,
                        BlockPos.class,
                        String.class,
                        double.class,
                        double.class,
                        int.class
                ).invoke(null, sound, pos, dimensionKey, range, weight, lifetime);
                return true;
            } catch (NoSuchMethodException ignored) {
                trackerClass.getMethod(
                        "addSound",
                        SoundEvent.class,
                        BlockPos.class,
                        String.class
                ).invoke(null, sound, pos, dimensionKey);
                return true;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warnOnce(e);
            return false;
        }
    }

    public static SoundRecord findNearestSound(Mob mob) {
        try {
            Class<?> trackerClass = Class.forName("com.example.soundattract.tracking.SoundTracker");
            Level level = mob.level();
            if (level == null || level.isClientSide || !mob.isAlive()) return null;
            BlockPos mobPos = mob.blockPosition();
            Vec3 mobEyePos = mob.getEyePosition();

            Object attractSound = trackerClass.getMethod("findNearestSound", 
                    Mob.class, Level.class, BlockPos.class, Vec3.class)
                    .invoke(null, mob, level, mobPos, mobEyePos);

            if (attractSound == null) {
                return null;
            }

            Class<?> soundRecordClass = Class.forName("com.example.soundattract.tracking.SoundTracker$SoundRecord");
            SoundEvent soundEventKey = (SoundEvent) soundRecordClass.getField("sound").get(attractSound);
            String soundId;
            try {
                soundId = (String) soundRecordClass.getField("soundId").get(attractSound);
            } catch (NoSuchFieldException oldApi) {
                soundId = soundEventKey == null ? null : soundEventKey.getLocation().toString();
            }
            double finalRange = (Double) soundRecordClass.getField("range").get(attractSound);
            double finalWeight = (Double) soundRecordClass.getField("weight").get(attractSound);

            if (!Double.isFinite(finalRange) || !Double.isFinite(finalWeight) || finalRange <= 0 || finalWeight <= 0) {
                if (soundEventKey != null && FearofSoundConfig.SOUND_CONFIGS_CACHE.containsKey(soundEventKey)) {
                    SoundConfig fearConfig = FearofSoundConfig.SOUND_CONFIGS_CACHE.get(soundEventKey);
                    if (fearConfig != null) {
                        finalRange = fearConfig.range;
                        finalWeight = fearConfig.weight;
                    }
                }
            }

            if (!Double.isFinite(finalRange) || !Double.isFinite(finalWeight) || finalRange <= 0 || finalWeight <= 0) {
                return null;
            }

            ResourceLocation sid = ResourceLocation.tryParse(soundId);
            if (sid == null && soundEventKey != null) sid = ForgeRegistries.SOUND_EVENTS.getKey(soundEventKey);
            if (!AdvancedFearOverrideRegistry.shouldFear(mob, sid)) {
                return null;
            }
            
            Object posObj = soundRecordClass.getField("pos").get(attractSound);
            BlockPos pos;
            if (posObj instanceof BlockPos bp) {
                pos = bp;
            } else if (posObj instanceof Vec3 v) {
                pos = BlockPos.containing(v);
            } else {
                return null;
            }
            int ticksRemaining = (Integer) soundRecordClass.getField("ticksRemaining").get(attractSound);
            String dimensionKey = (String) soundRecordClass.getField("dimensionKey").get(attractSound);
            if (dimensionKey == null || !dimensionKey.equals(level.dimension().location().toString())) return null;
            if (ticksRemaining <= 0 || ticksRemaining > 20 * 60 * 10) return null;
            
            SoundRecord result = new SoundRecord(
                    soundEventKey,
                    soundId,
                    pos,
                    ticksRemaining,
                    dimensionKey,
                    finalRange,
                    finalWeight
            );
            return result;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warnOnce(e);
            return null;
        }
    }

    private static void warnOnce(Throwable error) {
        if (WARNED.compareAndSet(false, true)) {
            com.example.fear_of_sound.FearofSoundMod.LOGGER.warn(
                    "Sound Attract adapter unavailable; using Fear of Sound's standalone sound tracker: {}", error.toString());
        }
    }

}
