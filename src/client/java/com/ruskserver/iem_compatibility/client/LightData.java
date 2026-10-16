package com.ruskserver.iem_compatibility.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * サーバーの光のデータを、リソースパックのポストエフェクト (iem:grade の sun_cache の段) が読むテクスチャ iem:light_data に書く。
 * 並びは sun_cache と同じ (iem_control.glsl の iemDataTexel: 幅 512、行 1 から) で、色もデータの画素と同じ
 * (R = 上 6bit、G = 下 8bit、B = 印 0xA0 + 位置のずれ 8 (ずれ無し))。sun_cache は、ヘッダーの印のマスに値があればこちらをそのまま使い、
 * 無ければ (mod の無いクライアント・データが届いていない時) 今まで通り画面のデータの画素を読む。
 * <p>
 * リソースパックには同じ名前の空の画像があるが、ポストエフェクトはテクスチャをテクスチャの管理から名前で取るので、
 * 先に登録しておいたこちらが使われる
 */
final class LightData {
    /** ポストエフェクトの "location": "iem:light_data" (PostChain が textures/effect/<名前>.png にする) */
    static final Identifier ID = Identifier.fromNamespaceAndPath("iem", "textures/effect/light_data.png");
    /** iem_control.glsl の IEM_DATA_WIDTH と、行の数 (行 0 は使わない) */
    private static final int WIDTH = 512, HEIGHT = 33;
    private static final int CAPACITY = WIDTH * (HEIGHT - 1);
    private static final int SIGNATURE = 0xA0 | 8;

    private static DynamicTexture texture;

    private LightData() {
    }

    /** ポストエフェクトがテクスチャを取りに来る前 (クライアントの起動後すぐ) に登録する */
    static void register(Minecraft minecraft) {
        texture = new DynamicTexture(() -> "IEM light data", WIDTH, HEIGHT, true);
        texture.upload();
        minecraft.getTextureManager().register(ID, texture);
    }

    /** 届いたデータで全部のマスを書き直す (届いた数より後ろのマスは空にする) */
    static void set(byte[] cells) {
        if (texture == null) return;
        NativeImage pixels = texture.getPixels();
        int count = Math.min(cells.length / 2, CAPACITY);
        for (int i = 0; i < CAPACITY; i++) {
            int color = 0;
            if (i < count) {
                int value = (((cells[2 * i] & 0xFF) << 8) | (cells[2 * i + 1] & 0xFF)) & 0x3FFF;
                color = 0xFF000000 | (value >> 8) << 16 | (value & 0xFF) << 8 | SIGNATURE;
            }
            pixels.setPixel(i % WIDTH, 1 + i / WIDTH, color);
        }
        texture.upload();
    }

    /** データを消す (サーバーから抜けた時。sun_cache は画面のデータの画素を読むのに戻る) */
    static void clear() {
        if (texture == null) return;
        texture.getPixels().fillRect(0, 0, WIDTH, HEIGHT, 0);
        texture.upload();
    }
}
