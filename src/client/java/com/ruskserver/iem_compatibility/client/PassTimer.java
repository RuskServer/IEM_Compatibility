package com.ruskserver.iem_compatibility.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.profiling.Profiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * GPU の時間 (/iemgpu で付け外し): フレーム全体 (frame。GameRenderer.render)、ワールドの描画全体 (world)、その中のパイプライン (コアシェーダー) ごと、
 * ポストエフェクトのパスごと、GUI (gui)。1 フレームの実時間 (CPU も含む) も出し、GPU の時間と比べて GPU が律速かを見られるようにする。
 * 区切りに GPU のタイムスタンプを書き、数フレーム後に結果が届いたら名前ごとに足して、なめらかにした値 (ms) を画面の左上に出す。
 * ワールドの中は、パイプラインを切り替えてから次に切り替える (かレンダーパスを閉じる) までをそのパイプラインの時間にする
 * (字下げして出す。world の内訳なので合計には入れない)。同じ名前は合計、使われなくなった名前はだんだん 0 に近づけて消す
 */
public final class PassTimer {
    /** 結果を待つ間に使い回すフレームの数と、1 フレームのタイムスタンプ・区間の数の上限 */
    private static final int FRAMES = 4, MAX_STAMPS = 4096, MAX_RECORDS = 4096;
    /** なめらかにする割合 (1 フレームごと) と、表示する行の数 */
    private static final double SMOOTHING = 0.05;
    private static final int LINES = 28;
    /** world の内訳 (パイプラインごと) の名前の頭 */
    private static final String SEGMENT = "  ";

    private static boolean enabled;
    private static GpuQueryPool pool;
    private static final int[] stamps = new int[FRAMES];
    private static final int[] recordCounts = new int[FRAMES];
    private static final String[][] names = new String[FRAMES][MAX_RECORDS];
    private static final int[][] starts = new int[FRAMES][MAX_RECORDS];
    private static final int[][] ends = new int[FRAMES][MAX_RECORDS];
    private static int frame;
    /** ワールドを描いている間 (パイプラインごとに測る) */
    private static boolean inWorld;
    private static final Map<String, Double> averages = new HashMap<>();
    /** 1 フレームの実時間 (ms。なめらかにした値) と、前のフレームの始まり */
    private static double frameMillis;
    private static long lastFrameStart;

    private PassTimer() {
    }

    /** 付け外し。付けたら true */
    static boolean toggle() {
        enabled = !enabled;
        averages.clear();
        java.util.Arrays.fill(stamps, 0);
        java.util.Arrays.fill(recordCounts, 0);
        return enabled;
    }

    public static boolean enabled() {
        return enabled;
    }

    /** フレームの初め (GameRenderer.render の最初) に 1 回: 次の記録の場所に移り、そこにあった (FRAMES フレーム前の) 結果を集める */
    public static void beginFrame() {
        if (!enabled) return;
        long now = System.nanoTime();
        if (lastFrameStart != 0) {
            double ms = (now - lastFrameStart) / 1e6;
            frameMillis = frameMillis == 0 ? ms : frameMillis + (ms - frameMillis) * SMOOTHING;
        }
        lastFrameStart = now;
        Profiler.get().push("iem_gpuTimer");
        if (pool == null) pool = RenderSystem.getDevice().createTimestampQueryPool(FRAMES * MAX_STAMPS);
        frame = (frame + 1) % FRAMES;
        collect(frame);
        stamps[frame] = 0;
        recordCounts[frame] = 0;
        Profiler.get().pop();
    }

    // ---- タイムスタンプと区間 ----

    private static int stamp(RenderPass pass) {
        if (!enabled || pool == null || stamps[frame] >= MAX_STAMPS) return -1;
        int index = frame * MAX_STAMPS + stamps[frame]++;
        pass.writeTimestamp(pool, index);
        return index;
    }

    private static int stampOutside() {
        if (!enabled || pool == null || stamps[frame] >= MAX_STAMPS) return -1;
        int index = frame * MAX_STAMPS + stamps[frame]++;
        RenderSystem.getDevice().createCommandEncoder().writeTimestamp(pool, index);
        return index;
    }

    private static int open(String name, int start) {
        if (start < 0 || recordCounts[frame] >= MAX_RECORDS) return -1;
        int record = recordCounts[frame]++;
        names[frame][record] = name;
        starts[frame][record] = start;
        ends[frame][record] = -1;
        return record;
    }

    private static void close(int record, int end) {
        if (record >= 0 && end >= 0 && record < recordCounts[frame]) ends[frame][record] = end;
    }

    // ---- レンダーパスの外 (フレーム全体・GUI) ----

    public static int beginOutside(String name) {
        return enabled ? open(name, stampOutside()) : -1;
    }

    public static void endOutside(int record) {
        if (record >= 0) close(record, stampOutside());
    }

    // ---- ポストエフェクトのパス ----

    /** パスを描く前。戻り値は end に渡す (測らない時は -1) */
    public static int begin(RenderPass pass, String name) {
        return enabled ? open(name, stamp(pass)) : -1;
    }

    public static void end(RenderPass pass, int record) {
        if (record >= 0) close(record, stamp(pass));
    }

    // ---- ワールドの描画全体と、その中のパイプラインごと ----

    /** ワールドを描き始める時。戻り値は endWorld に渡す */
    public static int beginWorld() {
        if (!enabled) return -1;
        inWorld = true;
        return open("world", stampOutside());
    }

    public static void endWorld(int record) {
        inWorld = false;
        if (record >= 0) close(record, stampOutside());
    }

    /** レンダーパスでパイプラインを切り替えた時: 開いている区間 (前のパイプライン) を閉じ、新しいパイプラインの区間を開く。戻り値は次に渡す */
    public static int switchPipeline(RenderPass pass, int open, String pipeline) {
        if (!enabled || !inWorld) return -1;
        int at = stamp(pass);
        close(open, at);
        return open(SEGMENT + pipeline, at);
    }

    /** レンダーパスを閉じる時: 開いている区間を閉じる */
    public static void closePipeline(RenderPass pass, int open) {
        if (open >= 0) close(open, stamp(pass));
    }

    // ---- 集める・出す ----

    private static void collect(int slot) {
        int count = stamps[slot];
        if (count == 0) return;
        OptionalLong[] values = pool.getValues(slot * MAX_STAMPS, count);
        double nanosPerTick = RenderSystem.getDevice().getDeviceInfo().timestampPeriod();
        Map<String, Double> sums = new HashMap<>();
        for (int r = 0; r < recordCounts[slot]; r++) {
            int end = ends[slot][r];
            if (end < 0) continue;
            OptionalLong a = values[starts[slot][r] - slot * MAX_STAMPS], b = values[end - slot * MAX_STAMPS];
            if (a.isEmpty() || b.isEmpty()) return;   // まだ届いていない (このフレームは集めない)
            sums.merge(names[slot][r], (b.getAsLong() - a.getAsLong()) * nanosPerTick / 1e6, Double::sum);
        }
        for (Map.Entry<String, Double> entry : sums.entrySet()) {
            averages.merge(entry.getKey(), entry.getValue(), (old, now) -> old + (now - old) * SMOOTHING);
        }
        averages.replaceAll((name, ms) -> sums.containsKey(name) ? ms : ms * (1 - SMOOTHING));
        averages.values().removeIf(ms -> ms < 0.001);
    }

    /**
     * 画面の左上に出す: 1 行目は 1 フレームの実時間と GPU の時間 (GPU の時間が実時間に近ければ GPU が律速)、2 行目は GPU の時間の内訳
     * (world・ポストエフェクト・gui・その他)、その下に時間のかかっている順 (world の内訳は字下げ。frame は 1 行目に出すので除く)
     */
    static void draw(GuiGraphicsExtractor graphics) {
        if (!enabled) return;
        Font font = Minecraft.getInstance().font;
        List<Map.Entry<String, Double>> rows = new ArrayList<>(averages.entrySet());
        rows.removeIf(row -> row.getKey().equals("frame"));
        rows.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        double frameGpu = averages.getOrDefault("frame", 0.0), world = averages.getOrDefault("world", 0.0), gui = averages.getOrDefault("gui", 0.0);
        double post = 0;
        for (Map.Entry<String, Double> row : rows) {
            String name = row.getKey();
            if (!name.startsWith(SEGMENT) && !name.equals("world") && !name.equals("gui")) post += row.getValue();
        }
        List<String> lines = new ArrayList<>();
        lines.add(String.format("frame %.1f ms (%.0f fps)  GPU %.1f ms (%.0f%%)", frameMillis, frameMillis > 0 ? 1000 / frameMillis : 0,
                frameGpu, frameMillis > 0 ? 100 * frameGpu / frameMillis : 0));
        lines.add(String.format("GPU: world %.2f + post %.2f + gui %.2f + other %.2f", world, post, gui, Math.max(0, frameGpu - world - post - gui)));
        for (int i = 0; i < Math.min(LINES, rows.size()); i++) {
            lines.add(String.format("%6.2f  %s", rows.get(i).getValue(), rows.get(i).getKey()));
        }
        int width = 0;
        for (String line : lines) width = Math.max(width, font.width(line));
        graphics.fill(2, 2, 6 + width, 4 + lines.size() * 10, 0xA0000000);
        for (int i = 0; i < lines.size(); i++) graphics.text(font, lines.get(i), 4, 4 + i * 10, 0xFFFFFFFF);
    }
}
