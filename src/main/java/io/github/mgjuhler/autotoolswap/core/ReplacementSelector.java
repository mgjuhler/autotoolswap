package io.github.mgjuhler.autotoolswap.core;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ReplacementSelector {
	private ReplacementSelector() {}

	public static Optional<Candidate> selectBest(String wornItemId, ItemCategory category,
	                                             int thresholdPercent, List<Candidate> candidates) {
		return candidates.stream()
			.filter(c -> !DurabilityCheck.isLow(c.damageValue(), c.maxDamage(), thresholdPercent))
			.filter(c -> matches(wornItemId, category, c))
			.min(Comparator
				.comparing((Candidate c) -> c.location() == Candidate.Location.SHULKER)
				.thenComparing(c -> !c.itemId().equals(wornItemId))
				.thenComparing(Comparator.comparingInt(Candidate::maxDamage).reversed())
				.thenComparing(Comparator.comparingInt(Candidate::remaining).reversed()));
	}

	private static boolean matches(String wornItemId, ItemCategory category, Candidate c) {
		if (c.itemId().equals(wornItemId)) return true;
		return category.allowsCategoryFallback() && c.category() == category;
	}
}
