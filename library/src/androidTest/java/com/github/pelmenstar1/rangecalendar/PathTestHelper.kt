package com.github.pelmenstar1.rangecalendar

import android.graphics.Path
import android.graphics.PointF

object PathTestHelper {
    @Suppress("UNCHECKED_CAST")
    fun getPathPoints(path: Path): Array<PointF> {
        val rawPoints = path.approximate(0.0001f)
        val points = arrayOfNulls<PointF>(rawPoints.size / 3)

        for (i in points.indices) {
            val x = rawPoints[i * 3 + 1]
            val y = rawPoints[i * 3 + 2]

            points[i] = PointF(x, y)
        }

        return points as Array<PointF>
    }
}