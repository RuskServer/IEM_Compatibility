package com.ruskserver.iem_compatibility.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.ruskserver.iem_compatibility.client.FrameValues;
import com.ruskserver.iem_compatibility.client.LightData;
import com.ruskserver.iem_compatibility.client.PassTimer;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.util.profiling.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/**
 * ポストパス: サーバーの光が無い時は光の段 (light_grid・light) を描かない (grade はその時光の段を読まない。CPU の手間を減らす)。
 * CPU のプロファイラー (F3+L) にパスごとの名前を付ける。描く直前に、uniform IemFrameValues を持つパス (JSON の既定値) には mod の値を渡し直す。
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

    /** CPU のプロファイラーの名前 (例: light) と、サーバーの光が無い時に描かないパスか */
    @Unique
    private String iem$fragment;
    @Unique
    private boolean iem$lightPass;

    @WrapMethod(method = "lambda$addToFrame$1")
    private void iem$execute(ResourceHandle<?> output, GpuBufferSlice orthoMatrix, Map<?, ?> targets, Operation<Void> original) {
        if (this.iem$fragment == null) {
            String path = this.pipeline.getShaders().get(ShaderType.FRAGMENT).getPath();
            this.iem$fragment = path.substring(path.lastIndexOf('/') + 1);
            this.iem$lightPass = this.iem$fragment.equals("light_grid") || this.iem$fragment.equals("light");
        }
        if (this.iem$lightPass && !LightData.active()) return;
        Profiler.get().push(this.iem$fragment);
        try {
            original.call(output, orthoMatrix, targets);
        } finally {
            Profiler.get().pop();
        }
    }

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
        return this.name + " " + this.iem$fragment;
    }
}
