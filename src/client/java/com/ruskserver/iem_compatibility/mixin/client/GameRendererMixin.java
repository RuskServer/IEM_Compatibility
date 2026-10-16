package com.ruskserver.iem_compatibility.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.ruskserver.iem_compatibility.client.FrameValues;
import com.ruskserver.iem_compatibility.client.PassTimer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ワールドを描く時の値 (FrameValues) を拾い、ポストエフェクトの前にバッファへ書く */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow
    @Final
    private GameRenderState gameRenderState;

    @Unique
    private int iem$worldTimer = -1;

    /** /iemgpu: ワールドの描画全体 (地形・エンティティ・空など。ポストエフェクトの前) の GPU の時間 */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void iem$beginWorld(CallbackInfo ci) {
        PassTimer.beginFrame();
        this.iem$worldTimer = PassTimer.beginOutside("world");
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void iem$endWorld(CallbackInfo ci) {
        PassTimer.endOutside(this.iem$worldTimer);
    }

    /** 投影は揺れ・視野の変化を掛けた後 (ワールドを描くのに使う投影) */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/ProjectionType;)V",
            ordinal = 0))
    private void iem$captureFrame(CallbackInfo ci, @Local Matrix4f projectionMatrix) {
        FrameValues.capture(this.gameRenderState.levelRenderState, projectionMatrix);
    }

    @Inject(method = "applyPostEffects", at = @At("HEAD"))
    private void iem$beforePostEffects(CallbackInfo ci) {
        FrameValues.upload();
    }
}
