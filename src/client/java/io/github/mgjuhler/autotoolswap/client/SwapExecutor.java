package io.github.mgjuhler.autotoolswap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

public final class SwapExecutor {
	private static final int OFFHAND_SWAP_BUTTON = 40; // vanilla: SWAP med knap 40 = offhand

	private SwapExecutor() {}

	/** Oversætter Inventory-indeks (0-8 hotbar, 9-35 resten) til InventoryMenu-slot-id. */
	public static int menuSlot(int inventorySlot) {
		return inventorySlot < 9
			? InventoryMenu.USE_ROW_SLOT_START + inventorySlot
			: InventoryMenu.INV_SLOT_START + (inventorySlot - 9);
	}

	public static void swapIntoMainHand(int fromInventorySlot) {
		Minecraft mc = Minecraft.getInstance();
		var inv = mc.player.getInventory();
		if (inv.isHotbarSlot(fromInventorySlot)) {
			inv.setSelectedSlot(fromInventorySlot);
			return;
		}
		mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
			menuSlot(fromInventorySlot), inv.getSelectedSlot(), ContainerInput.SWAP, mc.player);
	}

	public static void swapIntoOffhand(int fromInventorySlot) {
		Minecraft mc = Minecraft.getInstance();
		mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
			menuSlot(fromInventorySlot), OFFHAND_SWAP_BUTTON, ContainerInput.SWAP, mc.player);
	}

	public static void throwSlot(int inventorySlot) {
		Minecraft mc = Minecraft.getInstance();
		mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,
			menuSlot(inventorySlot), 1, ContainerInput.THROW, mc.player); // knap 1 = hele stakken
	}

	/** Vælger en hotbar-plads uden sårbart item. Returnerer false hvis ingen findes. */
	public static boolean selectSafeSlot() {
		Minecraft mc = Minecraft.getInstance();
		var inv = mc.player.getInventory();
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || !s.isDamageableItem()) {
				inv.setSelectedSlot(i);
				return true;
			}
		}
		return false;
	}
}
