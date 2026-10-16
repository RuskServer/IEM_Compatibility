package com.ruskserver.iem_compatibility.mixin.client;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import com.ruskserver.iem_compatibility.client.PassTimer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * /iemgpu: ワールドを描いている間、レンダーパスでパイプライン (コアシェーダー) を切り替えるたびと閉じる時に GPU のタイムスタンプを書き、
 * パイプラインごとの時間を測る (OpenGL・Vulkan 共通の表側の実装)
 */
@Mixin(FrontendRenderPass.class)
abstract class FrontendRenderPassMixin {
    @Unique
    private int iem$pipelineTimer = -1;

    @Inject(method = "setPipeline", at = @At("TAIL"))
    private void iem$switchPipeline(CompiledRenderPipeline pipeline, CallbackInfo ci) {
        if (!PassTimer.enabled()) return;
        String name = pipeline instanceof FrontendRenderPipeline frontend ? frontend.name() : "?";
        this.iem$pipelineTimer = PassTimer.switchPipeline((RenderPass) (Object) this, this.iem$pipelineTimer, name);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void iem$closePipeline(CallbackInfo ci) {
        PassTimer.closePipeline((RenderPass) (Object) this, this.iem$pipelineTimer);
        this.iem$pipelineTimer = -1;
    }
}
