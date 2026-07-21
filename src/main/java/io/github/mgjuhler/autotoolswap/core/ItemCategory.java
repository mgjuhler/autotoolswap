package io.github.mgjuhler.autotoolswap.core;

public enum ItemCategory {
	PICKAXE, AXE, SHOVEL, HOE, SWORD, BOW, CROSSBOW, TRIDENT, MACE, SHIELD, SHEARS, FISHING_ROD, OTHER;

	public boolean allowsCategoryFallback() {
		return switch (this) {
			case PICKAXE, AXE, SHOVEL, HOE, SWORD -> true;
			default -> false;
		};
	}
}
