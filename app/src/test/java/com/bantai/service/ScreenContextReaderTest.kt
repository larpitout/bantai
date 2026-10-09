package com.bantai.service

import com.bantai.model.ScreenContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ScreenContextReader logic and constraints.
 */
class ScreenContextReaderTest {

    private val reader = ScreenContextReader()

    @Test
    fun testNullRootNodeReturnsEmptyScreenContext() {
        val result = reader.extractScreenContext(null, "com.fake.app")
        assertEquals("com.fake.app", result.appName)
        assertTrue(result.labels.isEmpty())
    }

    @Test
    fun testNullRootAndPackageReturnsEmptyStrings() {
        val result = reader.extractScreenContext(null, null)
        assertEquals("", result.appName)
        assertTrue(result.labels.isEmpty())
    }

    @Test
    fun testConstantsMeetRequirements() {
        assertEquals(30, ScreenContextReader.MAX_NODES)
        assertEquals(600, ScreenContextReader.MAX_CHARACTERS)
    }
}
