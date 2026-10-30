package com.ruskserver.iem_compatibility.client.audio;

import org.lwjgl.openal.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.LoggerFactory;

public final class SpatialAudio {
    public record Device(String renderer, String hrtf, int status, boolean spatialize, boolean filters, List<String> profiles) {}
    public static volatile Device device = new Device("未初期化", "", -1, false, false, List.of());
    public static final AtomicLong positioned = new AtomicLong(), stereo = new AtomicLong();
    private SpatialAudio() {}

    /** Library が使う同じデバイスで列挙する。IDは保存せず、名前から毎回解決する。 */
    public static int profile(long device) {
        if (!ALC.getCapabilities().ALC_SOFT_HRTF) return -1;
        String wanted = AudioSettings.INSTANCE.hrtf;
        int n = ALC10.alcGetInteger(device, SOFTHRTF.ALC_NUM_HRTF_SPECIFIERS_SOFT);
        for (int i = 0; i < n; i++) if (wanted.equals(SOFTHRTF.alcGetStringiSOFT(device, SOFTHRTF.ALC_HRTF_SPECIFIER_SOFT, i))) return i;
        return -1;
    }
    public static void inspect(long handle) {
        boolean hrtf = ALC.getCapabilities().ALC_SOFT_HRTF;
        List<String> names = new ArrayList<>();
        if (hrtf) {
            int n = ALC10.alcGetInteger(handle, SOFTHRTF.ALC_NUM_HRTF_SPECIFIERS_SOFT);
            for (int i = 0; i < n; i++) names.add(SOFTHRTF.alcGetStringiSOFT(handle, SOFTHRTF.ALC_HRTF_SPECIFIER_SOFT, i));
        }
        var caps = AL.getCapabilities();
        device = new Device(AL10.alGetString(AL10.AL_VERSION), hrtf ? ALC10.alcGetString(handle, SOFTHRTF.ALC_HRTF_SPECIFIER_SOFT) : "非対応",
                hrtf ? ALC10.alcGetInteger(handle, SOFTHRTF.ALC_HRTF_STATUS_SOFT) : -1,
                caps.AL_SOFT_source_spatialize, ALC.getCapabilities().ALC_EXT_EFX, List.copyOf(names));
        LoggerFactory.getLogger("IEM Audio").info("音響デバイス: {}", device);
    }
}
