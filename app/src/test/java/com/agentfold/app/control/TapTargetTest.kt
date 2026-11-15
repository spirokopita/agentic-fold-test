package com.agentfold.app.control

import org.junit.Assert.*
import org.junit.Test

class TapTargetTest {
    private val frame = WindowFrame(3, "test.app", 1000, 100, 2000, 1100)

    @Test fun centreMapsToWindowRelativePercent() {
        assertEquals(50 to 50, TapTarget.centerPercent(frame, 1400, 400, 1600, 800))
    }
    @Test fun resultRoundTripsToTheSameScreenPointWithinOnePercent() {
        val (x, y) = TapTarget.centerPercent(frame, 1700, 300, 1900, 500)!!
        val (px, py) = frame.point(x, y)!!
        assertEquals(1800f, px, frame.width / 100f)
        assertEquals(400f, py, frame.height / 100f)
    }
    @Test fun edgeElementsClampInsideTheAcceptedRange() {
        assertEquals(1 to 1, TapTarget.centerPercent(frame, 1000, 100, 1002, 102))
        assertEquals(99 to 99, TapTarget.centerPercent(frame, 1998, 1098, 2000, 1100))
    }
    @Test fun offWindowOrEmptyElementsAreRejected() {
        assertNull(TapTarget.centerPercent(frame, 0, 0, 100, 100))
        assertNull(TapTarget.centerPercent(frame, 1500, 500, 1500, 600))
        assertNull(TapTarget.centerPercent(frame.copy(right = 1000), 1000, 100, 1100, 200))
    }
}
