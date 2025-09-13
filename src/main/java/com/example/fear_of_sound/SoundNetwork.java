package com.example.fear_of_sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class SoundNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(FearofSoundMod.MOD_ID, "network"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    public static void init() {
        int id = 0;
        INSTANCE.messageBuilder(SoundMessage.class, id++)
                .encoder(SoundMessage::encode)
                .decoder(SoundMessage::decode)
                .consumerMainThread(SoundMessage::handle)
                .add();

        INSTANCE.messageBuilder(VoiceChatMessage.class, id++)
                .encoder(VoiceChatMessage::encode)
                .decoder(VoiceChatMessage::decode)
                .consumerMainThread(VoiceChatMessage::handle)
                .add();
    }
}