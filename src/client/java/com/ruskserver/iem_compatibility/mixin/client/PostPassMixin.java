package com.ruskserver.iem_compatibility.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.ruskserver.iem_compatibility.client.FrameValues;
import com.ruskserver.iem_compatibility.client.PassTimer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/**
 * ポストパスを描く直前: uniform IemFrameValues を持つパス (JSON の既定値) には mod の値を渡し直す。
 * /iemgpu を付けている間は描画の前後に GPU のタイムスタンプを書く
 */
@Mixin(PostPass.class)
abstract class PostPassMixin {
    @Shadow
    @Final
    private String name;

    @Shadow
    @Final
    private Map<String, GpuBuffer> customUniforms;

    @Shadow
    @Final
    private RenderPipeline pipeline;

    @WrapOperation(method = "lambda$addToFrame$1", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/RenderPass;draw(IIII)V"))
    private void iem$draw(RenderPass pass, int vertexCount, int instanceCount, int firstVertex, int firstInstance, Operation<Void> original) {
        if (this.customUniforms.containsKey(FrameValues.NAME)) FrameValues.bind(pass);
        int timer = PassTimer.enabled() ? PassTimer.begin(pass, iem$label()) : -1;
        original.call(pass, vertexCount, instanceCount, firstVertex, firstInstance);
        PassTimer.end(pass, timer);
    }

    /** 表示の名前: パスの名前 (効果/番号) と画素のシェーダーの名前 (例: iem:grade/11 light) */
    private String iem$label() {
        String fragment = this.pipeline.getShaders().get(ShaderType.FRAGMENT).getPath();
        return this.name + " " + fragment.substring(fragment.lastIndexOf('/') + 1);
    }
}
