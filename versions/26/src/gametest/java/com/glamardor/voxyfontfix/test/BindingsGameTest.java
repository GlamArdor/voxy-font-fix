package com.glamardor.voxyfontfix.test;

import com.glamardor.voxyfontfix.GlStateGuard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Walks into a world with Voxy running, keeps the font baking new glyphs while Voxy works, and writes
 * down what the binding audit saw. Run once normally and once with {@code -Ppassive}: with the fix
 * switched off the audit shows whether Voxy still leaves GlStateManager's cache stale.
 */
public final class BindingsGameTest implements FabricClientGameTest {
	// Glyphs that are baked lazily, the first time they are drawn: Cyrillic, Greek, CJK.
	private static final String[] LINES = {
			"Съешь же ещё этих мягких французских булок, да выпей чаю",
			"Широкая электрификация южных губерний даст мощный толчок подъёму",
			"Ξεσκεπάζω την ψυχοφθόρα βδελυγμία",
			"いろはにほへと ちりぬるを わかよたれそ つねならむ",
			"В чащах юга жил бы цитрус? Да, но фальшивый экземпляр!",
			"天地玄黄 宇宙洪荒 日月盈昃 辰宿列张",
	};

	private static String name(String what) {
		return "voxyfontfix_" + (GlStateGuard.passive() ? "passive_" : "fixed_") + what;
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		List<String> out = new ArrayList<>();
		out.add("passive=" + GlStateGuard.passive());
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("gamemode creative @a");
			server.runCommand("effect give @a minecraft:slow_falling infinite 0 true");
			server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword");
			server.runCommand("item replace entity @a hotbar.1 with minecraft:grass_block");
			server.runCommand("item replace entity @a hotbar.2 with minecraft:golden_apple");
			server.runCommand("item replace entity @a hotbar.3 with minecraft:oak_sapling");
			server.runCommand("item replace entity @a hotbar.4 with minecraft:redstone");
			server.runCommand("item replace entity @a hotbar.5 with minecraft:water_bucket");
			server.runCommand("tp @a 0 200 0 45 30");

			// Let Voxy start, ingest and bake, and feed the font new glyphs the whole time.
			for (int round = 0; round < 12; round++) {
				server.runCommand("say " + LINES[round % LINES.length]);
				ctx.waitTicks(40);
				out.add("t=" + (round + 1) * 2 + "s audits=" + GlStateGuard.audits() + " desyncs=" + GlStateGuard.desyncs()
						+ " staleBinds=" + GlStateGuard.misbinds());
				if (round == 5) {
					ctx.takeScreenshot(name("before_reload"));
					// F3+T: the original report came right after a resource reload, which throws away
					// every baked glyph and makes Voxy rebake its models at the same time.
					ctx.runOnClient(mc -> mc.reloadResourcePacks());
					ctx.waitTicks(100);
					out.add("reloaded: staleBinds=" + GlStateGuard.misbinds());
				}
			}
			ctx.takeScreenshot(name("after_reload"));
			ctx.setScreen(() -> new InventoryScreen(ctx.computeOnClient(mc -> mc.player)));
			ctx.waitTicks(20);
			ctx.takeScreenshot(name("inventory"));
			ctx.setScreen(() -> null);
			out.add("end: audits=" + GlStateGuard.audits() + " desyncs=" + GlStateGuard.desyncs()
					+ " staleBinds=" + GlStateGuard.misbinds());
			out.add("last report: " + GlStateGuard.lastReport());
		}

		Path file = FabricLoader.getInstance().getGameDir().resolve("voxyfontfix-test-" + (GlStateGuard.passive() ? "passive" : "fixed") + ".txt");
		try {
			Files.write(file, out, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
