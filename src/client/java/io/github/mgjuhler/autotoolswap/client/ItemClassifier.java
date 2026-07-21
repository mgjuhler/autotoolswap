package io.github.mgjuhler.autotoolswap.client;

import io.github.mgjuhler.autotoolswap.core.ItemCategory;
import io.github.mgjuhler.autotoolswap.core.config.AutoToolSwapConfig;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ItemClassifier {
	private ItemClassifier() {}

	public static ItemCategory classify(ItemStack stack) {
		if (stack.is(ItemTags.PICKAXES)) return ItemCategory.PICKAXE;
		if (stack.is(ItemTags.AXES)) return ItemCategory.AXE;
		if (stack.is(ItemTags.SHOVELS)) return ItemCategory.SHOVEL;
		if (stack.is(ItemTags.HOES)) return ItemCategory.HOE;
		if (stack.is(ItemTags.SWORDS)) return ItemCategory.SWORD;
		if (stack.is(Items.BOW)) return ItemCategory.BOW;
		if (stack.is(Items.CROSSBOW)) return ItemCategory.CROSSBOW;
		if (stack.is(Items.TRIDENT)) return ItemCategory.TRIDENT;
		if (stack.is(Items.MACE)) return ItemCategory.MACE;
		if (stack.is(Items.SHIELD)) return ItemCategory.SHIELD;
		if (stack.is(Items.SHEARS)) return ItemCategory.SHEARS;
		if (stack.is(Items.FISHING_ROD)) return ItemCategory.FISHING_ROD;
		return ItemCategory.OTHER;
	}

	public static boolean isMonitored(ItemCategory category, AutoToolSwapConfig cfg) {
		return switch (category) {
			case PICKAXE, AXE, SHOVEL, HOE, FISHING_ROD -> cfg.monitorTools;
			case SWORD, TRIDENT, MACE -> cfg.monitorWeapons;
			case BOW, CROSSBOW -> cfg.monitorRanged;
			case SHIELD -> cfg.monitorShield;
			case SHEARS -> cfg.monitorShears;
			case OTHER -> false;
		};
	}
}
