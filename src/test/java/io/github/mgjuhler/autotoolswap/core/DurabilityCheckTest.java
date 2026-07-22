package io.github.mgjuhler.autotoolswap.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DurabilityCheckTest {
	@Test void fullDurabilityIsNotLow() {
		assertFalse(DurabilityCheck.isLow(0, 1561, 10));
	}
	@Test void belowTenPercentIsLow() {
		// 1561 max, 150 tilbage = 9.6 %
		assertTrue(DurabilityCheck.isLow(1561 - 150, 1561, 10));
	}
	@Test void exactBoundaryIsLow() {
		// 100 max, præcis 10 tilbage
		assertTrue(DurabilityCheck.isLow(90, 100, 10));
	}
	@Test void justAboveBoundaryIsNotLow() {
		assertFalse(DurabilityCheck.isLow(89, 100, 10));
	}
	@Test void nonDamageableIsNeverLow() {
		assertFalse(DurabilityCheck.isLow(0, 0, 10));
	}
}
