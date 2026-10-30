package com.ruskserver.iem_compatibility.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import java.util.ArrayList;
import java.util.List;

/** ワールドへのアクセスはクライアントスレッドのみ。OpenALには不変の目標値だけ渡す。 */
public final class AcousticScene {
    public static final class Entry {
        final SoundInstance sound;
        final ChannelAccess.ChannelHandle handle;
        final boolean alreadyOccluded;
        public volatile AudioPolicy.Response target = AudioPolicy.Response.CLEAR;
        int lastTick = -100;
        public boolean serverFiltered() { return alreadyOccluded; }
        Entry(SoundInstance sound, ChannelAccess.ChannelHandle handle) {
            this.sound = sound; this.handle = handle;
            var id = sound.getIdentifier();
            alreadyOccluded = AudioPolicy.serverOccluded(id.getNamespace(), id.getPath());
        }
    }
    private static final List<Entry> ACTIVE = new ArrayList<>();
    private static final int MAX_ACTIVE = 96, MAX_RAYS = 12, UPDATE_TICKS = 4;
    private static int tick, rays, cursor;
    private static Object world;
    public static volatile int activeCount, lastRays;
    private AcousticScene() {}

    public static Entry begin(SoundInstance sound, ChannelAccess.ChannelHandle handle) {
        Entry entry = new Entry(sound, handle);
        Minecraft mc = Minecraft.getInstance();
        Runnable add = () -> {
            resetWorld(mc);
            if (ACTIVE.size() >= MAX_ACTIVE || handle.isStopped()) return;
            ACTIVE.add(entry);
            // 短い着弾音にも、予算が残っていれば初回から遮蔽を反映する。
            update(mc, entry);
        };
        if (mc.isSameThread()) add.run(); else mc.execute(add);
        return entry;
    }
    private static void resetWorld(Minecraft mc) {
        if (world != mc.level) { ACTIVE.clear(); world = mc.level; cursor = 0; }
    }
    public static void tick(Minecraft mc) {
        resetWorld(mc);
        lastRays = rays; rays = 0; tick++;
        ACTIVE.removeIf(e -> e.handle.isStopped());
        int count = ACTIVE.size();
        for (int i = 0; i < count && rays + 3 <= MAX_RAYS; i++) {
            if (cursor >= count) cursor = 0;
            Entry e = ACTIVE.get(cursor++);
            if (tick - e.lastTick >= UPDATE_TICKS) update(mc, e);
        }
        activeCount = count;
    }
    private static void update(Minecraft mc, Entry e) {
        if (mc.level == null || mc.player == null || !AudioSettings.INSTANCE.enabled || !AudioSettings.INSTANCE.occlusion || e.alreadyOccluded) {
            e.target = AudioPolicy.Response.CLEAR; e.lastTick = tick; return;
        }
        var camera = mc.gameRenderer.mainCamera();
        Vec3 from = camera.isInitialized() ? camera.position() : mc.player.getEyePosition();
        Vec3 to = new Vec3(e.sound.getX(), e.sound.getY(), e.sound.getZ());
        double distance = from.distanceTo(to);
        if (distance > 128) { e.target = AudioPolicy.response(distance, 0, false); e.lastTick = tick; return; }
        if (rays + 3 > MAX_RAYS) return;
        Vec3 delta = to.subtract(from);
        Vec3 side = new Vec3(-delta.z, 0, delta.x).normalize().scale(.18);
        double[] blocked = {barrier(mc, from, to), barrier(mc, from.add(side), to), barrier(mc, from.subtract(side), to)};
        java.util.Arrays.sort(blocked);
        e.target = AudioPolicy.response(distance, blocked[1], false);
        e.lastTick = tick;
    }
    private static double barrier(Minecraft mc, Vec3 from, Vec3 to) {
        rays++;
        if (!AcousticMath.loaded(from.x, from.z, to.x, to.z, (x,z) -> mc.level.hasChunk(x,z))) return 1;
        var hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return 0;
        var state = mc.level.getBlockState(hit.getBlockPos());
        return state.is(BlockTags.LEAVES) ? .25 : state.is(BlockTags.WOOL) ? 1.5 : 1;
    }
}
