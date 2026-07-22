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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DurabilityMonitor {
	/** Max antal forsøg pr. slot/item før vi giver op og går i stilhed. */
	private static final int MAX_ATTEMPTS = 2;

	/** Slots (inventory-indeks, -1 = offhand) der allerede er håndteret, med antal forsøg; ryddes når indholdet ændrer sig. */
	private final Map<String, Integer> handled = new HashMap<>();

	/** Rydder ét slots håndterede tilstand, så monitoren kan genvurdere netop det item (fx efter et shulker-bytte). */
	public void clearSlot(int slot) {
		handled.keySet().removeIf(k -> k.startsWith(slot + ":"));
	}

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
			handled.keySet().removeIf(k -> k.startsWith(slot + ":"));
			return;
		}
		if (!DurabilityCheck.isLow(stack.getDamageValue(), stack.getMaxDamage(), cfg.thresholdPercent)) {
			handled.keySet().removeIf(k -> k.startsWith(slot + ":"));
			return;
		}
		int priorAttempts = handled.getOrDefault(key, 0);
		if (priorAttempts >= MAX_ATTEMPTS) {
			// Opgivet — men sig det præcis én gang, ved overgangen, og først EFTER at
			// forsøgene reelt er brugt op (et vellykket skift når aldrig hertil, fordi
			// slottet så ikke længere holder et lavt item og nulstilles ovenfor).
			if (priorAttempts == MAX_ATTEMPTS) {
				handled.put(key, MAX_ATTEMPTS + 1);
				Notifier.chat("autotoolswap.gave_up", stack.getItemName());
			}
			return;
		}

		ItemCategory category = ItemClassifier.classify(stack);
		if (!ItemClassifier.isMonitored(category, cfg)) return;

		String wornId = InventoryScanner.itemId(stack);
		var candidates = InventoryScanner.scan(player, cfg.searchShulkers, mainHand ? slot : -2);
		Optional<Candidate> best = ReplacementSelector.selectBest(wornId, category, cfg.thresholdPercent, candidates);

		if (best.isPresent() && best.get().location() == Candidate.Location.SHULKER) {
			// Async sti: ShulkerFlow venter på at spilleren åbner en container, det kan tage
			// mange ticks. Den holder IKKE med i attempts-tælleren (som er til de umiddelbare
			// stier: inventory-bytte / ingen erstatning) — ellers udløser næste tick en falsk
			// gave_up mens byttet reelt bare venter på spilleren. Sæt tælleren direkte til
			// MAX_ATTEMPTS så vi ikke re-scanner hver tick; clearSlot() (kaldt fra ShulkerFlow
			// når byttet fuldføres) og de tidlige clears ovenfor (ikke længere lav/tomt) re-armer.
			if (!ShulkerFlow.isPending()) {
				ShulkerFlow.onShulkerCandidate(mc, cfg, stack, slot, mainHand, best.get()); // Task 8/9
			}
			// Parkér FORBI grænsen: shulker-stien har givet sin egen besked (in_shulker),
			// og gave_up-overgangen ved == MAX_ATTEMPTS må ikke fyre for den.
			handled.put(key, MAX_ATTEMPTS + 1);
			return;
		}

		handled.merge(key, 1, Integer::sum);

		if (best.isEmpty()) {
			if (mainHand && SwapExecutor.selectSafeSlot()) {
				Notifier.actionBar("autotoolswap.no_replacement", stack.getItemName());
			} else {
				Notifier.chat("autotoolswap.no_replacement", stack.getItemName());
			}
			return;
		}

		Candidate c = best.get();
		int wornEndsUpIn = swapFromInventory(player, c, slot, mainHand);
		Notifier.actionBar("autotoolswap.swapped", stack.getItemName());
		if (cfg.oldItemAction == OldItemAction.DROP && wornEndsUpIn >= 0) {
			SwapExecutor.throwSlot(wornEndsUpIn);
			Notifier.chat("autotoolswap.dropped_old", stack.getItemName());
		} else if (cfg.oldItemAction == OldItemAction.STORE_IN_SHULKER) {
			Notifier.chat("autotoolswap.store_manually", stack.getItemName());
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
