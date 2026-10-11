package com.aspects.smp.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminCommandTest {

    @Test
    void clampGiveAmount_returnsRequestedAmount_whenInRange() {
        assertEquals(5, AdminCommand.clampGiveAmount(5));
    }

    @Test
    void clampGiveAmount_clampsAboveSixtyFour_toSixtyFour() {
        assertEquals(64, AdminCommand.clampGiveAmount(1000));
    }

    @Test
    void clampGiveAmount_allowsExactlySixtyFour() {
        assertEquals(64, AdminCommand.clampGiveAmount(64));
    }

    @Test
    void clampGiveAmount_allowsExactlyOne() {
        assertEquals(1, AdminCommand.clampGiveAmount(1));
    }
}
