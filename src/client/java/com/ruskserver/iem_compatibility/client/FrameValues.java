package com.ruskserver.iem_compatibility.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * リソースパックの数値の画素 (iem_control.glsl。空の段が画面の左下に書く値) の代わりに、ゲームが描くのに使った値を
 * uniform IemFrameValues (ポストエフェクトの JSON の uniforms。tools/post_effects.py) として渡す。並びは iem_control_read.glsl と同じ vec4 が 17 個:
 * 0 情報 (mod が書いた、雨の強さ、太陽がある、月がある)、1〜3 カメラの回転、4〜7 投影、8 霧の色、9 空の色、10・11 霧の距離、12 太陽の向き、13 月の向き、14 カメラの位置、15 バイザーの濡れ具合 (プラグインから届いた値。届いていなければ -1)、
 * 16 照明弾 (位置 (視点の座標) と明るさ。照明弾の印のアイテムディスプレイから。無ければ明るさ -1)。
 * 値は空の段が書くものと同じ: 回転は空を描く時の ModelViewMat (視点の回転)、投影は揺れ・視野の変化込み (GameRenderer.renderLevel)、
 * 太陽と月の向きは SkyRenderer の四角の中心 (Y で -90°、X で角度、高さ 100) を視点の座標にしたもの
 */
public final class FrameValues {
    /** ポストエフェクトの uniform の名前 (JSON の uniforms のキー) */
    public static final String NAME = "IemFrameValues";
    private static final int VEC4S = 17;
    /** 照明弾の印 (ImpossbleEscapeMC の SignalFlares が出すアイテムディスプレイのアイテムの模型。明るさはアイテムの色の赤) */
    private static final Identifier FLARE_MODEL = Identifier.fromNamespaceAndPath("iem", "flare_marker");
    private static final int SIZE = VEC4S * 16;

    private static final float[] values = new float[VEC4S * 4];
    private static final ByteBuffer data = MemoryUtil.memAlloc(SIZE);
    private static GpuBuffer buffer;
    /** このフレームの値を拾ったか、と、ポストエフェクトに渡せる値があるか */
    private static boolean captured, ready;
    /** バイザーの濡れ具合 (0..1。プラグインの ScreenEffectService が iem:visor_wet で送る。届いていなければ -1 = パックの値を使う) */
    private static float visorWetness = -1f;

    private static final MuzzlePulse muzzle = new MuzzlePulse();
    private static long frameBinds;

    public static void setMuzzle(int mode) {
        muzzle.receive(mode, System.nanoTime());
    }

    public static void testMuzzle() {
        muzzle.inject(1, System.nanoTime());
    }

    public static void clearMuzzle() {
        muzzle.clear();
    }

    public static String muzzleStatus() {
        return muzzle.status(System.nanoTime()) + " | フレーム値=" + ready + " UBO反映=" + frameBinds;
    }

    private FrameValues() {
    }

    /** ワールドを描く前 (投影を決めた所) に、このフレームの値を拾う */
    public static void capture(LevelRenderState level, Matrix4fc projection) {
        Profiler.get().push("iem_frameValues");
        CameraRenderState camera = level.cameraRenderState;
        SkyRenderState sky = level.skyRenderState;
        FogData fog = camera.fogData;
        Matrix4fc view = camera.viewRotationMatrix;
        boolean celestial = sky.skybox == DimensionType.Skybox.OVERWORLD;

        put(0, 1f, 1f - sky.rainBrightness, celestial ? 1f : 0f, celestial ? 1f : 0f);
        put(1, view.m00(), view.m01(), view.m02(), 0f);
        put(2, view.m10(), view.m11(), view.m12(), 0f);
        put(3, view.m20(), view.m21(), view.m22(), 0f);
        put(4, projection.m00(), projection.m01(), projection.m02(), projection.m03());
        put(5, projection.m10(), projection.m11(), projection.m12(), projection.m13());
        put(6, projection.m20(), projection.m21(), projection.m22(), projection.m23());
        put(7, projection.m30(), projection.m31(), projection.m32(), projection.m33());
        Vector4fc fogColor = fog.color;
        put(8, fogColor.x(), fogColor.y(), fogColor.z(), fogColor.w());
        Vector3fc skyColor = sky.skyColor;
        if (skyColor != null) put(9, skyColor.x(), skyColor.y(), skyColor.z(), 1f);
        else put(9, 0f, 0f, 0f, 1f);
        put(10, fog.environmentalStart, fog.environmentalEnd, fog.renderDistanceStart, fog.renderDistanceEnd);
        put(11, fog.skyEnd, fog.cloudEnd, 0f, 0f);
        Vector3f sun = celestial ? celestialDirection(view, sky.sunAngle) : new Vector3f();
        Vector3f moon = celestial ? celestialDirection(view, sky.moonAngle) : new Vector3f();
        put(12, sun.x, sun.y, sun.z, 0f);
        put(13, moon.x, moon.y, moon.z, 0f);
        put(14, wrap(camera.pos.x), wrap(camera.pos.y), wrap(camera.pos.z), 0f);
        put(15, visorWetness, 0f, 0f, 0f);
        putFlare(view, camera.pos, level.worldPartialTicks);
        captured = true;
        Profiler.get().pop();
    }

    /** ポストエフェクトの前に 1 回: 拾った値をバッファに書く (拾えなかったフレームは渡さない = パックは画面の数値の画素を読む) */
    public static void upload() {
        ready = captured;
        captured = false;
        if (!ready) return;
        if (buffer == null) {
            buffer = RenderSystem.getDevice().createBuffer(() -> "IEM frame values", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, SIZE);
        }
        // captureから描画までの間に届いた発射も反映し、未描画の点灯を消灯通知で失わない。
        values[15 * 4 + 1] = muzzle.sample(System.nanoTime());
        for (int i = 0; i < values.length; i++) data.putFloat(i * 4, values[i]);
        data.position(0).limit(SIZE);
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
    }

    /** バイザーの濡れ具合 (届いた時。サーバーから抜けた時は -1 に戻す) */
    public static void setVisorWetness(float wetness) {
        visorWetness = wetness;
    }

    /** IemFrameValues を持つポストパスを描く直前に、JSON の既定値の代わりにこちらを渡す */
    public static void bind(RenderPass pass) {
        if (ready && buffer != null) {
            pass.setUniform(NAME, buffer);
            frameBinds++;
        }
    }

    /** 照明弾: 照明弾の印のうち一番明るいものの位置 (視点の座標) と明るさ。無ければ明るさ -1 (パックは照明弾の光を描かない) */
    private static void putFlare(Matrix4fc view, Vec3 camera, float partialTick) {
        ClientLevel world = Minecraft.getInstance().level;
        float best = -1f;
        Vector3f at = new Vector3f();
        if (world != null) {
            for (Entity entity : world.entitiesForRendering()) {
                if (!(entity instanceof Display.ItemDisplay display)) continue;
                ItemStack item = display.getItemStack();
                if (!FLARE_MODEL.equals(item.get(DataComponents.ITEM_MODEL))) continue;
                CustomModelData data = item.get(DataComponents.CUSTOM_MODEL_DATA);
                Integer color = data != null ? data.getColor(0) : null;
                float power = color != null ? ((color >> 16) & 255) / 255f : 0f;
                if (power <= best) continue;
                best = power;
                Vec3 position = entity.getPosition(partialTick);
                at.set((float) (position.x - camera.x), (float) (position.y - camera.y), (float) (position.z - camera.z));
            }
        }
        if (best >= 0f) view.transformDirection(at);
        put(16, at.x, at.y, at.z, best);
    }

    private static void put(int vec4, float x, float y, float z, float w) {
        values[vec4 * 4] = x;
        values[vec4 * 4 + 1] = y;
        values[vec4 * 4 + 2] = z;
        values[vec4 * 4 + 3] = w;
    }

    /** 太陽・月の四角の中心の向き (視点の座標、長さ 1)。SkyRenderer.renderSunMoonAndStars と同じ回し方 */
    private static Vector3f celestialDirection(Matrix4fc view, float angle) {
        Vector3f center = new Matrix4f().rotateY((float) Math.toRadians(-90.0)).rotateX(angle).transformPosition(new Vector3f(0f, 100f, 0f));
        return view.transformDirection(center).normalize();
    }

    /** 空の段と同じカメラの位置 (CameraBlockPos & 1023 - CameraOffset: ブロックの座標を 1024 で割った余り + ブロックの中の位置) */
    private static float wrap(double coordinate) {
        double block = Math.floor(coordinate);
        return (float) (Math.floorMod((long) block, 1024L) + (coordinate - block));
    }
}
