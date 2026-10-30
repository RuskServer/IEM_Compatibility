package com.ruskserver.iem_compatibility.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import com.ruskserver.iem_compatibility.client.audio.*;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.openal.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
abstract class AudioChannelMixin implements AudioChannel {
    @Shadow @Final private int source;
    @Shadow private AudioStream stream;
    @Unique private AcousticScene.Entry iem$entry;
    @Unique private boolean iem$positional;
    @Unique private int iem$filter;
    @Unique private int iem$channels;
    @Unique private final float[] iem$sourcePosition = new float[3], iem$listenerPosition = new float[3];
    @Unique private float iem$mix = 1, iem$gain = 1, iem$hf = 1, iem$requestedVolume = 1;

    @Override public void iem$configure(boolean positional, AcousticScene.Entry entry) {
        iem$positional = positional; iem$entry = entry;
    }
    @Inject(method = "play", at = @At("HEAD"))
    private void iem$prepare(CallbackInfo ci) {
        if (!iem$positional) return;
        int buffer = AL10.alGetSourcei(source, AL10.AL_BUFFER);
        int channels = stream != null ? stream.getFormat().getChannels() : buffer != 0 ? AL10.alGetBufferi(buffer, AL10.AL_CHANNELS) : 0;
        if (channels < 1 || channels > 2 || !AL.getCapabilities().AL_SOFT_source_spatialize) return;
        iem$channels = channels;
        AL10.alSourcei(source, SOFTSourceSpatialize.AL_SOURCE_SPATIALIZE_SOFT, AL10.AL_TRUE);
        iem$mix = AudioPolicy.mixGain(channels, true);
        iem$coincidentStereo();
        SpatialAudio.positioned.incrementAndGet();
        if (channels == 2) SpatialAudio.stereo.incrementAndGet();
        if (ALC.getCapabilities().ALC_EXT_EFX && AudioSettings.INSTANCE.occlusion && iem$entry != null && !iem$entry.serverFiltered()) {
            iem$filter = EXTEfx.alGenFilters();
            if (iem$filter != 0) {
                EXTEfx.alFilteri(iem$filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
                EXTEfx.alFilterf(iem$filter, EXTEfx.AL_LOWPASS_GAIN, 1);
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, iem$filter);
            }
        }
        if (iem$entry != null) { iem$gain = iem$entry.target.gain(); iem$hf = iem$entry.target.highFrequency(); }
        iem$apply();
    }
    @ModifyVariable(method = "setVolume", at = @At("HEAD"), argsOnly = true)
    private float iem$volume(float volume) { iem$requestedVolume = volume; return volume * iem$mix * iem$gain; }
    @Inject(method = "updateStream", at = @At("HEAD"))
    private void iem$smooth(CallbackInfo ci) {
        if (iem$entry == null) return;
        var target = iem$entry.target;
        iem$coincidentStereo();
        iem$gain += (target.gain() - iem$gain) * .28f;
        iem$hf += (target.highFrequency() - iem$hf) * .28f;
        iem$apply();
    }
    @Unique private void iem$apply() {
        AL10.alSourcef(source, AL10.AL_GAIN, iem$requestedVolume * iem$mix * iem$gain);
        if (iem$filter != 0) {
            EXTEfx.alFilterf(iem$filter, EXTEfx.AL_LOWPASS_GAINHF, iem$hf);
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, iem$filter);
        }
    }
    /** OpenALは聞き手と完全同位置のステレオを合成しない。その場合に半音量にしない。 */
    @Unique private void iem$coincidentStereo() {
        if (iem$channels != 2) return;
        AL10.alGetSourcefv(source, AL10.AL_POSITION, iem$sourcePosition);
        AL10.alGetListenerfv(AL10.AL_POSITION, iem$listenerPosition);
        boolean same = true;
        for (int i = 0; i < 3; i++) same &= iem$sourcePosition[i] == iem$listenerPosition[i];
        iem$mix = same ? 1f : .5f;
    }
    @Inject(method = "destroy", at = @At("HEAD"))
    private void iem$cleanup(CallbackInfo ci) {
        if (iem$filter != 0) {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            EXTEfx.alDeleteFilters(iem$filter); iem$filter = 0;
        }
        iem$entry = null;
    }
}
