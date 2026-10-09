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
    fun callOnlyOffersPhoneButtons() {
        assertEquals(listOf("Phone", "Phone Master"), GabayRanker.candidates("Call my grandson", home))
        assertEquals(listOf("Phone", "Phone Master"), GabayRanker.candidates("gusto ko tawagan ang apo ko", home))
    }

    @Test
    fun cameraPrefersExactLabel() {
        assertEquals(listOf("Camera", "Camera search"), GabayRanker.candidates("open the camera", home))
    }

    @Test
    fun unrelatedQuestionOffersEveryButton() {
        assertEquals(home, GabayRanker.candidates("kumusta", home))
    }
}
