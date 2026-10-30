package com.ruskserver.iem_compatibility.mixin.client;

import com.mojang.blaze3d.audio.Library;
import com.ruskserver.iem_compatibility.client.audio.AudioSettings;
import com.ruskserver.iem_compatibility.client.audio.SpatialAudio;
import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.nio.IntBuffer;

@Mixin(Library.class)
abstract class AudioLibraryMixin {
    @Shadow private long currentDevice;
    @ModifyVariable(method = "init", at = @At("HEAD"), argsOnly = true)
    private boolean iem$headphones(boolean vanilla) { return AudioSettings.INSTANCE.enabled || vanilla; }

    @Inject(method = "createAttributes", at = @At("RETURN"))
    private void iem$profile(MemoryStack stack, boolean hrtf, CallbackInfoReturnable<IntBuffer> cir) {
        if (!AudioSettings.INSTANCE.enabled || !hrtf) return;
        int id = SpatialAudio.profile(currentDevice);
        if (id < 0) return;
        IntBuffer attributes = cir.getReturnValue();
        for (int i = attributes.position(); i + 1 < attributes.limit(); i += 2)
            if (attributes.get(i) == SOFTHRTF.ALC_HRTF_ID_SOFT) attributes.put(i + 1, id);
    }
    @Inject(method = "init", at = @At("TAIL"))
    private void iem$inspect(CallbackInfo ci) { SpatialAudio.inspect(currentDevice); }
}
