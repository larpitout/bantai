package com.bantai.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GabayParserTest {

    private val labels = listOf("Camera", "Gallery", "Send", "Voice call", "Video call")

    @Test
    fun readsButtonNamesOnePerLine() {
        assertEquals(listOf("Gallery", "Send"), GabayParser.parse("Gallery\nSend", labels))
    }

    @Test
    fun readsNumberedQuotedAndCommaSeparatedNames() {
        assertEquals(listOf("Gallery", "Send"), GabayParser.parse("1. \"Gallery\"\n2) **Send**.", labels))
        assertEquals(listOf("Voice call", "Send"), GabayParser.parse("Voice call, Send", labels))
        assertEquals(listOf("Camera"), GabayParser.parse("- “camera”", labels))
    }

    @Test
    fun spellsButtonsAsOnTheScreen() {
        assertEquals(listOf("Voice call"), GabayParser.parse("VOICE  CALL", labels))
    }

    @Test
    fun findsAButtonInsideALongerLine() {
        assertEquals(listOf("Voice call"), GabayParser.parse("Tap Voice call", labels))
    }

    @Test
    fun keepsAtMostThreeAndNoRepeats() {
        assertEquals(
            listOf("Camera", "Gallery", "Send"),
            GabayParser.parse("Camera\nCamera\nGallery\nSend\nVoice call", labels),
        )
    }

    @Test
    fun dropsNamesThatAreNotOnTheScreen() {
        assertEquals(listOf("Gallery", "Send"), GabayParser.parse("Gallery\nAttach\nSend", labels))
    }

    @Test
    fun replyWithoutAKnownButtonIsEmpty() {
        assertTrue(GabayParser.parse("Hindi ko po alam kung paano.", labels).isEmpty())
        assertTrue(GabayParser.parse("", labels).isEmpty())
        assertTrue(GabayParser.parse("Attach\nShare", labels).isEmpty())
    }
}
