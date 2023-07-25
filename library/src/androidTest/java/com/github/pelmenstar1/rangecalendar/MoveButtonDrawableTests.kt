package com.github.pelmenstar1.rangecalendar

import android.content.res.ColorStateList
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class MoveButtonDrawableTests {
    private data class AnimationPathState(val fraction: Float, val points: Array<PointF>) {
        override fun equals(other: Any?): Boolean {
            return if (other is AnimationPathState) {
                fraction == other.fraction && points contentEquals other.points
            } else false
        }

        override fun hashCode(): Int {
            var result = fraction.toBits()
            result = result * 31 + points.contentHashCode()

            return result
        }

        override fun toString(): String {
            return "AnimationPathState(fraction=$fraction, points=${points.contentToString()})"
        }
    }

    private class AnimationPathStateBuilder(val fraction: Float) {
        private val points = ArrayList<PointF>()

        fun point(x: Float, y: Float) {
            points.add(PointF(x, y))
        }

        fun build(): AnimationPathState {
            return AnimationPathState(fraction, points.toTypedArray())
        }
    }

    private class AnimationPathStateArrayBuilder {
        private val states = ArrayList<AnimationPathState>()

        fun frame(fraction: Float, buildPoints: AnimationPathStateBuilder.() -> Unit) {
            val state = AnimationPathStateBuilder(fraction).also(buildPoints).build()

            states.add(state)
        }

        fun build(): Array<AnimationPathState> {
            return states.toTypedArray()
        }
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Suppress("UNCHECKED_CAST")
    private fun getPathPoints(path: Path): Array<PointF> {
        val rawPoints = path.approximate(0.0001f)
        val points = arrayOfNulls<PointF>(rawPoints.size / 3)

        for (i in points.indices) {
            val x = rawPoints[i * 3 + 1]
            val y = rawPoints[i * 3 + 2]

            points[i] = PointF(x, y)
        }

        return points as Array<PointF>
    }

    private fun arrowAnimationTestHelper(
        direction: Int,
        type: Int,
        buildFrames: AnimationPathStateArrayBuilder.() -> Unit
    ) {
        val drawable = MoveButtonDrawable(context, ColorStateList.valueOf(0), direction, type)
        drawable.setArrowStrokeWidth(2f)

        val expectedFrames = AnimationPathStateArrayBuilder().also(buildFrames).build()
        val actualFramesList = ArrayList<AnimationPathState>()

        val leftTop = ARROW_LEFT_TOP

        drawable.setBounds(leftTop.x, leftTop.y, leftTop.x + ARROW_SIZE, leftTop.y + ARROW_SIZE)
        drawable.setArrowSize(ARROW_SIZE.toFloat())

        for ((fraction, _) in expectedFrames) {
            drawable.setAnimationFraction(fraction)

            val points = if (drawable.arrowUsePath) {
                getPathPoints(drawable.arrowPath)
            } else {
                val rawPoints = drawable.arrowLinePoints

                arrayOf(
                    PointF(rawPoints[0], rawPoints[1]),
                    PointF(rawPoints[2], rawPoints[3]),
                )
            }

            actualFramesList.add(AnimationPathState(fraction, points))
        }

        val actualFrames = actualFramesList.toTypedArray()

        assertContentEquals(expectedFrames, actualFrames)
    }

    @Test
    fun arrowAnimationTest_directionRight_arrowCross() {
        arrowAnimationTestHelper(
            direction = MoveButtonDrawable.DIRECTION_RIGHT,
            type = MoveButtonDrawable.ANIM_TYPE_ARROW_TO_CROSS
        ) {
            frame(fraction = 0f) {
                point(x = 11f, y = 21f)
                point(x = 20f, y = 30f)
                point(x = 11f, y = 39f)
            }

            frame(fraction = 0.25f) {
                point(x = 11f, y = 21f)
                point(x = 22.25f, y = 32.25f)
                point(x = 11f, y = 39f)
                point(x = 22.25f, y = 27.75f)
            }

            frame(fraction = 0.5f) {
                point(x = 11f, y = 21f)
                point(x = 24.5f, y = 34.5f)
                point(x = 11f, y = 39f)
                point(x = 24.5f, y = 25.5f)
            }

            frame(fraction = 1f) {
                point(x = 11f, y = 21f)
                point(x = 29f, y = 39f)
                point(x = 11f, y = 39f)
                point(x = 29f, y = 21f)
            }
        }
    }

    @Test
    fun arrowAnimationTest_directionRight_voidArrow() {
        arrowAnimationTestHelper(
            direction = MoveButtonDrawable.DIRECTION_RIGHT,
            type = MoveButtonDrawable.ANIM_TYPE_VOID_TO_ARROW
        ) {
            frame(fraction = 0f) {
                point(x = 11f, y = 39f)
                point(x = 11f, y = 39f)
            }

            frame(fraction = 0.25f) {
                point(x = 11f, y = 39f)
                point(x = 15.5f, y = 34.5f)
            }

            frame(fraction = 0.5f) {
                point(x = 11f, y = 39f)
                point(x = 20f, y = 30f)
            }

            frame(fraction = 0.75f) {
                point(x = 11f, y = 39f)
                point(x = 20f, y = 30f)
                point(x = 15.5f, y = 25.5f)
            }

            frame(fraction = 1f) {
                point(x = 11f, y = 39f)
                point(x = 20f, y = 30f)
                point(x = 11f, y = 21f)
            }
        }
    }

    @Test
    fun arrowAnimationTest_directionLeft_arrowCross() {
        arrowAnimationTestHelper(
            direction = MoveButtonDrawable.DIRECTION_LEFT,
            type = MoveButtonDrawable.ANIM_TYPE_ARROW_TO_CROSS
        ) {
            frame(fraction = 0f) {
                point(x = 29f, y = 21f)
                point(x = 20f, y = 30f)
                point(x = 29f, y = 39f)
            }

            frame(fraction = 0.25f) {
                point(x = 29f, y = 21f)
                point(x = 17.75f, y = 32.25f)
                point(x = 29f, y = 39f)
                point(x = 17.75f, y = 27.75f)
            }

            frame(fraction = 0.5f) {
                point(x = 29f, y = 21f)
                point(x = 15.5f, y = 34.5f)
                point(x = 29f, y = 39f)
                point(x = 15.5f, y = 25.5f)
            }

            frame(fraction = 1f) {
                point(x = 29f, y = 21f)
                point(x = 11f, y = 39f)
                point(x = 29f, y = 39f)
                point(x = 11f, y = 21f)
            }
        }
    }

    @Test
    fun arrowAnimationTest_directionLeft_voidArrow() {
        arrowAnimationTestHelper(
            direction = MoveButtonDrawable.DIRECTION_LEFT,
            type = MoveButtonDrawable.ANIM_TYPE_VOID_TO_ARROW
        ) {
            frame(fraction = 0f) {
                point(x = 29f, y = 39f)
                point(x = 29f, y = 39f)
            }

            frame(fraction = 0.25f) {
                point(x = 29f, y = 39f)
                point(x = 24.5f, y = 34.5f)
            }

            frame(fraction = 0.5f) {
                point(x = 29f, y = 39f)
                point(x = 20f, y = 30f)
            }

            frame(fraction = 0.75f) {
                point(x = 29f, y = 39f)
                point(x = 20f, y = 30f)
                point(x = 24.5f, y = 25.5f)
            }

            frame(fraction = 1f) {
                point(x = 29f, y = 39f)
                point(x = 20f, y = 30f)
                point(x = 29f, y = 21f)
            }
        }
    }

    @Test
    fun setAlphaTest() {
        val baseColor = 0x10AABBCC

        val drawable = MoveButtonDrawable(
            context,
            ColorStateList.valueOf(baseColor),
            MoveButtonDrawable.DIRECTION_LEFT,
            MoveButtonDrawable.ANIM_TYPE_ARROW_TO_CROSS
        )

        drawable.alpha = 127

        val paint = drawable.arrowPaint
        val actualColor = paint.color
        val expectedColor = 0x08AABBCC

        assertEquals(expectedColor, actualColor)
    }

    companion object {
        private const val ARROW_SIZE = 20
        private const val ARROW_STROKE_WIDTH = 2f
        private val ARROW_LEFT_TOP = Point(10, 20)
    }
}