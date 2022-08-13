package com.github.pelmenstar1.rangecalendar

import kotlin.test.Test
import kotlin.test.assertEquals

class PackedRectFTests {
    private inline fun forEachRange(block: (Float) -> Unit) {
        var number = 0f

        while(number < 100f) {
            block(number)
            number += 1f
        }

        block(1000f)
    }


    @Test
    fun test() {
        forEachRange { left ->
            forEachRange { top ->
                forEachRange { right ->
                    forEachRange { bottom ->
                        val size = PackedRectF(left, top, right, bottom)

                        assertEquals(left, size.left, 0.1f)
                        assertEquals(top, size.top, 0.1f)
                        assertEquals(right, size.right, 0.1f)
                        assertEquals(bottom, size.bottom, 0.1f)
                    }
                }
            }
        }
    }
}