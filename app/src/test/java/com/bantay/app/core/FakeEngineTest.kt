package com.bantay.app.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeEngineTest {

    @Test
    fun `load moves state to READY`() = runTest {
        val engine = FakeEngine()
        assertEquals(EngineState.IDLE, engine.state.value)
        engine.load()
        assertEquals(EngineState.READY, engine.state.value)
    }

    @Test
    fun `generate returns the HATOL format`() = runTest {
        val engine = FakeEngine()
        engine.load()
        val out = engine.generate("kahit ano")
        assertTrue(out.contains("HATOL:"))
        assertTrue(out.contains("DAHILAN:"))
        assertTrue(out.contains("GAWIN:"))
    }

    @Test
    fun `generate before load fails`() {
        val engine = FakeEngine()
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { engine.generate("x") }
        }
    }
}
