package com.ruskserver.iem_compatibility.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * サーバーの光のデータを、リソースパックのポストエフェクト (iem:grade の sun_cache の段) が読むテクスチャ iem:light_data に書く。
 * 並びは sun_cache と同じ (iem_control.glsl の iemDataTexel: 幅 512、行 1 から) で、色もデータの画素と同じ
 * (R = 上 6bit、G = 下 8bit、B = 印 0xA0 + 位置のずれ 8 (ずれ無し))。sun_cache は、ヘッダーの印のマスに値があればこちらをそのまま使う
 * (mod は起動した時から「光なし」を書いておくので、いつもこちら。mod の無いクライアントはパックの空の画像なので、画面のデータの画素を読む)。
 * <p>
 * リソースパックには同じ名前の空の画像があるが、ポストエフェクトはテクスチャをテクスチャの管理から名前で取るので、
 * 先に登録しておいたこちらが使われる
 */
public final class LightData {
    /** ポストエフェクトの "location": "iem:light_data" (PostChain が textures/effect/<名前>.png にする) */
    static final Identifier ID = Identifier.fromNamespaceAndPath("iem", "textures/effect/light_data.png");
    /** iem_control.glsl の IEM_DATA_WIDTH と、行の数 (行 0 は使わない) */
    private static final int WIDTH = 512, HEIGHT = 33;
    private static final int CAPACITY = WIDTH * (HEIGHT - 1);
    private static final int SIGNATURE = 0xA0 | 8;

    /** iem_sun.glsl の IEM_DATA_HEADER (ヘッダーの印のマス) と IEM_SUN_MAGIC */
    private static final int HEADER = 3, MAGIC = 0x2B6C;

    private static DynamicTexture texture;
    /** 今サーバーの光があるか (最後に届いたデータのヘッダーの印が合っている) */
    private static boolean active;

    private LightData() {
    }

    /** ポストエフェクトがテクスチャを取りに来る前 (クライアントの起動後すぐ) に登録する */
    static void register(Minecraft minecraft) {
        texture = new DynamicTexture(() -> "IEM light data", WIDTH, HEIGHT, true);
        minecraft.getTextureManager().register(ID, texture);
        clear();
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
        active = count > HEADER && ((((cells[2 * HEADER] & 0xFF) << 8) | (cells[2 * HEADER + 1] & 0xFF)) & 0x3FFF) == MAGIC;
    }

    /**
     * 「光なし」にする (起動した時・サーバーから抜けた時): ヘッダーの印のマスを 0 (印と違う値) にして書く。
     * sun_cache はこのテクスチャを使い続け (mod がある時は画面のデータの画素は無い)、光の格子は無いことになる
     */
    static void clear() {
        active = false;
        if (texture == null) return;
        texture.getPixels().fillRect(0, 0, WIDTH, HEIGHT, 0);
        texture.getPixels().setPixel(HEADER % WIDTH, 1 + HEADER / WIDTH, 0xFF000000 | SIGNATURE);
        texture.upload();
    }

    /** 今サーバーの光があるか (無ければ光の段を描かなくてよい。PostPassMixin) */
    public static boolean active() {
        return active;
    }
}
