package com.glamardor.voxyfontfix.mixin;

import com.glamardor.voxyfontfix.GlStateGuard;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two sync points per frame: one before anything is drawn, and one right after the world – which is
 * where Voxy renders – so the HUD and the chat start from a state the game actually agrees with.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void voxyfontfix$resyncAtFrameStart(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
		GlStateGuard.resyncTextureUnits();
	}

	@Inject(method = "renderWorld", at = @At("RETURN"))
	private void voxyfontfix$resyncAfterWorld(RenderTickCounter tickCounter, CallbackInfo ci) {
		GlStateGuard.auditBindings("after world");
		GlStateGuard.resyncTextureUnits();
	}
}
