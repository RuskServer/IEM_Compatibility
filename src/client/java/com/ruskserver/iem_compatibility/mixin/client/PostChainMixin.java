package com.ruskserver.iem_compatibility.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** CPU のプロファイラー (F3+L) に、ポストエフェクトのチェーンごとの名前 (例: iem:grade_visor) を付ける */
@Mixin(PostChain.class)
abstract class PostChainMixin {
    @Shadow
    public abstract Identifier id();

    @WrapMethod(method = "process")
    private void iem$profile(RenderTarget mainTarget, GraphicsResourceAllocator resourceAllocator, Operation<Void> original) {
        Profiler.get().push(this.id().toString());
        try {
            original.call(mainTarget, resourceAllocator);
        } finally {
            Profiler.get().pop();
        }
    }
}
