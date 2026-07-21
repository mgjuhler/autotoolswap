package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.Candidate;
import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public final class ShulkerFlow {
	private ShulkerFlow() {}

	public static void onShulkerCandidate(Minecraft mc, AutoToolSwapConfig cfg,
	                                      ItemStack worn, int wornSlot, boolean mainHand, Candidate c) {
		Notifier.chat("autotoolswap.in_shulker", worn.getItemName());
	}
}
