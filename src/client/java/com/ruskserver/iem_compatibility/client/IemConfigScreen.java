package com.ruskserver.iem_compatibility.client;

import com.ruskserver.iem_compatibility.client.audio.*;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 開くたびに設定を読み、保存した変更だけを一度に適用する。 */
public final class IemConfigScreen {
    private record AudioState(boolean enabled, boolean occlusion, String hrtf) {
        static AudioState current() {
            var settings = AudioSettings.INSTANCE;
            return new AudioState(settings.enabled, settings.occlusion, settings.hrtf);
        }
    }
    private IemConfigScreen() {}
    private static Component text(String key) { return Component.translatable("iem.config." + key); }
    private static OptionDescription description(String key) { return OptionDescription.of(text(key + ".description")); }
    private static Option<Boolean> toggle(String key, boolean defaultValue, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder().name(text(key)).description(description(key))
                .binding(defaultValue, getter, setter).controller(option -> BooleanControllerBuilder.create(option).onOffFormatter()).build();
    }
    public static Screen create(Screen parent) { return model(Minecraft.getInstance().level != null, AudioCommands::reload).generateScreen(parent); }

    /** モデルを独立して構築できるようにして、保存・取消の動作も検証する。 */
    public static YetAnotherConfigLib model(boolean inWorld, Runnable applyAudio) {
        AudioSettings settings = AudioSettings.INSTANCE;
        AudioState[] applied = {AudioState.current()};
        List<String> profiles = new ArrayList<>();
        profiles.add("");
        for (String name : SpatialAudio.device.profiles()) if (!profiles.contains(name)) profiles.add(name);
        // 未検出の保存値を勝手に削除しない。既定に戻す操作はユーザーが選べる。
        if (!profiles.contains(settings.hrtf)) profiles.add(settings.hrtf);

        var audio = ConfigCategory.createBuilder().name(text("audio"))
                .option(toggle("enabled", true, () -> settings.enabled, value -> settings.enabled = value))
                .option(toggle("occlusion", true, () -> settings.occlusion, value -> settings.occlusion = value))
                .option(Option.<String>createBuilder().name(text("hrtf")).description(description("hrtf"))
                        .binding("", () -> settings.hrtf, value -> settings.hrtf = value)
                        .controller(option -> CyclingListControllerBuilder.create(option).values(profiles)
                                .valueFormatter(value -> value.isEmpty() ? text("default") : Component.literal(value))).build())
                .option(LabelOption.create(text("apply_note")))
                .build();

        var d = SpatialAudio.device;
        var diagnostics = ConfigCategory.createBuilder().name(text("diagnostics"))
                .option(toggle("gpu", false, PassTimer::enabled, value -> { if (PassTimer.enabled() != value) PassTimer.toggle(); }))
                .option(LabelOption.create(Component.translatable("iem.config.device", d.renderer())))
                .option(LabelOption.create(Component.translatable("iem.config.active_hrtf", Objects.toString(d.hrtf(), ""), text("status." + d.status()))))
                .option(LabelOption.create(Component.translatable("iem.config.extensions", d.spatialize(), d.filters())))
                .option(LabelOption.create(Component.translatable("iem.config.counters", SpatialAudio.positioned.get(), SpatialAudio.stereo.get(), AcousticScene.activeCount, AcousticScene.lastRays)))
                .option(ButtonOption.createBuilder().name(text("test_mono")).text(text("test"))
                        .description(description("test")).available(inWorld)
                        .action(screen -> { screen.finishOrSave(); Minecraft.getInstance().gui.setScreen(null); AudioCommands.startTest("mono"); }).build())
                .option(ButtonOption.createBuilder().name(text("test_stereo")).text(text("test"))
                        .description(description("test")).available(inWorld)
                        .action(screen -> { screen.finishOrSave(); Minecraft.getInstance().gui.setScreen(null); AudioCommands.startTest("stereo"); }).build())
                .option(ButtonOption.createBuilder().name(text("test_stop")).text(text("stop"))
                        .available(inWorld)
                        .action(screen -> AudioCommands.stopTest(Minecraft.getInstance())).build())
                .option(LabelOption.create(text("diagnostics_note")))
                .build();
        return YetAnotherConfigLib.createBuilder().title(text("title")).category(audio).category(diagnostics)
                .save(() -> {
                    AudioState current = AudioState.current();
                    if (!applied[0].equals(current)) { applyAudio.run(); applied[0] = current; }
                }).build();
    }
}
