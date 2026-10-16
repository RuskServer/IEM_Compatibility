package com.ruskserver.iem_compatibility.client;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * サーバーの光 (ImpossbleEscapeMC の SunShadowService) のデータ。中身はデータのマスの値を 0 番から順に 2 バイトずつ (ビッグエンディアン、下 14bit)。
 * 並びはリソースパックの iem_sun.glsl と同じ (0〜2 番は使わない、3 番からヘッダー、18 番から格子)
 */
public record LightPayload(byte[] cells) implements CustomPacketPayload {
    public static final Type<LightPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("iem", "light"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LightPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.cells),
            buf -> {
                byte[] cells = new byte[buf.readableBytes()];
                buf.readBytes(cells);
                return new LightPayload(cells);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
