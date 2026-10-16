package com.ruskserver.iem_compatibility.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * ポストエフェクトのパスごとと、ワールドの描画全体の GPU の時間 (/iemgpu で付け外し)。パスの描画の前後に GPU のタイムスタンプを書き、
 * 数フレーム後に結果が届いたらシェーダーの名前ごとに足して、なめらかにした値 (ms) を画面の左上に出す。
 * 同じシェーダーを何度も使うパス (フレアのぼかしなど) は合計、使われなくなったパスはだんだん 0 に近づけて消す
 */
public final class PassTimer {
    /** 結果を待つ間に使い回すフレームの数と、1 フレームで測るパスの数の上限 */
    private static final int FRAMES = 4, MAX_PASSES = 96;
    /** なめらかにする割合 (1 フレームごと) と、表示する行の数 */
    private static final double SMOOTHING = 0.05;
    private static final int LINES = 20;

    private static boolean enabled;
    private static GpuQueryPool pool;
    private static final String[][] names = new String[FRAMES][MAX_PASSES];
    private static final int[] counts = new int[FRAMES];
    private static int frame;
    private static final Map<String, Double> averages = new HashMap<>();

    private PassTimer() {
    }

    /** 付け外し。付けたら true */
    static boolean toggle() {
        enabled = !enabled;
        averages.clear();
        java.util.Arrays.fill(counts, 0);
        return enabled;
    }

    /** ポストエフェクトの前に 1 回: 次の記録の場所に移り、そこにあった (FRAMES フレーム前の) 結果を集める */
    public static void beginFrame() {
        if (!enabled) return;
        if (pool == null) pool = RenderSystem.getDevice().createTimestampQueryPool(FRAMES * MAX_PASSES * 2);
        frame = (frame + 1) % FRAMES;
        collect(frame);
        counts[frame] = 0;
    }

    public static boolean enabled() {
        return enabled;
    }

    /** パスを描く前。戻り値は end に渡す (測らない時は -1) */
    public static int begin(RenderPass pass, String name) {
        if (!enabled || pool == null || counts[frame] >= MAX_PASSES) return -1;
        int i = counts[frame]++;
        names[frame][i] = name;
        int index = (frame * MAX_PASSES + i) * 2;
        pass.writeTimestamp(pool, index);
        return index;
    }

    public static void end(RenderPass pass, int index) {
        if (index >= 0) pass.writeTimestamp(pool, index + 1);
    }

    /** レンダーパスの外 (ワールドの描画全体など) を測り始める。戻り値は endOutside に渡す */
    public static int beginOutside(String name) {
        if (!enabled || pool == null || counts[frame] >= MAX_PASSES) return -1;
        int i = counts[frame]++;
        names[frame][i] = name;
        int index = (frame * MAX_PASSES + i) * 2;
        RenderSystem.getDevice().createCommandEncoder().writeTimestamp(pool, index);
        return index;
    }

    public static void endOutside(int index) {
        if (index >= 0) RenderSystem.getDevice().createCommandEncoder().writeTimestamp(pool, index + 1);
    }

    private static void collect(int slot) {
        int count = counts[slot];
        if (count == 0) return;
        OptionalLong[] stamps = pool.getValues(slot * MAX_PASSES * 2, count * 2);
        double nanosPerTick = RenderSystem.getDevice().getDeviceInfo().timestampPeriod();
        Map<String, Double> sums = new HashMap<>();
        for (int i = 0; i < count; i++) {
            OptionalLong start = stamps[2 * i], end = stamps[2 * i + 1];
            if (start.isEmpty() || end.isEmpty()) return;   // まだ届いていない (このフレームは集めない)
            double ms = (end.getAsLong() - start.getAsLong()) * nanosPerTick / 1e6;
            sums.merge(names[slot][i], ms, Double::sum);
        }
        for (Map.Entry<String, Double> entry : sums.entrySet()) {
            averages.merge(entry.getKey(), entry.getValue(), (old, now) -> old + (now - old) * SMOOTHING);
        }
        averages.replaceAll((name, ms) -> sums.containsKey(name) ? ms : ms * (1 - SMOOTHING));
        averages.values().removeIf(ms -> ms < 0.001);
    }

    /** 画面の左上に、時間のかかっている順に出す */
    static void draw(GuiGraphicsExtractor graphics) {
        if (!enabled) return;
        Font font = Minecraft.getInstance().font;
        List<Map.Entry<String, Double>> rows = new ArrayList<>(averages.entrySet());
        rows.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        double total = 0;
        for (Map.Entry<String, Double> row : rows) total += row.getValue();
        List<String> lines = new ArrayList<>();
        lines.add(String.format("IEM GPU %.2f ms (world + post)", total));
        for (int i = 0; i < Math.min(LINES, rows.size()); i++) {
            lines.add(String.format("%6.2f  %s", rows.get(i).getValue(), rows.get(i).getKey()));
        }
        int width = 0;
        for (String line : lines) width = Math.max(width, font.width(line));
        graphics.fill(2, 2, 6 + width, 4 + lines.size() * 10, 0xA0000000);
        for (int i = 0; i < lines.size(); i++) graphics.text(font, lines.get(i), 4, 4 + i * 10, 0xFFFFFFFF);
    }
}
