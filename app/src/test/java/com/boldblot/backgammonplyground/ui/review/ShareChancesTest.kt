package com.boldblot.backgammonplyground.ui.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareChancesTest {
    private val probs = listOf(0.6f, 0.2f, 0.01f, 0.1f, 0.005f)

    @Test fun chances_text_of_the_roller() {
        assertEquals("Win 60.0% (G 20.0% B 1.0%) · Lose 40.0% (G 10.0% B 0.5%)", PositionShare.chancesText(probs))
    }

    @Test fun chances_text_of_the_taker_is_the_mirror() {
        assertEquals("Win 40.0% (G 10.0% B 0.5%) · Lose 60.0% (G 20.0% B 1.0%)", PositionShare.chancesText(probs, swap = true))
    }

    @Test fun no_chances_without_five_numbers() {
        assertNull(PositionShare.chancesText(emptyList()))
    }
}
