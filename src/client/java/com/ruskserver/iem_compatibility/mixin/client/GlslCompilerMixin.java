package com.ruskserver.iem_compatibility.mixin.client;

import com.mojang.renderpearl.frontend.shaders.GlslCompiler;
import org.lwjgl.util.shaderc.Shaderc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * すべてのシェーダー (バニラ・Sodium・ポストエフェクト。どれもここでコンパイルされる) に IEM_MOD を定義する。
 * リソースパックの iem_control.glsl などは、これがあれば画面の左下の数値の画素・データの画素を書かず・守らず・避けない
 * (数値は IemFrameValues、サーバーの光はテクスチャで mod から受け取る。ワールドを描く段から discard が消えて Early-Z が効く)
 */
@Mixin(GlslCompiler.class)
abstract class GlslCompilerMixin {
    @Inject(method = "createBaseShaderOptions", at = @At("RETURN"))
    private void iem$defineMod(CallbackInfoReturnable<Long> cir) {
        long options = cir.getReturnValueJ();
        Shaderc.shaderc_compile_options_add_macro_definition(options, "IEM_MOD", "");
    }
}
