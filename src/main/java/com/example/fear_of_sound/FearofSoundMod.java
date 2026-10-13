package com.example.fear_of_sound;

import com.example.fear_of_sound.config.FearofSoundConfig;
import com.example.fear_of_sound.config.ConfigAutoUpdater;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(FearofSoundMod.MOD_ID)
public class FearofSoundMod {
    public static final String MOD_ID = "fear_of_sound";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static boolean isSoundAttractLoaded = false;

    public FearofSoundMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onModConfigEvent); 

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FearofSoundConfig.COMMON_SPEC, "fear_of_sound-common.toml");

        MinecraftForge.EVENT_BUS.register(ForgeEvents.class);

    }

    private void commonSetup(final FMLCommonSetupEvent event) {

        try {

            ConfigAutoUpdater.migrateIfNeeded();
            FearofSoundConfig.bakeConfig();
        } catch (Throwable t) {
            LOGGER.error("[FearOfSound] Failed to bake config during common setup.", t);
        }
        isSoundAttractLoaded = ModList.get().isLoaded("soundattract");
        SoundNetwork.init();
        
        event.enqueueWork(this::handleTaczIntegration);
        event.enqueueWork(() -> {
            try {
                com.example.fear_of_sound.quantified.QuantifiedIntegration.bootstrap();
            } catch (Throwable t) {
                LOGGER.error("[FearOfSound] Failed to bootstrap Quantified integration.", t);
            }
        });
    }

    private void handleTaczIntegration() {
        if (ModList.get().isLoaded("tacz") && FearofSoundConfig.TACZ_ENABLED_CACHE) {
            try {
                MinecraftForge.EVENT_BUS.register(com.example.fear_of_sound.integration.TaczIntegrationEvents.class);
            } catch (NoClassDefFoundError e) {
                LOGGER.error("Failed to register Tacz integration events. The Tacz API might be missing or has changed.", e);
            }
        }
    }

    private void onModConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == FearofSoundConfig.COMMON_SPEC) {
            FearofSoundConfig.bakeConfig();
            handleTaczIntegration();
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                SoundTracker.tick();
            }
        }
    }
}
