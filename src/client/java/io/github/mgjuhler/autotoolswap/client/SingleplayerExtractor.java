package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.Candidate;
import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import io.github.mgjuhler.autotoolswap.core.config.OldItemAction;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.UUID;

/** Task 9: singleplayer full-auto shulker extraction via direct server-side access. */
public final class SingleplayerExtractor {
	private SingleplayerExtractor() {}

	public static void extract(Minecraft mc, AutoToolSwapConfig cfg,
	                           int wornSlot, boolean mainHand, Candidate c) {
		var server = mc.getSingleplayerServer();
		if (server == null) return;
		UUID uuid = mc.player.getUUID();
		OldItemAction action = cfg.oldItemAction;
		int shulkerSlot = c.inventorySlot();
		int boxSlot = c.slotInShulker();
		int targetSlot = mainHand ? wornSlot : Inventory.SLOT_OFFHAND;
		String expectedReplacementId = c.itemId();
		String expectedWornId = InventoryScanner.itemId(mc.player.getInventory().getItem(targetSlot));

		server.execute(() -> {
			ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
			if (sp == null) return;
			Inventory inv = sp.getInventory();
			ItemStack shulker = inv.getItem(shulkerSlot);
			if (!shulker.has(DataComponents.CONTAINER)) return;

			NonNullList<ItemStack> items = NonNullList.withSize(InventoryScanner.SHULKER_SIZE, ItemStack.EMPTY);
			shulker.get(DataComponents.CONTAINER).copyInto(items);
			ItemStack replacement = items.get(boxSlot);
			if (replacement.isEmpty()) return; // indholdet har ændret sig — opgiv stille
			if (!InventoryScanner.itemId(replacement).equals(expectedReplacementId)) return; // indholdet er skiftet ud — opgiv stille

			ItemStack worn = inv.getItem(targetSlot);
			if (!InventoryScanner.itemId(worn).equals(expectedWornId)) return; // det slidte item er ikke længere det forventede — opgiv stille før mutation
			items.set(boxSlot, action == OldItemAction.STORE_IN_SHULKER ? worn : ItemStack.EMPTY);
			shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
			inv.setItem(targetSlot, replacement);
			switch (action) {
				case DROP -> sp.drop(worn, true, Prediction.SERVER_ONLY);
				case KEEP -> {
					if (!sp.getInventory().add(worn)) sp.drop(worn, true, Prediction.SERVER_ONLY);
				}
				case STORE_IN_SHULKER -> {} // allerede lagt i boksen
			}
		});
		Notifier.actionBar("autotoolswap.swapped_from_shulker",
			mc.player.getInventory().getItem(wornSlot).getItemName());
	}
}
