package io.github.chorusfruit233.studywithmiku

import org.junit.Test
import org.junit.Assert.*

class FadeCurveTest {
    @Test fun envelopeIsMonotonicBoundedAndKeepsTotalGainConstant() {
        var previous = 0f
        for (i in 0..100) {
            val gain = fadeCurve(i/100f)
            assertTrue(gain in 0f..1f); assertTrue(gain >= previous)
            assertEquals(1f,gain+fadeCurve(1-i/100f),0.0001f)
            previous = gain
        }
        assertEquals(0f,fadeCurve(-1f),0f); assertEquals(1f,fadeCurve(2f),0f)
    }
}
