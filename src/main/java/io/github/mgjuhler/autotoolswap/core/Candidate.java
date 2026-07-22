package io.github.mgjuhler.autotoolswap.core;

public record Candidate(String itemId, ItemCategory category, int damageValue, int maxDamage,
                        Location location, int inventorySlot, int slotInShulker) {
	public enum Location { INVENTORY, SHULKER }

	public int remaining() {
		return maxDamage - damageValue;
	}
}
