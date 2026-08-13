package com.glamardor.voxyfontfix;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoxyFontFix implements ClientModInitializer {
	static final Logger LOGGER = LoggerFactory.getLogger("voxyfontfix");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Voxy Font Fix active: texture unit bindings will be kept in sync with GlStateManager.");
	}
}
