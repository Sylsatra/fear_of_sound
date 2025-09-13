package com.example.fear_of_sound.integration;

import com.example.fear_of_sound.VoiceChatMessage;
import com.example.fear_of_sound.config.FearofSoundConfig;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.example.fear_of_sound.SoundNetwork;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

@ForgeVoicechatPlugin
public class VoiceChatIntegration implements VoicechatPlugin {

    private static final String VOICE_CHAT_PLUGIN_ID = "fear_of_sound_vc_integration";

    @Override
    public String getPluginId() {
        return VOICE_CHAT_PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientSoundEvent.class, this::onClientSound);
    }

    public void onClientSound(ClientSoundEvent event) {
        Player clientPlayer = Minecraft.getInstance().player;
        Level clientWorld = Minecraft.getInstance().level;

        if (clientPlayer == null || clientWorld == null || !FearofSoundConfig.VOICE_CHAT_DETECTION_ENABLED_CACHE) {
            return;
        }

        short[] rawAudio = event.getRawAudio();
        if (rawAudio == null || rawAudio.length == 0) {
            return;
        }

        double db = computePeakDb(rawAudio);
        double normDb = db - (-127.0);

        double factor = 0.0;
        List<? extends String> rawMap = FearofSoundConfig.COMMON.voiceChatDbThresholdMap.get();
        if (rawMap != null && !rawMap.isEmpty()) {
            List<double[]> pairs = new ArrayList<>();
            for (String entry : rawMap) {
                if (entry == null || entry.isEmpty()) continue;
                String[] parts = entry.split(":", 2);
                if (parts.length != 2) continue;
                try {
                    double th = Double.parseDouble(parts[0].trim());
                    double mul = Double.parseDouble(parts[1].trim());
                    pairs.add(new double[]{th, mul});
                } catch (Exception ignored) {}
            }
            Collections.sort(pairs, new Comparator<double[]>() {
                @Override
                public int compare(double[] a, double[] b) {
                    return Double.compare(b[0], a[0]);
                }
            });
            for (double[] p : pairs) {
                if (normDb >= p[0]) {
                    factor = p[1];
                    break;
                }
            }
        } else {
            if (normDb >= 50.0) {
                factor = 1.0;
            } else if (normDb >= 30.0) {
                factor = 0.7;
            } else if (normDb >= 10.0) {
                factor = 0.3;
            } else {
                factor = 0.0;
            }
        }

        boolean isWhispering = event.isWhispering();
        int baseRange = isWhispering
                ? FearofSoundConfig.COMMON.voiceChatWhisperRange.get()
                : FearofSoundConfig.COMMON.voiceChatNormalRange.get();

        int effectiveRange = (int) Math.round(baseRange * factor);
        if (effectiveRange <= 0) {
            return;
        }

        double weight = FearofSoundConfig.COMMON.voiceChatWeight.get();

        double x = clientPlayer.getX();
        double y = clientPlayer.getY();
        double z = clientPlayer.getZ();
        ResourceLocation dim = clientWorld.dimension().location();
        Optional<UUID> sourcePlayerUUID = Optional.of(clientPlayer.getUUID());

        VoiceChatMessage msg = new VoiceChatMessage(x, y, z, dim, sourcePlayerUUID, effectiveRange, weight);
        SoundNetwork.INSTANCE.sendToServer(msg);
    }

    private static double computePeakDb(short[] samples) {
        short highest = 0;
        for (short s : samples) {
            int a = Math.abs(s);
            if (a > highest) highest = (short) a;
        }
        if (highest == 0) return -127.0;
        double norm = Math.abs(highest) / 32768.0;
        double db = 20.0 * Math.log10(norm);
        if (!Double.isFinite(db)) return -127.0;
        if (db > 0.0) db = 0.0;
        if (db < -127.0) db = -127.0;
        return db;
    }
}