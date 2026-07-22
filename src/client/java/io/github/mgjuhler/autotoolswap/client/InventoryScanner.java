package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.Candidate;
import io.github.mgjuhler.autotoolswap.core.ItemCategory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.ArrayList;
import java.util.List;

public final class InventoryScanner {
	public static final int MAIN_INVENTORY_SIZE = 36; // hotbar 0-8 + resten 9-35
	public static final int SHULKER_SIZE = 27;

	private InventoryScanner() {}

	public static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	public static List<Candidate> scan(LocalPlayer player, boolean includeShulkers, int excludeInventorySlot) {
		List<Candidate> out = new ArrayList<>();
		var inv = player.getInventory();
		for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
			if (slot == excludeInventorySlot) continue;
			ItemStack stack = inv.getItem(slot);
			if (stack.isEmpty()) continue;
			if (stack.isDamageableItem()) {
				out.add(new Candidate(itemId(stack), ItemClassifier.classify(stack),
					stack.getDamageValue(), stack.getMaxDamage(),
					Candidate.Location.INVENTORY, slot, -1));
			}
			if (includeShulkers && stack.has(DataComponents.CONTAINER)) {
				ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
				NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
				contents.copyInto(items);
				for (int boxSlot = 0; boxSlot < items.size(); boxSlot++) {
					ItemStack inner = items.get(boxSlot);
					if (inner.isEmpty() || !inner.isDamageableItem()) continue;
					out.add(new Candidate(itemId(inner), ItemClassifier.classify(inner),
						inner.getDamageValue(), inner.getMaxDamage(),
						Candidate.Location.SHULKER, slot, boxSlot));
				}
			}
		}
		return out;
	}
}
