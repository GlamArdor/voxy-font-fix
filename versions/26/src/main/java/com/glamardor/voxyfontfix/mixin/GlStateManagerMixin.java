package com.glamardor.voxyfontfix.mixin;

import com.glamardor.voxyfontfix.GlStateGuard;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Closes the blind spot in GlStateManager's binding cache.
 *
 * <p>Both {@code _activeTexture} and {@code _bindTexture} compare their argument against a cached
 * value and skip the driver call when the two agree. That is sound as long as every binding goes
 * through GlStateManager – but Voxy binds through {@code glBindTextureUnit} and {@code glBindSampler},
 * which the cache never sees. From then on the cache describes a state the driver does not have, and
 * vanilla's own binds turn into no-ops precisely when they are needed.
 *
 * <p>Both hooks run before the original and simply issue the call the original might skip. The
 * original still updates the cache exactly as before, so nothing downstream can tell the difference
 * apart from the bindings actually being correct.
 */
@Mixin(GlStateManager.class)
public class GlStateManagerMixin {
	@Inject(method = "_activeTexture(I)V", at = @At("HEAD"))
	private static void voxyfontfix$forceActiveTexture(int texture, CallbackInfo ci) {
		GlStateGuard.enforceActiveUnit(texture);
	}

	@Inject(method = "_bindTexture(I)V", at = @At("HEAD"))
	private static void voxyfontfix$forceBindTexture(int texture, CallbackInfo ci) {
		GlStateGuard.auditSkippedBind(texture);
		GlStateGuard.enforceBind(texture);
	}
}
