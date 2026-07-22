package io.github.mgjuhler.autotoolswap.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static io.github.mgjuhler.autotoolswap.core.Candidate.Location.*;
import static org.junit.jupiter.api.Assertions.*;

class ReplacementSelectorTest {
	private static Candidate inv(String id, ItemCategory cat, int dmg, int max, int slot) {
		return new Candidate(id, cat, dmg, max, INVENTORY, slot, -1);
	}
	private static Candidate shulker(String id, ItemCategory cat, int dmg, int max, int invSlot, int boxSlot) {
		return new Candidate(id, cat, dmg, max, SHULKER, invSlot, boxSlot);
	}

	@Test void prefersSameItemOverHigherTierFallback() {
		var iron = inv("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 0, 250, 10);
		var netherite = inv("minecraft:netherite_pickaxe", ItemCategory.PICKAXE, 0, 2031, 11);
		var best = ReplacementSelector.selectBest("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 10, List.of(netherite, iron));
		assertEquals("minecraft:iron_pickaxe", best.orElseThrow().itemId());
	}

	@Test void fallsBackToBestTierInCategory() {
		var stone = inv("minecraft:stone_pickaxe", ItemCategory.PICKAXE, 0, 131, 10);
		var iron = inv("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 0, 250, 11);
		var best = ReplacementSelector.selectBest("minecraft:netherite_pickaxe", ItemCategory.PICKAXE, 10, List.of(stone, iron));
		assertEquals("minecraft:iron_pickaxe", best.orElseThrow().itemId());
	}

	@Test void skipsCandidatesThatAreThemselvesLow() {
		var worn = inv("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 245, 250, 10);
		assertTrue(ReplacementSelector.selectBest("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 10, List.of(worn)).isEmpty());
	}

	@Test void prefersInventoryOverShulker() {
		var inShulker = shulker("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 0, 250, 20, 3);
		var inInv = inv("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 100, 250, 12);
		var best = ReplacementSelector.selectBest("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 10, List.of(inShulker, inInv));
		assertEquals(Candidate.Location.INVENTORY, best.orElseThrow().location());
	}

	@Test void bowNeverMatchesCrossbow() {
		var crossbow = inv("minecraft:crossbow", ItemCategory.CROSSBOW, 0, 465, 10);
		assertTrue(ReplacementSelector.selectBest("minecraft:bow", ItemCategory.BOW, 10, List.of(crossbow)).isEmpty());
	}

	@Test void tieOnTierPicksMostRemaining() {
		var a = inv("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 200, 250, 10);
		var b = inv("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 50, 250, 11);
		var best = ReplacementSelector.selectBest("minecraft:iron_pickaxe", ItemCategory.PICKAXE, 10, List.of(a, b));
		assertEquals(11, best.orElseThrow().inventorySlot());
	}
}
