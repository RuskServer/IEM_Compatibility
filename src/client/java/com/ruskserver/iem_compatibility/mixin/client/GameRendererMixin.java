package com.ruskserver.iem_compatibility.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.ruskserver.iem_compatibility.client.FrameValues;
import com.ruskserver.iem_compatibility.client.PassTimer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** ワールドを描く時の値 (FrameValues) を拾い、ポストエフェクトの前にバッファへ書く */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow
    @Final
    private GameRenderState gameRenderState;

    @Unique
    private int iem$worldTimer = -1, iem$frameTimer = -1, iem$guiTimer = -1;

    /** /iemgpu: フレーム全体 (ワールド・ポストエフェクト・GUI) の GPU の時間 */
    @Inject(method = "render", at = @At("HEAD"))
    private void iem$beginFrame(CallbackInfo ci) {
        PassTimer.beginFrame();
        this.iem$frameTimer = PassTimer.beginOutside("frame");
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void iem$endFrame(CallbackInfo ci) {
        PassTimer.endOutside(this.iem$frameTimer);
    }

    /** /iemgpu: GUI (HUD・画面) の GPU の時間 */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void iem$beginGui(CallbackInfo ci) {
        this.iem$guiTimer = PassTimer.beginOutside("gui");
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V", shift = At.Shift.AFTER))
    private void iem$endGui(CallbackInfo ci) {
        PassTimer.endOutside(this.iem$guiTimer);
    }

    /** /iemgpu: ワールドの描画全体 (地形・エンティティ・空など。ポストエフェクトの前) の GPU の時間。この間はパイプラインごとにも測る */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void iem$beginWorld(CallbackInfo ci) {
        this.iem$worldTimer = PassTimer.beginWorld();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void iem$endWorld(CallbackInfo ci) {
        PassTimer.endWorld(this.iem$worldTimer);
    }

    /** 投影は揺れ・視野の変化を掛けた後 (ワールドを描くのに使う投影) */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/ProjectionType;)V",
            ordinal = 0))
    private void iem$captureFrame(CallbackInfo ci, @Local Matrix4f projectionMatrix) {
        FrameValues.capture(this.gameRenderState.levelRenderState, projectionMatrix);
    }

    /** ポストエフェクトの前に mod の値をバッファへ書く。CPU のプロファイラー (F3+L) には "postEffects" の名前を付ける (バニラは名前を付けていない) */
    @WrapMethod(method = "applyPostEffects")
    private void iem$postEffects(Operation<Void> original) {
        Profiler.get().push("postEffects");
        try {
            FrameValues.upload();
            original.call();
        } finally {
            Profiler.get().pop();
        }
    }

    /** CPU のプロファイラーの名前: ポストエフェクトのチェーンを読み込む・入れ替える所 */
    @WrapMethod(method = "preparePostEffects")
    private void iem$preparePostEffects(List<Identifier> requested, Operation<Void> original) {
        Profiler.get().push("preparePostEffects");
        try {
            original.call(requested);
        } finally {
            Profiler.get().pop();
        }
    }

    /** CPU のプロファイラーの名前: エンティティの輪郭 (発光) を画面に重ねる所 */
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;blitEntityOutline()V"))
    private void iem$entityOutline(LevelRenderer renderer, Operation<Void> original) {
        Profiler.get().push("entityOutline");
        try {
            original.call(renderer);
        } finally {
            Profiler.get().pop();
        }
    }
}
