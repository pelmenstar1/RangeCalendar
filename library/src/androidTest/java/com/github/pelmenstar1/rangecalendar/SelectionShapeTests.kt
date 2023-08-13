package com.github.pelmenstar1.rangecalendar

import android.graphics.PointF
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.pelmenstar1.rangecalendar.selection.CellRange
import com.github.pelmenstar1.rangecalendar.selection.SelectionShape
import com.github.pelmenstar1.rangecalendar.selection.SelectionShapeInfo
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SelectionShapeTests {
    class PointArrayBuilder {
        private val list = ArrayList<PointF>()

        fun point(x: Float, y: Float) {
            list.add(PointF(x, y))
        }

        fun rect(left: Float, top: Float, right: Float, bottom: Float) {
            point(left, top)
            point(right, top)
            point(right, bottom)
            point(left, bottom)
            point(left, top)
        }

        fun toArray(): Array<PointF> {
            return list.toTypedArray()
        }
    }

    private fun createShapeInfo(
        range: CellRange,
        startLeft: Float, startTop: Float,
        endRight: Float, endTop: Float,
        roundRadius: Float = 0f
    ): SelectionShapeInfo {
        return SelectionShapeInfo(
            range,
            startLeft, startTop,
            endRight, endTop,
            FIRST_CELL_LEFT, LAST_CELL_RIGHT,
            CELL_WIDTH, CELL_HEIGHT,
            roundRadius,
            useInMonthShape = false, inMonthShapeInfo = null
        )
    }

    private fun pathNoRoundRadiiTestHelper(
        shapeInfo: SelectionShapeInfo,
        expectedBounds: RectF,
        buildPoints: PointArrayBuilder.() -> Unit
    ) {
        val shape = SelectionShape()
        shape.update(
            shapeInfo,
            SelectionShape.ORIGIN_LOCAL,
            SelectionShape.FLAG_IGNORE_ROUND_RADII or SelectionShape.FLAG_FORCE_PATH
        )

        assertEquals(expectedBounds, shape.bounds)

        val expectedPoints = PointArrayBuilder().also(buildPoints).toArray()
        val actualPoints = PathTestHelper.getPathPoints(shape.path!!)

        assertContentEquals(expectedPoints, actualPoints)
    }

    @Test
    fun pathNoRoundRadiiTest() {
        // 1 row
        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(1, 2),
                startLeft = 17f, startTop = 5f,
                endRight = 30f, endTop = 5f
            ),
            expectedBounds = RectF(17f, 5f, 30f, 15f)
        ) {
            rect(17f, 5f, 30f, 15f)
        }

        // 2 rows
        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                CellRange(2, 7),
                startLeft = 30f, startTop = 5f,
                endRight = 20f, endTop = 5f + CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
        ) {
            rect(30f, 5f, LAST_CELL_RIGHT, 5f + CELL_HEIGHT)
            rect(FIRST_CELL_LEFT, 5f + CELL_HEIGHT, 20f, 5f + 2f * CELL_HEIGHT)
        }

        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(1, 8),
                startLeft = 30f, startTop = 5f,
                endRight = 40f, endTop = 5f + CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT),
        ) {
            point(30f, 5f)
            point(LAST_CELL_RIGHT, 5f)
            point(LAST_CELL_RIGHT, 5f + CELL_HEIGHT)
            point(40f, 5f + CELL_HEIGHT)
            point(40f, 5f + 2f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + 2f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + CELL_HEIGHT)
            point(30f, 5f + CELL_HEIGHT)
            point(30f, 5f)
        }

        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(0, 13),
                startLeft = FIRST_CELL_LEFT, startTop = 5f,
                endRight = LAST_CELL_RIGHT, endTop = 5f + CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
        ) {
            rect(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
        }

        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(1, 13),
                startLeft = 15f, startTop = 5f,
                endRight = LAST_CELL_RIGHT, endTop = 5f + CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
        ) {
            point(15f, 5f)
            point(LAST_CELL_RIGHT, 5f)
            point(LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + 2f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + CELL_HEIGHT)
            point(15f, 5f + CELL_HEIGHT)
            point(15f, 5f)
        }

        // 2+ rows
        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(0, 20),
                startLeft = FIRST_CELL_LEFT, startTop = 5f,
                endRight = LAST_CELL_RIGHT, endTop = 5f + 2f * CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 3f * CELL_HEIGHT)
        ) {
            rect(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 3f * CELL_HEIGHT)
        }

        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(1, 19),
                startLeft = 15f, startTop = 5f,
                endRight = 50f, endTop = 5f + 2f * CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 3f * CELL_HEIGHT)
        ) {
            point(15f, 5f)
            point(LAST_CELL_RIGHT, 5f)
            point(LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
            point(50f, 5f + 2f * CELL_HEIGHT)
            point(50f, 5f + 3f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + 3f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + CELL_HEIGHT)
            point(15f, 5f + CELL_HEIGHT)
            point(15f, 5f)
        }

        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(1, 20),
                startLeft = 15f, startTop = 5f,
                endRight = LAST_CELL_RIGHT, endTop = 5f + 2f * CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 3f * CELL_HEIGHT)
        ) {
            point(15f, 5f)
            point(LAST_CELL_RIGHT, 5f)
            point(LAST_CELL_RIGHT, 5f + 3f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + 3f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + CELL_HEIGHT)
            point(15f, 5f + CELL_HEIGHT)
            point(15f, 5f)
        }

        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(0, 19),
                startLeft = FIRST_CELL_LEFT, startTop = 5f,
                endRight = 50f, endTop = 5f + 2f * CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 3f * CELL_HEIGHT)
        ) {
            point(FIRST_CELL_LEFT, 5f)
            point(LAST_CELL_RIGHT, 5f)
            point(LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
            point(50f, 5f + 2f * CELL_HEIGHT)
            point(50f, 5f + 3f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + 3f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f)
        }
    }

    companion object {
        private const val CELL_WIDTH = 10f
        private const val CELL_HEIGHT = 10f

        private const val FIRST_CELL_LEFT = 5f
        private const val LAST_CELL_RIGHT = FIRST_CELL_LEFT + CELL_WIDTH * GridConstants.COLUMN_COUNT
    }
}