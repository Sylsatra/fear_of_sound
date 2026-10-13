package com.example.fear_of_sound.integration;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLEnvironment;

@ForgeVoicechatPlugin
public class VoiceChatIntegration implements VoicechatPlugin {

    private static final String VOICE_CHAT_PLUGIN_ID = "fear_of_sound_vc_integration";

    @Override
    public String getPluginId() {
        return VOICE_CHAT_PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registration.registerEvent(ClientSoundEvent.class,
                    event -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> VoiceChatClientSoundHandler.onClientSound(event)));
        }
    }
}
