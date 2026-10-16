package com.ruskserver.iem_compatibility.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public class Iem_compatibilityClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // サーバーの光: 受け取れることをサーバーに知らせ (チャンネルの登録)、届いたらテクスチャに書く。
        // プラグインはこのチャンネルを持つクライアントにはテキストディスプレイ (データの画素) を出さずにこちらで送る
        PayloadTypeRegistry.clientboundPlay().register(LightPayload.TYPE, LightPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(LightPayload.TYPE, (payload, context) -> LightData.set(payload.cells()));
        ClientLifecycleEvents.CLIENT_STARTED.register(LightData::register);
        // バイザーの濡れ具合: プラグインはこのチャンネルを持つクライアントには段階ごとのエフェクトを付け替えず、値を送る
        PayloadTypeRegistry.clientboundPlay().register(VisorWetPayload.TYPE, VisorWetPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(VisorWetPayload.TYPE, (payload, context) -> FrameValues.setVisorWetness(payload.wetness()));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, minecraft) -> minecraft.execute(() -> {
            LightData.clear();
            FrameValues.setVisorWetness(-1f);
        }));

        // GPU の時間: ワールド全体・その中のパイプラインごと・ポストエフェクトのパスごと (/iemgpu で付け外し、画面の左上に出す)
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("iem_compatibility", "pass_timer"), (graphics, delta) -> PassTimer.draw(graphics));
        // 確かめ用: コアシェーダーの差し替えを機能ごとに切る (/iemcore <機能|all> <on|off>。/iemcore で今の状態)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            var root = ClientCommands.literal("iemcore").executes(command -> {
                command.getSource().sendFeedback(Component.literal("切っている機能: " + (CoreSwitches.off().isEmpty() ? "なし" : String.join(", ", CoreSwitches.off()))
                        + " (切れる機能: " + String.join(", ", CoreSwitches.FEATURES) + ", all)"));
                return 1;
            });
            List<String> choices = new ArrayList<>(CoreSwitches.FEATURES);
            choices.add("all");
            for (String feature : choices) {
                for (boolean on : new boolean[]{true, false}) {
                    root.then(ClientCommands.literal(feature).then(ClientCommands.literal(on ? "on" : "off").executes(command -> {
                        for (String f : feature.equals("all") ? CoreSwitches.FEATURES : List.of(feature)) CoreSwitches.set(f, on);
                        command.getSource().sendFeedback(Component.literal(feature + " を" + (on ? "戻しました" : "切りました") + "。シェーダーを作り直します"));
                        Minecraft.getInstance().reloadResourcePacks();
                        return 1;
                    })));
                }
            }
            dispatcher.register(root);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommands.literal("iemgpu").executes(command -> {
                    boolean on = PassTimer.toggle();
                    command.getSource().sendFeedback(Component.literal(on
                            ? "GPU の時間 (ワールド・パイプラインごと・ポストエフェクト) を左上に出します (もう一度 /iemgpu で消す)"
                            : "GPU の時間を消しました"));
                    return 1;
                })));
    }
}
