package com.glamardor.voxyfontfix;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL33;

/**
 * Repairs the desync between what {@link GlStateManager} believes is bound and what is actually
 * bound in the GL context.
 *
 * <p>GlStateManager caches the bound texture per texture unit and turns {@code _bindTexture} into a
 * no-op whenever the cache already holds the requested id. Voxy binds its textures with
 * {@code glBindTextureUnit} and {@code glBindSampler} — direct state access calls that never pass
 * through GlStateManager — so after Voxy has baked its block models the cache is a lie: it claims
 * unit 0 still holds texture A while the driver actually holds the block atlas.
 *
 * <p>The next vanilla texture upload then skips its bind and {@code glTexSubImage2D} writes into
 * whatever Voxy left bound. For the glyph atlas that means the glyph never reaches its own texture
 * (the character renders as nothing) and its pixels end up smeared over another atlas (the coloured
 * garbage). Nothing throws, because from OpenGL's point of view every call was perfectly valid.
 */
public final class GlStateGuard {
	private GlStateGuard() {
	}

	/**
	 * Called right before vanilla uploads pixels into {@code texture}. Forces the real GL binding
	 * onto the upload target and then lets GlStateManager agree with it.
	 */
	public static void enforceUploadTarget(GpuTexture texture) {
		if (!(texture instanceof GlTexture glTexture) || !RenderSystem.isOnRenderThread()) {
			return;
		}

		// The active unit itself can be stale as well, so pin it back to what GlStateManager thinks.
		int activeUnit = GlStateManager._getActiveTexture();
		GL13.glActiveTexture(activeUnit);

		// Vanilla never uses sampler objects; a leftover one would override the atlas' own filter
		// and mip settings and make a mip-less texture sample as garbage.
		GL33.glBindSampler(activeUnit - GL13.GL_TEXTURE0, 0);

		// Cubemaps are bound straight to GL_TEXTURE_CUBE_MAP by vanilla, without the cache, so
		// there is nothing to repair – and binding one to GL_TEXTURE_2D is an error in itself.
		if ((texture.usage() & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0) {
			return;
		}

		int id = glTexture.getGlId();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);

		// Now make the cache agree. Either it already holds this id — in which case the binding we
		// just did is exactly what it assumed — or it rebinds the very same texture and records it.
		// Both paths end with cache and driver in agreement.
		GlStateManager._bindTexture(id);
	}

	/**
	 * Puts every texture unit back to the binding GlStateManager assumes it has. Cheap enough to run
	 * a couple of times per frame: twelve units, three calls each.
	 */
	public static void resyncTextureUnits() {
		if (!RenderSystem.isOnRenderThread()) {
			return;
		}

		GlStateManager.Texture2DState[] units = GlStateManager.TEXTURES;
		for (int unit = 0; unit < units.length; unit++) {
			int cached = units[unit].boundTexture;
			if (cached < 0) {
				// -1 means "a texture that used to live here was deleted". Nothing is bound then.
				cached = 0;
				units[unit].boundTexture = 0;
			}

			GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
			GL33.glBindSampler(unit, 0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, cached);
		}

		GL13.glActiveTexture(GlStateManager._getActiveTexture());
	}
}
