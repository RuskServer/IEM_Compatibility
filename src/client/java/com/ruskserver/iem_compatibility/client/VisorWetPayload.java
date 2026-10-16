package com.ruskserver.iem_compatibility.client;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * バイザーの濡れ具合 (0..1。ImpossbleEscapeMC の ScreenEffectService が雨に打たれている時間から求める)。中身は float 1 つ (ビッグエンディアン)。
 * 届けばパックの水滴 (rain_drops) は段階ごとのエフェクトを付け替えず、この値でなめらかに変わる (FrameValues の IemFrameValues)
 */
public record VisorWetPayload(float wetness) implements CustomPacketPayload {
    public static final Type<VisorWetPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("iem", "visor_wet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VisorWetPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeFloat(payload.wetness),
            buf -> new VisorWetPayload(buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
