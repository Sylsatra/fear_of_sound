package com.example.fear_of_sound.quantified;

import com.example.fear_of_sound.FearofSoundMod;
import com.example.fear_of_sound.config.FearofSoundConfig;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.function.Supplier;

public final class QuantifiedCacheCompat {
    private static volatile Api api;
    private static volatile boolean resolutionAttempted;

    private QuantifiedCacheCompat() {
    }

    public static boolean isUsable() {
        if (!ModList.get().isLoaded("quantified") || !FearofSoundConfig.QUANTIFIED_INTEGRATION_ENABLED_CACHE) return false;
        QuantifiedIntegration.bootstrap();
        Api resolved = resolve();
        if (resolved == null) return false;
        try {
            Object manager = resolved.managerMethod.getParameterCount() == 0
                    ? resolved.managerMethod.invoke(null)
                    : resolved.managerMethod.invoke(null, FearofSoundMod.MOD_ID);
            return manager == null || !Boolean.TRUE.equals(resolved.memoryPressureMethod.invoke(manager));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    public static <T> T getCached(String cacheName, String key, Supplier<T> loader,
                                  Duration ttl, long maxSize, boolean persistence) {
        if (loader == null) return null;
        if (!isUsable()) return loader.get();
        Api resolved = resolve();
        try {
            if (resolved.cacheMethod != null && resolved.cacheGetMethod != null) {
                Object request = resolved.cacheMethod.invoke(null, FearofSoundMod.MOD_ID, cacheName);
                request = chain(resolved.cacheTtlMethod, request, ttl);
                request = chain(resolved.cacheMaxEntriesMethod, request, maxSize);
                Method mode = persistence ? resolved.persistentMethod : resolved.memoryOnlyMethod;
                request = chain(mode, request);
                @SuppressWarnings("unchecked")
                T value = (T) resolved.cacheGetMethod.invoke(request, key, loader);
                if (value != null) return value;
            }
            if (resolved.legacyGetCachedMethod != null) {
                @SuppressWarnings("unchecked")
                T value = (T) resolved.legacyGetCachedMethod.invoke(null, cacheName, key, loader, ttl, maxSize, persistence);
                if (value != null) return value;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
        }
        return loader.get();
    }

    private static Object chain(Method method, Object target, Object... args) throws ReflectiveOperationException {
        if (method == null || target == null) return target;
        Object result = method.invoke(target, args);
        return result == null ? target : result;
    }

    private static Api resolve() {
        Api current = api;
        if (current != null || resolutionAttempted) return current;
        synchronized (QuantifiedCacheCompat.class) {
            if (api != null || resolutionAttempted) return api;
            resolutionAttempted = true;
            try {
                Class<?> apiClass = Class.forName("org.admany.quantified.api.QuantifiedAPI");
                Class<?> managerClass = Class.forName("org.admany.quantified.api.interfaces.ModCacheManager");
                Method manager;
                try {
                    manager = apiClass.getMethod("getCacheManager", String.class);
                } catch (NoSuchMethodException oldApi) {
                    manager = apiClass.getMethod("getCacheManager");
                }
                Method pressure = managerClass.getMethod("isMemoryPressureHigh");
                Method cache = null;
                Method get = null;
                Method ttl = null;
                Method maxEntries = null;
                Method persistent = null;
                Method memoryOnly = null;
                Method legacy = null;
                try {
                    cache = apiClass.getMethod("cache", String.class, String.class);
                    Class<?> requestClass = cache.getReturnType();
                    ttl = requestClass.getMethod("ttl", Duration.class);
                    maxEntries = requestClass.getMethod("maxEntries", long.class);
                    persistent = requestClass.getMethod("persistent");
                    try {
                        memoryOnly = requestClass.getMethod("memoryOnly");
                    } catch (NoSuchMethodException noMemoryOnly) {
                        memoryOnly = requestClass.getMethod("ephemeral");
                    }
                    get = requestClass.getMethod("get", String.class, Supplier.class);
                } catch (ReflectiveOperationException oldCacheApi) {
                    try {
                        legacy = apiClass.getMethod("getCached", String.class, String.class,
                                Supplier.class, Duration.class, long.class, boolean.class);
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                api = new Api(manager, pressure, cache, get, ttl, maxEntries, persistent, memoryOnly, legacy);
            } catch (ReflectiveOperationException | LinkageError e) {
            }
            return api;
        }
    }

    private record Api(Method managerMethod, Method memoryPressureMethod, Method cacheMethod,
                       Method cacheGetMethod, Method cacheTtlMethod, Method cacheMaxEntriesMethod,
                       Method persistentMethod, Method memoryOnlyMethod, Method legacyGetCachedMethod) {
    }
}
