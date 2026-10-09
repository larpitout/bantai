package com.bantai.rules

import com.bantai.rules.GabayIntent.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class GabayIntentTest {

    @Test
    fun callNamedPersonOnMessenger() {
        assertEquals(GabayIntent(Action.CALL, "Junjun", false, "messenger"), GabayIntent.parse("Call Junjun on Messenger"))
    }

    @Test
    fun tagalogCallGrandchildOnMessenger() {
        assertEquals(GabayIntent(Action.CALL, null, true, "messenger"), GabayIntent.parse("Tawagan mo nga sa Messenger yung apo ko"))
    }

    @Test
    fun englishCallGrandson() {
        assertEquals(GabayIntent(Action.CALL, null, true, null), GabayIntent.parse("Call my grandson"))
    }

    @Test
    fun videoCallSiMaria() {
        assertEquals(GabayIntent(Action.VIDEO_CALL, "Maria", false, null), GabayIntent.parse("video call si Maria"))
    }

    @Test
    fun openAppHasNoPerson() {
        assertEquals(GabayIntent(Action.OPEN, null, false, null), GabayIntent.parse("Go to Facebook"))
    }

    @Test
    fun tawaganSiJunjunSaViber() {
        assertEquals(GabayIntent(Action.CALL, "Junjun", false, "viber"), GabayIntent.parse("tawagan si Junjun sa Viber"))
    }
}
