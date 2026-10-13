package com.example.fear_of_sound.integration;

import com.example.fear_of_sound.SoundNetwork;
import com.example.fear_of_sound.VoiceChatMessage;
import com.example.fear_of_sound.config.FearofSoundConfig;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
final class VoiceChatClientSoundHandler {
    private VoiceChatClientSoundHandler() {
    }

    static void onClientSound(ClientSoundEvent event) {
        Player clientPlayer = Minecraft.getInstance().player;
        Level clientWorld = Minecraft.getInstance().level;
        if (clientPlayer == null || clientWorld == null || !FearofSoundConfig.VOICE_CHAT_DETECTION_ENABLED_CACHE) return;

        short[] rawAudio = event.getRawAudio();
        if (rawAudio == null || rawAudio.length == 0) return;

        double normDb = computePeakDb(rawAudio) + 127.0;
        double factor = 0.0;
        List<? extends String> rawMap = FearofSoundConfig.COMMON.voiceChatDbThresholdMap.get();
        if (rawMap != null && !rawMap.isEmpty()) {
            List<double[]> pairs = new ArrayList<>();
            for (String entry : rawMap) {
                if (entry == null || entry.isEmpty()) continue;
                String[] parts = entry.split(":", 2);
                if (parts.length != 2) continue;
                try {
                    double threshold = Double.parseDouble(parts[0].trim());
                    double multiplier = Double.parseDouble(parts[1].trim());
                    pairs.add(new double[]{threshold, multiplier});
                } catch (NumberFormatException ignored) {
                }
            }
            pairs.sort(Comparator.comparingDouble((double[] pair) -> pair[0]).reversed());
            for (double[] pair : pairs) {
                if (normDb >= pair[0]) {
                    factor = pair[1];
                    break;
                }
            }
        } else if (normDb >= 50.0) {
            factor = 1.0;
        } else if (normDb >= 30.0) {
            factor = 0.7;
        } else if (normDb >= 10.0) {
            factor = 0.3;
        }

        int baseRange = event.isWhispering()
                ? FearofSoundConfig.COMMON.voiceChatWhisperRange.get()
                : FearofSoundConfig.COMMON.voiceChatNormalRange.get();
        int effectiveRange = (int) Math.round(baseRange * factor);
        if (effectiveRange <= 0) return;

        ResourceLocation dimension = clientWorld.dimension().location();
        VoiceChatMessage message = new VoiceChatMessage(clientPlayer.getX(), clientPlayer.getY(), clientPlayer.getZ(),
                dimension, Optional.of(clientPlayer.getUUID()), effectiveRange,
                FearofSoundConfig.COMMON.voiceChatWeight.get());
        SoundNetwork.INSTANCE.sendToServer(message);
    }

    private static double computePeakDb(short[] samples) {
        int highest = 0;
        for (short sample : samples) {
            highest = Math.max(highest, Math.abs((int) sample));
        }
        if (highest == 0) return -127.0;
        double db = 20.0 * Math.log10(highest / 32768.0);
        return Math.max(-127.0, Math.min(0.0, db));
    }
}
