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


public class Iem_compatibilityClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        com.ruskserver.iem_compatibility.client.audio.AudioCommands.register();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommands.literal("iemconfig").executes(command -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.execute(() -> minecraft.gui.setScreen(IemConfigScreen.create(minecraft.gui.screen())));
                    return 1;
                })));
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
