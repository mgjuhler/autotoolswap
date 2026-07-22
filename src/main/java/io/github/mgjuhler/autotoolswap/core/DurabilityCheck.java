package io.github.mgjuhler.autotoolswap.core;

public final class DurabilityCheck {
	private DurabilityCheck() {}

	public static boolean isLow(int damageValue, int maxDamage, int thresholdPercent) {
		if (maxDamage <= 0) return false;
		int remaining = maxDamage - damageValue;
		return remaining * 100L <= (long) maxDamage * thresholdPercent;
	}
}
