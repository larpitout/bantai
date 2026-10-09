package com.bantai.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class GabayRankerTest {

    // Totoong ayos ng mga button sa Home screen ng TECNO (mula itaas pababa).
    private val home = listOf(
        "AHA Games", "AI Gallery", "Palm Store", "Phone Master", "Folder: Google", "Hola", "Play Store",
        "Settings", "Google app", "Google search", "Voice search", "Camera search", "Phone", "Messages", "Chrome", "Camera",
    )

    @Test
    fun tawagMovesPhoneIntoTheFirst12() {
        val ranked = PromptBuilder.gabayLabels(GabayRanker.rank("gusto ko tawagan ang apo ko", home))
        assertEquals("Phone", ranked.first { it == "Phone" })
        assertEquals(true, ranked.indexOf("Phone") in 0..2)
    }

    @Test
    fun englishCameraPutsCameraFirst() {
        assertEquals(true, GabayRanker.rank("open the camera", home).take(2).contains("Camera"))
    }

    @Test
    fun unrelatedQuestionKeepsScreenOrder() {
        assertEquals(home, GabayRanker.rank("kumusta", home))
    }
}
