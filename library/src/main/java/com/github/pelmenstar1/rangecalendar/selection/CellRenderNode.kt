package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderNode
import androidx.annotation.RequiresApi
import com.github.pelmenstar1.rangecalendar.SelectionFillGradientBoundsType
import com.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat

/**
 * Represents a wrapper of [RenderNode] that adapted to contain a cell.
 * Makes all the dirty stuff about re-rendering the content of [RenderNode] easier.
 */
@RequiresApi(29)
internal class CellRenderNode {
    private val renderNode = RenderNode(null)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var width = 0f
    private var height = 0f
    private var options: SelectionRenderOptions? = null

    private var currentAlpha = 0f
    private var transX = 0f
    private var transY = 0f

    // Determines whether the cell should be re-rendered
    private var isDirty = true

    fun setSize(w: Float, h: Float) {
        val oldWidth = width
        val oldHeight = height

        width = w
        height = h

        // As we're specifying the size, the components should be "ceiled" not to cut a pixel due to
        // antialiasing.
        val ceilNewW = (w + 1f).toInt()
        val ceilNewH = (h + 1f).toInt()

        if ((oldWidth + 1f).toInt() != ceilNewW || (oldHeight + 1f).toInt() != ceilNewH) {
            renderNode.setPosition(0, 0, ceilNewW, ceilNewH)

            isDirty = true
        }
    }

    fun setRenderOptions(newOptions: SelectionRenderOptions) {
        val oldOptions = options
        options = newOptions

        if (oldOptions == null || shouldNodeBeUpdatedOnOptionsChange(oldOptions, newOptions)) {
            isDirty = true
        }
    }

    /**
     * Draws the cell at specified position with given alpha.
     *
     * The [canvas] is expected to hardware-accelerated.
     */
    fun draw(canvas: Canvas, left: Float, top: Float, alpha: Float) {
        val node = renderNode

        if (isDirty) {
            isDirty = false
            updateNode()
        }

        // Checking up front if we should update the RenderNode is cheaper than making native call each time.
        updateNodeProperty(node, alpha, ::currentAlpha, { currentAlpha = it }, RenderNode::setAlpha)
        updateNodeProperty(node, left, ::transX, { transX = it }, RenderNode::setTranslationX)
        updateNodeProperty(node, top, ::transY, { transY = it }, RenderNode::setTranslationY)

        canvas.drawRenderNode(node)
    }

    private inline fun updateNodeProperty(
        node: RenderNode,
        newValue: Float,
        get: () -> Float,
        set: (Float) -> Unit,
        setOnNode: RenderNode.(Float) -> Unit
    ) {
        if (get() != newValue) {
            set(newValue)
            node.setOnNode(newValue)
        }
    }

    private fun shouldNodeBeUpdatedOnOptionsChange(
        oldOptions: SelectionRenderOptions,
        newOptions: SelectionRenderOptions
    ): Boolean {
        return oldOptions.fill != newOptions.fill ||
                oldOptions.fillGradientBoundsType != newOptions.fillGradientBoundsType
    }

    private fun updateNode() {
        val node = renderNode

        val options = options!!
        val fill = options.fill
        val paint = paint
        val w = width
        val h = height

        val canvas = node.beginRecording((w + 1f).toInt(), (h + 1f).toInt())

        try {
            if (options.fillGradientBoundsType == SelectionFillGradientBoundsType.SHAPE) {
                fill.setBounds(0f, 0f, w, h)
            }

            fill.applyToPaint(paint)

            canvas.drawRoundRectCompat(0f, 0f, w, h, options.roundRadius, paint)
        } finally {
            node.endRecording()
        }
    }
}