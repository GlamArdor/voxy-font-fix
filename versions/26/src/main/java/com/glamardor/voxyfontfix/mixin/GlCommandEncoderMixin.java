package com.glamardor.voxyfontfix.mixin;

import com.glamardor.voxyfontfix.GlStateGuard;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Both texture uploads bind their destination through {@code GlStateManager._bindTexture}, which
 * silently does nothing when the cache is stale. This is where glyphs are written, so this is where
 * the damage happens. On 26.x the two {@code writeToTexture} overloads of 1.21.8 became a single
 * {@code ByteBuffer} one, plus {@code copyBufferToTexture} for uploads that come from a GPU buffer.
 */
// Package-private on 26.x, so the target is named rather than imported.
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
public class GlCommandEncoderMixin {
	@Inject(
			method = "writeToTexture(Lcom/mojang/renderpearl/api/textures/GpuTexture;Ljava/nio/ByteBuffer;IIIIII)V",
			at = @At("HEAD")
	)
	private void voxyfontfix$pinUploadTarget(
			GpuTexture texture,
			ByteBuffer pixels,
			int mipLevel,
			int depthOrLayer,
			int destX,
			int destY,
			int width,
			int height,
			CallbackInfo ci) {
		GlStateGuard.enforceUploadTarget(texture);
	}

	@Inject(
			method = "copyBufferToTexture(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;IIIILcom/mojang/renderpearl/api/textures/GpuTexture;IIIIII)V",
			at = @At("HEAD")
	)
	private void voxyfontfix$pinCopyTarget(
			GpuBufferSlice source,
			int a,
			int b,
			int c,
			int d,
			GpuTexture texture,
			int e,
			int f,
			int g,
			int h,
			int i,
			int j,
			CallbackInfo ci) {
		GlStateGuard.enforceUploadTarget(texture);
	}
}
