package com.example.fear_of_sound.quantified;

import com.example.fear_of_sound.FearofSoundMod;
import com.example.fear_of_sound.config.FearofSoundConfig;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public final class QuantifiedIntegration {
    private static final AtomicBoolean INIT = new AtomicBoolean(false);

    private QuantifiedIntegration() {
    }

    public static void bootstrap() {
        if (!ModList.get().isLoaded("quantified") || !FearofSoundConfig.QUANTIFIED_INTEGRATION_ENABLED_CACHE
                || !INIT.compareAndSet(false, true)) {
            return;
        }
        try {
            Class<?> api = Class.forName("org.admany.quantified.api.QuantifiedAPI");
            Method register;
            Object[] args;
            try {
                register = api.getMethod("registerV2", String.class, String.class, String.class);
                args = new Object[]{FearofSoundMod.MOD_ID, "Fear of Sound", "3.0.0"};
            } catch (NoSuchMethodException missingNamedV2) {
                try {
                    register = api.getMethod("registerV2", String.class);
                } catch (NoSuchMethodException missingV2) {
                    register = api.getMethod("register", String.class);
                }
                args = new Object[]{FearofSoundMod.MOD_ID};
            }
            Object result = register.invoke(null, args);
            if (result instanceof Boolean accepted && !accepted) {
                FearofSoundMod.LOGGER.warn("Quantified declined Fear of Sound registration; local Caffeine caches remain available.");
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            FearofSoundMod.LOGGER.warn("Quantified integration is unavailable; continuing with local Caffeine caches: {}", e.toString());
        } catch (RuntimeException e) {
            FearofSoundMod.LOGGER.warn("Quantified integration failed safely; continuing with local Caffeine caches: {}", e.toString());
        }
    }
}
