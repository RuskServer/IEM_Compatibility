package com.ruskserver.iem_compatibility.client.audio;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;

public final class AudioSettings {
    public boolean enabled = true;
    public boolean occlusion = true;
    public String hrtf = "";
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("iem-audio.json");
    public static final AudioSettings INSTANCE = load();
    private static AudioSettings load() {
        try {
            if (Files.isRegularFile(FILE)) {
                AudioSettings settings = new GsonBuilder().create().fromJson(Files.readString(FILE), AudioSettings.class);
                if (settings != null) { if (settings.hrtf == null) settings.hrtf = ""; return settings; }
            }
        } catch (Exception e) { LoggerFactory.getLogger("IEM Audio").warn("音響設定を読めません。標準設定を使います", e); }
        return new AudioSettings();
    }
    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(this));
        } catch (Exception e) { LoggerFactory.getLogger("IEM Audio").warn("音響設定を保存できません", e); }
    }
}
