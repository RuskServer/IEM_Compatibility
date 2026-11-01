package com.ruskserver.iem_compatibility.client;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 発射信号: 0=消灯、1=腰撃ち、2=ADS、3=サプレッサー。 */
public record MuzzlePayload(int mode) implements CustomPacketPayload {
    public static final Type<MuzzlePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("iem", "muzzle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MuzzlePayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByte(payload.mode),
            buf -> new MuzzlePayload(buf.readUnsignedByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
