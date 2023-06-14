package com.github.pelmenstar1.rangecalendar.movebuttontest

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import com.github.pelmenstar1.rangecalendar.MoveButtonDrawable
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {
    private lateinit var colorControlNormal: ColorStateList

    private lateinit var drawableLeft: MoveButtonDrawable
    private lateinit var drawableRight: MoveButtonDrawable

    private lateinit var drawableLeftHolder: View
    private lateinit var drawableRightHolder: View

    private lateinit var animDurationTextView: TextView

    private var defaultArrowSize = 0f

    private var currentAnimationType: Int = MoveButtonDrawable.ANIM_TYPE_ARROW_TO_CROSS
    private var animationDuration = 1000
    private var isArrowAnimationReversed = false

    private var stateFlags = STATE_ENABLED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        colorControlNormal =
            getColorStateListFromAttribute(this, androidx.appcompat.R.attr.colorControlNormal)

        defaultArrowSize = resources.getDimension(R.dimen.testArrowSize)

        drawableLeftHolder = findViewById(R.id.drawableHolder_leftDirection)
        drawableRightHolder = findViewById(R.id.drawableHolder_rightDirection)

        setAnimationType(MoveButtonDrawable.ANIM_TYPE_ARROW_TO_CROSS)

        animDurationTextView = findViewById(R.id.animDurationTextView)
        updateAnimationDurationTextView()

        initStateCheckBox(R.id.visualStateButton_enabled, STATE_ENABLED)
        initStateCheckBox(R.id.visualStateButton_pressed, STATE_PRESSED)

        findViewById<Slider>(R.id.animDurationSlider).apply {
            addOnChangeListener { _, value, _ ->
                animationDuration = value.toInt()

                drawableLeft.setStateChangeDuration(value.toLong())
                drawableRight.setStateChangeDuration(value.toLong())

                updateAnimationDurationTextView()
            }
        }

        findViewById<RadioGroup>(R.id.animTypeRadioGroup).apply {
            setOnCheckedChangeListener { _, checkedId ->
                val newAnimType = when (checkedId) {
                    R.id.animTypeButton_arrowToCross -> MoveButtonDrawable.ANIM_TYPE_ARROW_TO_CROSS
                    R.id.animTypeButton_voidToArrow -> MoveButtonDrawable.ANIM_TYPE_VOID_TO_ARROW
                    else -> throw RuntimeException("Invalid id")
                }

                setAnimationType(newAnimType)
            }
        }

        findViewById<Button>(R.id.start_arrow_anim_button).setOnClickListener {
            startArrowAnimation()
        }

        findViewById<Button>(R.id.start_state_change_anim_button).setOnClickListener {
            startStateChangeAnimation()
        }
    }

    private fun initStateCheckBox(@IdRes buttonId: Int, state: Int) {
        findViewById<CheckBox>(buttonId).apply {
            setOnCheckedChangeListener { _, isChecked ->
                stateFlags = if (isChecked) {
                    stateFlags or state
                } else {
                    stateFlags and state.inv()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateAnimationDurationTextView() {
        animDurationTextView.text = "${getString(R.string.animationDuration)}: ${animationDuration}"
    }

    private fun startArrowAnimation() {
        ValueAnimator.ofFloat(0f, 1f).run {
            duration = animationDuration.toLong()

            addUpdateListener {
                val fraction = it.animatedFraction

                drawableLeft.setAnimationFraction(fraction)
                drawableRight.setAnimationFraction(fraction)
            }

            if (isArrowAnimationReversed) {
                reverse()
            } else {
                start()
            }

            isArrowAnimationReversed = !isArrowAnimationReversed
        }
    }

    private fun startStateChangeAnimation() {
        val flags = stateFlags
        val state = IntArray(flags.countOneBits())
        var index = 0

        if (flags and STATE_ENABLED != 0) {
            state[index++] = android.R.attr.state_enabled
        }

        if (flags and STATE_PRESSED != 0) {
            state[index] = android.R.attr.state_pressed
        }

        drawableLeft.state = state
        drawableRight.state = state
    }

    private fun setAnimationType(type: Int) {
        currentAnimationType = type

        initDrawableHolderView(drawableLeftHolder, MoveButtonDrawable.DIRECTION_LEFT)
        initDrawableHolderView(drawableRightHolder, MoveButtonDrawable.DIRECTION_RIGHT)
    }

    private fun initDrawableHolderView(view: View, direction: Int) {
        val drawable = MoveButtonDrawable(this, colorControlNormal, direction, currentAnimationType).apply {
            setArrowSize(defaultArrowSize)

            setStateChangeDuration(animationDuration.toLong())
        }

        view.background = drawable

        if (direction == MoveButtonDrawable.ANIM_TYPE_ARROW_TO_CROSS) {
            drawableLeft = drawable
        } else {
            drawableRight = drawable
        }
    }

    companion object {
        private const val STATE_ENABLED = 1
        private const val STATE_PRESSED = 1 shl 1

        fun getColorStateListFromAttribute(context: Context, @AttrRes attr: Int): ColorStateList {
            val array = context.obtainStyledAttributes(intArrayOf(attr))

            try {
                return array.getColorStateList(0)!!
            } finally {
                array.recycle()
            }
        }
    }
}