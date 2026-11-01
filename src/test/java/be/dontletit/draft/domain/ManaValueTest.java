package be.dontletit.draft.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ManaValueTest {

    @Test
    void genericAndColouredSymbolsAddUp() {
        assertEquals(4, ManaValue.of("{3}{G}"));
        assertEquals(3, ManaValue.of("{1}{U}{U}"));
        assertEquals(1, ManaValue.of("{C}"));
        assertEquals(1, ManaValue.of("{S}"));
        assertEquals(15, ManaValue.of("{15}"));
    }

    @Test
    void xCountsAsZero() {
        assertEquals(1, ManaValue.of("{X}{R}"));
        assertEquals(2, ManaValue.of("{X}{X}{G}{G}"));
    }

    @Test
    void hybridAndPhyrexianCountOne_twoGenericHybridCountsTwo() {
        assertEquals(2, ManaValue.of("{W/U}{W/U}"));
        assertEquals(1, ManaValue.of("{G/P}"));
        assertEquals(6, ManaValue.of("{2/W}{2/W}{2/W}"));
    }

    @Test
    void noCost_isZero() {
        assertEquals(0, ManaValue.of(null));
        assertEquals(0, ManaValue.of(""));
    }

    @Test
    void aCardWithoutAKnownCost_hasNoManaValue() {
        assertNull(new DraftCard("Old event card", "https://img/x.jpg").manaValue());
        assertEquals(3, new DraftCard("Brazen Borrower", "https://img/b.jpg", 3).manaValue());
    }
}
