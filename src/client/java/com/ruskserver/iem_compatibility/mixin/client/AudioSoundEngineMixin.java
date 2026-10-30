package com.ruskserver.iem_compatibility.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Channel;
import com.ruskserver.iem_compatibility.client.audio.*;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.function.Consumer;

@Mixin(SoundEngine.class)
abstract class AudioSoundEngineMixin {
    @WrapOperation(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"))
    private void iem$source(ChannelAccess.ChannelHandle handle, Consumer<Channel> setup, Operation<Void> original, @Local(argsOnly = true) SoundInstance sound) {
        var id = sound.getIdentifier();
        boolean positional = AudioSettings.INSTANCE.enabled && AudioPolicy.positional(sound.getSource().getName(), id.getNamespace(), id.getPath(),
                sound.isRelative(), sound.getAttenuation() == SoundInstance.Attenuation.LINEAR);
        AcousticScene.Entry entry = positional ? AcousticScene.begin(sound, handle) : null;
        original.call(handle, (Consumer<Channel>) channel -> {
            setup.accept(channel);
            ((AudioChannel)channel).iem$configure(positional, entry);
        });
    }
}
