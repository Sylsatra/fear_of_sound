package com.example.fear_of_sound;

import com.example.fear_of_sound.config.FearofSoundConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class VoiceChatMessage {
    private final double x;
    private final double y;
    private final double z;
    private final ResourceLocation dimension;
    private final Optional<UUID> sourcePlayerUUID;
    private final int range;
    private final double weight;

    public VoiceChatMessage(double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID, int range, double weight) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.sourcePlayerUUID = sourcePlayerUUID;
        this.range = range;
        this.weight = weight;
    }

    public static void encode(VoiceChatMessage msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeResourceLocation(msg.dimension);
        buf.writeOptional(msg.sourcePlayerUUID, FriendlyByteBuf::writeUUID);
        buf.writeInt(msg.range);
        buf.writeDouble(msg.weight);
    }

    public static VoiceChatMessage decode(FriendlyByteBuf buf) {
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        ResourceLocation dimension = buf.readResourceLocation();
        Optional<UUID> sourcePlayerUUID = buf.readOptional(FriendlyByteBuf::readUUID);
        int range = buf.readInt();
        double weight = buf.readDouble();
        return new VoiceChatMessage(x, y, z, dimension, sourcePlayerUUID, range, weight);
    }

    public static void handle(VoiceChatMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            ServerLevel serverLevel = null;

            if (sender != null) {
                serverLevel = sender.server.getLevel(sender.level().dimension());
            } else if (ctx.get().getDirection().getReceptionSide().isServer()) {
                net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, msg.dimension);
                    serverLevel = server.getLevel(levelKey);
                }
            }

            if (serverLevel == null || !serverLevel.dimension().location().equals(msg.dimension)) {
                return;
            }

            BlockPos pos = BlockPos.containing(msg.x, msg.y, msg.z);
            String dimString = msg.dimension.toString();
            int lifetime = FearofSoundConfig.COMMON.soundLifetimeTicks.get();

            if (msg.range > 0) {
                SoundTracker.addSound(null, pos, dimString, msg.range, msg.weight, lifetime);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}