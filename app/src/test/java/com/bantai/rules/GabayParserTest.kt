package com.bantai.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GabayParserTest {

    private val labels = listOf("Camera", "Gallery", "Send")

    @Test
    fun keepsNumberedStepsWithoutTheirNumbers() {
        val steps = GabayParser.parse(
            "1. Pindutin po ang \"Gallery\".\n2) Piliin po ang larawan.\n3: Pindutin po ang \"Send\".",
            labels,
        )

        assertEquals(
            listOf("Pindutin po ang \"Gallery\".", "Piliin po ang larawan.", "Pindutin po ang \"Send\"."),
            steps,
        )
    }

    @Test
    fun keepsOnlyTheFirstThreeSteps() {
        val raw = (1..5).joinToString("\n") { "$it. Hakbang $it po." }

        val steps = GabayParser.parse(raw, labels)

        assertEquals(listOf("Hakbang 1 po.", "Hakbang 2 po.", "Hakbang 3 po."), steps)
    }

    @Test
    fun dropsStepsThatNameAButtonNotOnTheScreen() {
        val steps = GabayParser.parse(
            "1. Pindutin po ang \"Gallery\".\n2. Pindutin po ang \"Attach\".\n3. Pindutin po ang \"Send\".",
            labels,
        )

        assertEquals(listOf("Pindutin po ang \"Gallery\".", "Pindutin po ang \"Send\"."), steps)
    }

    @Test
    fun matchesButtonNamesIgnoringCaseAndQuoteStyle() {
        val steps = GabayParser.parse("1. Pindutin po ang “send”.\n- Pindutin po ang \"CAMERA\".", labels)

        assertEquals(2, steps.size)
    }

    @Test
    fun addsPoWhenTheStepHasNone() {
        val steps = GabayParser.parse("1. Pindutin ang \"Send\".\n2. **Hintayin** itong maipadala!", labels)

        assertEquals(listOf("Pindutin ang \"Send\" po.", "Hintayin itong maipadala po."), steps)
    }

    @Test
    fun replyWithoutStepsIsEmpty() {
        assertTrue(GabayParser.parse("Hindi ko po alam kung paano.", labels).isEmpty())
        assertTrue(GabayParser.parse("", labels).isEmpty())
        assertTrue(GabayParser.parse("1. Pindutin po ang \"Attach\".", labels).isEmpty())
    }
}
