package com.glamardor.voxyfontfix.mixin;

import com.glamardor.voxyfontfix.GlStateGuard;
import com.mojang.blaze3d.textures.GpuTexture;
import java.nio.IntBuffer;
import net.minecraft.client.gl.GlCommandEncoder;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Both {@code writeToTexture} overloads bind their destination through
 * {@code GlStateManager._bindTexture}, which silently does nothing when the cache is stale. This is
 * where glyphs are written, so this is where the damage happens.
 */
@Mixin(GlCommandEncoder.class)
public class GlCommandEncoderMixin {
	@Inject(
			method = "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lnet/minecraft/client/texture/NativeImage;IIIIIIII)V",
			at = @At("HEAD")
	)
	private void voxyfontfix$pinImageUploadTarget(
			GpuTexture texture,
			NativeImage image,
			int mipLevel,
			int depthOrLayer,
			int destX,
			int destY,
			int width,
			int height,
			int srcX,
			int srcY,
			CallbackInfo ci) {
		GlStateGuard.enforceUploadTarget(texture);
	}

	@Inject(
			method = "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Ljava/nio/IntBuffer;Lnet/minecraft/client/texture/NativeImage$Format;IIIIII)V",
			at = @At("HEAD")
	)
	private void voxyfontfix$pinBufferUploadTarget(
			GpuTexture texture,
			IntBuffer pixels,
			NativeImage.Format format,
			int mipLevel,
			int depthOrLayer,
			int destX,
			int destY,
			int width,
			int height,
			CallbackInfo ci) {
		GlStateGuard.enforceUploadTarget(texture);
	}
}
