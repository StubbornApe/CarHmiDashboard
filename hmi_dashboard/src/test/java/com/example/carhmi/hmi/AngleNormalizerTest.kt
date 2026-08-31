package com.example.carhmi.hmi

import org.junit.Assert.assertEquals
import org.junit.Test

class AngleNormalizerTest {

    private val DELTA = 1e-4f

    @Test
    fun `0 到 360 内保持不变`() {
        assertEquals(0f, AngleNormalizer.normalize(0f), DELTA)
        assertEquals(135f, AngleNormalizer.normalize(135f), DELTA)
        assertEquals(359f, AngleNormalizer.normalize(359f), DELTA)
    }

    @Test
    fun `超过 360 折回`() {
        assertEquals(45f, AngleNormalizer.normalize(405f), DELTA)
        assertEquals(0f, AngleNormalizer.normalize(360f), DELTA)
    }

    @Test
    fun `负角安全`() {
        assertEquals(270f, AngleNormalizer.normalize(-90f), DELTA)
        assertEquals(0f, AngleNormalizer.normalize(-360f), DELTA)
    }
}