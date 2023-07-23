package com.github.pelmenstar1.rangecalendar

import android.content.res.ColorStateList
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals

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
        leftTop: Point,
        arrowSize: Int,
        buildFrames: AnimationPathStateArrayBuilder.() -> Unit
    ) {
        val drawable = MoveButtonDrawable(context, ColorStateList.valueOf(0), direction, type)

        val expectedFrames = AnimationPathStateArrayBuilder().also(buildFrames).build()
        val actualFramesList = ArrayList<AnimationPathState>()

        drawable.setBounds(leftTop.x, leftTop.y, leftTop.x + arrowSize, leftTop.y + arrowSize)
        drawable.setArrowSize(arrowSize.toFloat())

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

        Log.i("DrawableTests", actualFrames.contentToString())

        assertContentEquals(expectedFrames, actualFrames)
    }

    @Test
    fun arrowAnimationTest_directionRight_arrowCross() {
        arrowAnimationTestHelper(
            direction = MoveButtonDrawable.DIRECTION_RIGHT,
            type = MoveButtonDrawable.ANIM_TYPE_ARROW_TO_CROSS,
            leftTop = Point(10, 20),
            arrowSize = 20
        ) {
            frame(fraction = 0f) {
                point(x = 10f, y = 20f)
                point(x = 20f, y = 30f)
                point(x = 10f, y = 40f)
            }

            frame(fraction = 0.25f) {
                point(x = 10f, y = 20f)
                point(x = 22.5f, y = 32.5f)
                point(x = 10f, y = 40f)
                point(x = 22.5f, y = 27.5f)
            }

            frame(fraction = 0.5f) {
                point(x = 10f, y = 20f)
                point(x = 25f, y = 35f)
                point(x = 10f, y = 40f)
                point(x = 25f, y = 25f)
            }

            frame(fraction = 1f) {
                point(x = 10f, y = 20f)
                point(x = 30f, y = 40f)
                point(x = 10f, y = 40f)
                point(x = 30f, y = 20f)
            }
        }
    }

    @Test
    fun arrowAnimationTest_directionRight_voidArrow() {
        arrowAnimationTestHelper(
            direction = MoveButtonDrawable.DIRECTION_RIGHT,
            type = MoveButtonDrawable.ANIM_TYPE_VOID_TO_ARROW,
            leftTop = Point(10, 20),
            arrowSize = 20
        ) {
            frame(fraction = 0f) {
                point(x = 10f, y = 40f)
                point(x = 10f, y = 40f)
            }

            frame(fraction = 0.25f) {
                point(x = 10f, y = 40f)
                point(x = 15f, y = 35f)
            }

            frame(fraction = 0.5f) {
                point(x = 10f, y = 40f)
                point(x = 20f, y = 30f)
            }

            frame(fraction = 0.75f) {
                point(x = 10f, y = 40f)
                point(x = 20f, y = 30f)
                point(x = 15f, y = 25f)
            }

            frame(fraction = 1f) {
                point(x = 10f, y = 40f)
                point(x = 20f, y = 30f)
                point(x = 10f, y = 20f)
            }
        }
    }

    @Test
    fun arrowAnimationTest_directionLeft_arrowCross() {
        arrowAnimationTestHelper(
            direction = MoveButtonDrawable.DIRECTION_LEFT,
            type = MoveButtonDrawable.ANIM_TYPE_ARROW_TO_CROSS,
            leftTop = Point(10, 20),
            arrowSize = 20
        ) {
            frame(fraction = 0f) {
                point(x = 30f, y = 20f)
                point(x = 20f, y = 30f)
                point(x = 30f, y = 40f)
            }

            frame(fraction = 0.25f) {
                point(x = 30f, y = 20f)
                point(x = 17.5f, y = 32.5f)
                point(x = 30f, y = 40f)
                point(x = 17.5f, y = 27.5f)
            }

            frame(fraction = 0.5f) {
                point(x = 30f, y = 20f)
                point(x = 15f, y = 35f)
                point(x = 30f, y = 40f)
                point(x = 15f, y = 25f)
            }

            frame(fraction = 1f) {
                point(x = 30f, y = 20f)
                point(x = 10f, y = 40f)
                point(x = 30f, y = 40f)
                point(x = 10f, y = 20f)
            }
        }
    }

    @Test
    fun arrowAnimationTest_directionLeft_voidArrow() {
        arrowAnimationTestHelper(
            direction = MoveButtonDrawable.DIRECTION_LEFT,
            type = MoveButtonDrawable.ANIM_TYPE_VOID_TO_ARROW,
            leftTop = Point(10, 20),
            arrowSize = 20
        ) {
            frame(fraction = 0f) {
                point(x = 30f, y = 40f)
                point(x = 30f, y = 40f)
            }

            frame(fraction = 0.25f) {
                point(x = 30f, y = 40f)
                point(x = 25f, y = 35f)
            }

            frame(fraction = 0.5f) {
                point(x = 30f, y = 40f)
                point(x = 20f, y = 30f)
            }

            frame(fraction = 0.75f) {
                point(x = 30f, y = 40f)
                point(x = 20f, y = 30f)
                point(x = 25f, y = 25f)
            }

            frame(fraction = 1f) {
                point(x = 30f, y = 40f)
                point(x = 20f, y = 30f)
                point(x = 30f, y = 20f)
            }
        }
    }
}