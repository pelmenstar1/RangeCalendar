package com.github.pelmenstar1.rangecalendar.selectionviewtest

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import com.github.pelmenstar1.rangecalendar.CalendarSelectionViewLayoutParams
import com.github.pelmenstar1.rangecalendar.RangeCalendarView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {
    private lateinit var rangeCalendarView: RangeCalendarView
    private lateinit var transitionDurationTextView: TextView

    private var selectionViewHorizontalGravity = Gravity.START
    private var selectionViewVerticalGravity = Gravity.TOP

    private var selectionViewWidth = ViewGroup.LayoutParams.WRAP_CONTENT
    private var selectionViewHeight = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        rangeCalendarView = findViewById(R.id.rangeCalendar)

        // Init hasClearCheckBox

        findViewById<CheckBox>(R.id.hasClearCheckBox).apply {
            setOnCheckedChangeListener { _, isChecked ->
                rangeCalendarView.hasSelectionViewClearButton = isChecked
            }
        }

        // Init isSelectionViewNullCheckBox

        findViewById<CheckBox>(R.id.isSelectionViewNullCheckBox).apply {
            setOnCheckedChangeListener { _, isChecked ->
                setIsSelectionViewNull(isChecked)
            }
        }

        // Init transition duration views

        transitionDurationTextView = findViewById(R.id.transitionDurationTextView)
        onTransitionDurationChanged(value = 1000)

        findViewById<Slider>(R.id.transitionDurationSlider).apply {
            addOnChangeListener { _, value, _ ->
                onTransitionDurationChanged(value.toLong())
            }
        }

        // Init gravity spinners

        initGravitySpinner(
            spinnerRes = R.id.horizontalGravitySpinner,
            values = intArrayOf(Gravity.START, Gravity.CENTER_HORIZONTAL, Gravity.END),
            isHorizontal = true
        )

        initGravitySpinner(
            spinnerRes = R.id.verticalGravitySpinner,
            values = intArrayOf(Gravity.TOP, Gravity.CENTER_VERTICAL, Gravity.BOTTOM),
            isHorizontal = false
        )

        // Init size spinners

        initSizeSpinner(spinnerRes = R.id.selectionViewWidthSpinner, isWidth = true)
        initSizeSpinner(spinnerRes = R.id.selectionViewHeightSpinner, isWidth = false)

        // Init rangeCalendarView

        setIsSelectionViewNull(state = false)
        updateSelectionViewLayoutParams()
    }

    private fun setIsSelectionViewNull(state: Boolean) {
        rangeCalendarView.selectionView = if (state) {
            null
        } else {
            MaterialButton(this).apply {
                text = getString(R.string.action)
            }
        }
    }

    private fun initGravitySpinner(
        @IdRes spinnerRes: Int,
        values: IntArray,
        isHorizontal: Boolean
    ) {
        findViewById<Spinner>(spinnerRes).apply {
            onItemSelectedListener = createSpinnerSelectedListener { position ->
                val gravity = values[position]

                if (isHorizontal) {
                    selectionViewHorizontalGravity = gravity
                } else {
                    selectionViewVerticalGravity = gravity
                }

                updateSelectionViewLayoutParams()
            }
        }
    }

    private fun initSizeSpinner(@IdRes spinnerRes: Int, isWidth: Boolean) {
        findViewById<Spinner>(spinnerRes).apply {
            onItemSelectedListener = createSpinnerSelectedListener { position ->
                val size = when (position) {
                    0 -> ViewGroup.LayoutParams.WRAP_CONTENT
                    1 -> ViewGroup.LayoutParams.MATCH_PARENT
                    else -> throw RuntimeException("Invalid position")
                }

                if (isWidth) {
                    selectionViewWidth = size
                } else {
                    selectionViewHeight = size
                }

                updateSelectionViewLayoutParams()
            }
        }
    }

    private fun createSpinnerSelectedListener(block: (position: Int) -> Unit): AdapterView.OnItemSelectedListener {
        return object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                block(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
    }

    private fun updateSelectionViewLayoutParams() {
        rangeCalendarView.selectionViewLayoutParams = CalendarSelectionViewLayoutParams(
            selectionViewWidth,
            selectionViewHeight
        ).apply {
            gravity = selectionViewHorizontalGravity or selectionViewVerticalGravity
        }
    }

    @SuppressLint("SetTextI18n")
    private fun onTransitionDurationChanged(value: Long) {
        rangeCalendarView.selectionViewTransitionDuration = value

        transitionDurationTextView.text = "${getString(R.string.transitionDuration)}: $value"
    }
}