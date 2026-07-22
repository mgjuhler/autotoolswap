package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.Candidate;
import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import io.github.mgjuhler.autotoolswap.core.config.OldItemAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ShulkerFlow {
	private static String pendingItemId = null;     // itemId der ventes hentet fra en container
	private static int pendingHotbarSlot = -1;      // hotbar-slot byttet skal lande i
	private static boolean pendingStoreWorn = false;
	private static String pendingWornItemId = null; // itemId på det slidte item der ventes byttet ud

	private ShulkerFlow() {}

	/** Om et shulker-bytte allerede er planlagt og venter på at spilleren åbner en container. */
	public static boolean isPending() {
		return pendingItemId != null;
	}

	public static void onShulkerCandidate(Minecraft mc, AutoToolSwapConfig cfg,
	                                      ItemStack worn, int wornSlot, boolean mainHand, Candidate c) {
		if (cfg.singleplayerFullAuto && mc.getSingleplayerServer() != null) {
			SingleplayerExtractor.extract(mc, cfg, wornSlot, mainHand, c); // Task 9
			return;
		}
		if (pendingItemId != null) {
			// et bytte er allerede i gang — lad det først vinde, overskriv ikke ventetilstanden
			Notifier.chat("autotoolswap.in_shulker", worn.getItemName());
			return;
		}
		pendingItemId = c.itemId();
		pendingHotbarSlot = mainHand ? mc.player.getInventory().getSelectedSlot() : -1;
		pendingStoreWorn = cfg.oldItemAction == OldItemAction.STORE_IN_SHULKER;
		pendingWornItemId = InventoryScanner.itemId(worn);
		if (mainHand) SwapExecutor.selectSafeSlot(); // beskyt det slidte item indtil byttet
		Notifier.chat("autotoolswap.in_shulker", worn.getItemName());
	}

	/** Kaldes hvert tick. Udfører ventende bytte når en container-skærm med itemet er åben. */
	public static void tick(Minecraft mc) {
		if (mc.player == null) { clear(); return; }
		if (!AutoToolSwapClient.config().enabled || mc.player.isCreative() || mc.player.isSpectator()) { clear(); return; }
		if (pendingItemId == null) return;
		if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
		AbstractContainerMenu menu = screen.getMenu();
		if (menu == mc.player.inventoryMenu) return;
		if (!(menu instanceof net.minecraft.world.inventory.ChestMenu
				|| menu instanceof net.minecraft.world.inventory.ShulkerBoxMenu)) return;

		if (pendingHotbarSlot >= 0
				&& !InventoryScanner.itemId(mc.player.getInventory().getItem(pendingHotbarSlot)).equals(pendingWornItemId)) {
			// spilleren har lavet om på hotbaren siden byttet blev planlagt — den ventende tilstand er forældet
			clear();
			return;
		}

		int containerSlots = menu.slots.size() - 36; // sidste 36 slots er altid spillerens inventory
		for (int i = 0; i < containerSlots; i++) {
			Slot slot = menu.slots.get(i);
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || !InventoryScanner.itemId(stack).equals(pendingItemId)) continue;
			if (!slot.mayPickup(mc.player)) continue;

			if (pendingHotbarSlot >= 0 && pendingStoreWorn) {
				// ét SWAP-klik: erstatning ind i hotbaren, det slidte item ind i boksen
				mc.gameMode.handleContainerInput(menu.containerId, i, pendingHotbarSlot,
					ContainerInput.SWAP, mc.player);
				Notifier.chat("autotoolswap.stored_old", wornName());
			} else if (pendingHotbarSlot >= 0) {
				mc.gameMode.handleContainerInput(menu.containerId, i, pendingHotbarSlot,
					ContainerInput.SWAP, mc.player);
				// gamle item ligger nu i containeren — hent det tilbage til inventory hvis KEEP
				if (AutoToolSwapClient.config().oldItemAction == OldItemAction.KEEP) {
					mc.gameMode.handleContainerInput(menu.containerId, i, 0,
						ContainerInput.QUICK_MOVE, mc.player);
				} else if (AutoToolSwapClient.config().oldItemAction == OldItemAction.DROP) {
					mc.gameMode.handleContainerInput(menu.containerId, i, 1,
						ContainerInput.THROW, mc.player);
					Notifier.chat("autotoolswap.dropped_old", wornName());
				}
			} else {
				mc.gameMode.handleContainerInput(menu.containerId, i, 0,
					ContainerInput.QUICK_MOVE, mc.player);
			}
			if (pendingHotbarSlot >= 0) {
				mc.player.getInventory().setSelectedSlot(pendingHotbarSlot);
				AutoToolSwapClient.clearDebounceForSlot(pendingHotbarSlot);
			} else {
				AutoToolSwapClient.clearDebounceForSlot(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND);
			}
			Notifier.actionBar("autotoolswap.swapped_from_shulker", stack.getItemName());
			clear();
			return;
		}
	}

	/** Læsbart navn for det slidte item, udledt af det gemte itemId (fix: undgå at bruge erstatningens navn). */
	private static Component wornName() {
		var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(pendingWornItemId));
		return new ItemStack(item).getItemName();
	}

	public static void clear() {
		pendingItemId = null;
		pendingHotbarSlot = -1;
		pendingStoreWorn = false;
		pendingWornItemId = null;
	}
}
