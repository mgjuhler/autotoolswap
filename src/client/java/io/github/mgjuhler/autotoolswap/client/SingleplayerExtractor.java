package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.Candidate;
import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import net.minecraft.client.Minecraft;

/** Stub for Task 9 (singleplayer full-auto shulker extraction via direct server-side access). */
public final class SingleplayerExtractor {
	private SingleplayerExtractor() {}

	// Task 9 fills in the body: reach into the integrated server's container/shulker
	// contents directly (no manual container-screen interaction needed in singleplayer).
	public static void extract(Minecraft mc, AutoToolSwapConfig cfg, int wornSlot, boolean mainHand, Candidate c) {
	}
}
