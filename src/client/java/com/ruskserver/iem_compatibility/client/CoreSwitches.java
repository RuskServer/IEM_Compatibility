package com.ruskserver.iem_compatibility.client;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * 確かめ用 (/iemcore): リソースパックのコアシェーダーの差し替えを機能ごとに切り、重さを比べる (/iemgpu のパイプラインごとの時間で)。
 * 切った機能は IEM_NO_<名前> としてシェーダーをコンパイルする時に定義する (GlslCompilerMixin)。パックはその時バニラと同じ動きにする。
 * 切り替えたらリソースを読み直す (シェーダーが作り直される)。ゲームを終えると元に戻る
 */
public final class CoreSwitches {
    /** 切れる機能 (パックの IEM_NO_<名前の大文字>) */
    public static final List<String> FEATURES = List.of("indirect", "light_mark", "entity", "clouds");
    private static final Set<String> off = new TreeSet<>();

    private CoreSwitches() {
    }

    /** 切っている機能の定義の名前 (IEM_NO_*) */
    public static Set<String> defines() {
        Set<String> defines = new TreeSet<>();
        for (String feature : off) defines.add("IEM_NO_" + feature.toUpperCase(Locale.ROOT));
        return defines;
    }

    static void set(String feature, boolean on) {
        if (on) off.remove(feature);
        else off.add(feature);
    }

    static Set<String> off() {
        return off;
    }
}
