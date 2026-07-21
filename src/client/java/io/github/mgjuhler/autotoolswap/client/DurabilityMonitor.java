package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.Candidate;
import io.github.mgjuhler.autotoolswap.core.DurabilityCheck;
import io.github.mgjuhler.autotoolswap.core.ItemCategory;
import io.github.mgjuhler.autotoolswap.core.ReplacementSelector;
import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import io.github.mgjuhler.autotoolswap.core.config.OldItemAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class DurabilityMonitor {
	/** Slots (inventory-indeks, -1 = offhand) der allerede er håndteret; ryddes når indholdet ændrer sig. */
	private final Set<String> handled = new HashSet<>();

	public void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		AutoToolSwapConfig cfg = AutoToolSwapClient.config();
		if (player == null || !cfg.enabled) return;
		if (player.isCreative() || player.isSpectator()) return;
		if (mc.screen != null) return; // rør ikke inventory mens en skærm er åben

		int selected = player.getInventory().getSelectedSlot();
		check(mc, player, cfg, player.getInventory().getSelectedItem(), selected, true);
		if (cfg.monitorOffhand) {
			check(mc, player, cfg, player.getInventory().getItem(Inventory.SLOT_OFFHAND), Inventory.SLOT_OFFHAND, false);
		}
	}

	private void check(Minecraft mc, LocalPlayer player, AutoToolSwapConfig cfg,
	                   ItemStack stack, int slot, boolean mainHand) {
		String key = slot + ":" + InventoryScanner.itemId(stack);
		if (stack.isEmpty() || !stack.isDamageableItem() || stack.has(DataComponents.UNBREAKABLE)) {
			handled.removeIf(k -> k.startsWith(slot + ":"));
			return;
		}
		if (!DurabilityCheck.isLow(stack.getDamageValue(), stack.getMaxDamage(), cfg.thresholdPercent)) {
			handled.removeIf(k -> k.startsWith(slot + ":"));
			return;
		}
		if (handled.contains(key)) return;

		ItemCategory category = ItemClassifier.classify(stack);
		if (!ItemClassifier.isMonitored(category, cfg)) return;
		handled.add(key);

		String wornId = InventoryScanner.itemId(stack);
		var candidates = InventoryScanner.scan(player, cfg.searchShulkers, mainHand ? slot : -2);
		Optional<Candidate> best = ReplacementSelector.selectBest(wornId, category, cfg.thresholdPercent, candidates);

		if (best.isEmpty()) {
			if (mainHand && SwapExecutor.selectSafeSlot()) {
				Notifier.actionBar("autotoolswap.no_replacement", stack.getItemName());
			} else {
				Notifier.chat("autotoolswap.no_replacement", stack.getItemName());
			}
			return;
		}

		Candidate c = best.get();
		if (c.location() == Candidate.Location.INVENTORY) {
			int wornEndsUpIn = swapFromInventory(player, c, slot, mainHand);
			Notifier.actionBar("autotoolswap.swapped", stack.getItemName());
			if (cfg.oldItemAction == OldItemAction.DROP && wornEndsUpIn >= 0) {
				SwapExecutor.throwSlot(wornEndsUpIn);
				Notifier.chat("autotoolswap.dropped_old", stack.getItemName());
			}
			// STORE_IN_SHULKER for inventory-fund håndteres i Task 8 (pending store)
		} else {
			// Shulker-fund: Task 8 (semi-auto) og Task 9 (singleplayer fuld-auto)
			ShulkerFlow.onShulkerCandidate(mc, cfg, stack, slot, mainHand, c);
		}
	}

	/** Udfører byttet; returnerer inventory-slot hvor det slidte item lander (-1 = ukendt). */
	private int swapFromInventory(LocalPlayer player, Candidate c, int wornSlot, boolean mainHand) {
		if (mainHand) {
			if (player.getInventory().isHotbarSlot(c.inventorySlot())) {
				SwapExecutor.swapIntoMainHand(c.inventorySlot()); // rent slot-valg, intet flyttes
				return wornSlot;
			}
			SwapExecutor.swapIntoMainHand(c.inventorySlot());
			return c.inventorySlot(); // SWAP-klik: det slidte item lander i erstatningens gamle slot
		}
		SwapExecutor.swapIntoOffhand(c.inventorySlot());
		return c.inventorySlot();
	}
}
