package com.ruskserver.iem_compatibility.client.audio;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class AudioCommands {
    private static final String[] LABELS = {"正面", "右", "背後", "左", "上", "下"};
    private static Vec3[] testPositions;
    private static int testStep, delay;
    private static String testKind;
    private static Object testWorld;
    private AudioCommands() {}
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> { AcousticScene.tick(mc); testTick(mc); });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            var root = ClientCommands.literal("iemaudio").executes(ctx -> { status(); return 1; });
            root.then(ClientCommands.literal("status").executes(ctx -> { status(); return 1; }));
            for (boolean enabled : new boolean[]{true, false}) {
                root.then(ClientCommands.literal(enabled ? "on" : "off").executes(ctx -> {
                    AudioSettings.INSTANCE.enabled = enabled; reload();
                    say("IEM音響 " + (enabled ? "ON" : "OFF（Minecraftの指向性オーディオ設定に戻します）")); return 1;
                }));
                root.then(ClientCommands.literal("occlusion").then(ClientCommands.literal(enabled ? "on" : "off").executes(ctx -> {
                    AudioSettings.INSTANCE.occlusion = enabled; reload();
                    say("壁・床の遮蔽 " + (enabled ? "ON" : "OFF")); return 1;
                })));
            }
            root.then(ClientCommands.literal("hrtf").executes(ctx -> {
                var names = SpatialAudio.device.profiles();
                for (int i = 0; i < names.size(); i++) say(i + ": " + names.get(i));
                say("/iemaudio hrtf <番号> または default"); return 1;
            }).then(ClientCommands.literal("default").executes(ctx -> {
                AudioSettings.INSTANCE.hrtf = ""; reload(); say("HRTFを既定に戻しました"); return 1;
            })).then(ClientCommands.argument("id", IntegerArgumentType.integer(0)).executes(ctx -> {
                int id = IntegerArgumentType.getInteger(ctx, "id"); var names = SpatialAudio.device.profiles();
                if (id >= names.size()) { say("その番号のHRTFはありません"); return 0; }
                AudioSettings.INSTANCE.hrtf = names.get(id); reload(); say("HRTF: " + names.get(id)); return 1;
            })));
            var tests = ClientCommands.literal("test");
            for (String kind : new String[]{"mono", "stereo"}) tests.then(ClientCommands.literal(kind).executes(ctx -> startTest(kind)));
            tests.then(ClientCommands.literal("stop").executes(ctx -> { stopTest(Minecraft.getInstance()); return 1; }));
            root.then(tests);
            dispatcher.register(root);
        });
    }
    public static void reload() {
        stopTest(Minecraft.getInstance());
        AudioSettings.INSTANCE.save();
        Minecraft.getInstance().getSoundManager().reload();
    }
    private static void status() {
        var d = SpatialAudio.device;
        say("IEM音響 " + (AudioSettings.INSTANCE.enabled ? "ON" : "OFF") + " | 遮蔽 " + AudioSettings.INSTANCE.occlusion);
        say("OpenAL: " + d.renderer() + " | HRTF: " + d.hrtf() + " | 状態: " + switch(d.status()) {
            case 0 -> "無効"; case 1 -> "有効"; case 2 -> "要求拒否"; case 3 -> "必須"; case 4 -> "ヘッドホン自動検出"; case 5 -> "出力形式非対応"; default -> "未初期化/非対応";
        });
        say("空間化拡張 " + d.spatialize() + " | フィルター " + d.filters() + " | 空間化累計 " + SpatialAudio.positioned.get() + "（ステレオ " + SpatialAudio.stereo.get() + "）");
        say("追跡 " + AcousticScene.activeCount + "/96 | 前区間のray " + AcousticScene.lastRays + "/12");
        if (!AudioSettings.INSTANCE.hrtf.isEmpty() && !AudioSettings.INSTANCE.hrtf.equals(d.hrtf())) say("指定したHRTFは適用されていません: " + AudioSettings.INSTANCE.hrtf);
    }
    public static int startTest(String kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { say("ワールド内で実行してください"); return 0; }
        stopTest(mc);
        Vec3 center = mc.gameRenderer.mainCamera().position();
        double yaw = Math.toRadians(mc.player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).scale(6);
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(6);
        testPositions = new Vec3[]{center.add(forward), center.add(right), center.subtract(forward), center.subtract(right), center.add(0,6,0), center.add(0,-6,0)};
        testStep = 0; delay = 0; testKind = kind; testWorld = mc.level;
        say(kind + "定位テスト。静かな開けた場所で、開始時の向きを保って比較してください。遮蔽は現在の設定を使います");
        return 1;
    }
    private static void testTick(Minecraft mc) {
        if (testPositions == null) return;
        if (mc.level != testWorld || mc.player == null) { stopTest(mc); return; }
        if (delay-- > 0) return;
        if (testStep == testPositions.length) { testPositions = null; say("定位テスト終了"); return; }
        Vec3 p = testPositions[testStep]; say(LABELS[testStep++]); delay = 29;
        mc.getSoundManager().play(new SimpleSoundInstance(Identifier.fromNamespaceAndPath("iem_compatibility", "audio_probe." + testKind),
                SoundSource.BLOCKS, .5f, 1, RandomSource.create(), false, 0, SoundInstance.Attenuation.LINEAR, p.x, p.y, p.z, false));
    }
    public static void stopTest(Minecraft mc) {
        testPositions = null; testWorld = null;
        for (String kind : new String[]{"mono", "stereo"}) mc.getSoundManager().stop(Identifier.fromNamespaceAndPath("iem_compatibility", "audio_probe." + kind), SoundSource.BLOCKS);
    }
    private static void say(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal(message));
    }
}
