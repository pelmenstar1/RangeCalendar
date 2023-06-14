package com.github.pelmenstar1.rangecalendar.decortest

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import com.github.pelmenstar1.rangecalendar.Fill
import com.github.pelmenstar1.rangecalendar.RangeCalendarView
import com.github.pelmenstar1.rangecalendar.RectangleShape
import com.github.pelmenstar1.rangecalendar.decoration.LineDecor
import com.github.pelmenstar1.rangecalendar.decoration.ShapeDecor
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    private lateinit var textEditText: EditText
    private var decorType: Int = DECOR_TYPE_LINE

    private var colorIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val rangeCalendar = findViewById<RangeCalendarView>(R.id.rangeCalendar)
        textEditText = findViewById(R.id.textEditText)

        initDecorTypeRadioButton(R.id.decorType_line)
        initDecorTypeRadioButton(R.id.decorType_shape)

        var day = 0
        var positionInCell = 0

        findViewById<Spinner>(R.id.daySpinner).run {
            adapter = createNumberAdapter(max = 31)

            onItemSelectedListener = createItemSelectedListener { day = it + 1 }
        }

        findViewById<Spinner>(R.id.positionSpinner).run {
            adapter = createNumberAdapter(max = 100)

            onItemSelectedListener = createItemSelectedListener { positionInCell = it }
        }

        findViewById<Button>(R.id.addButton).setOnClickListener {
            safeOperation {
                val decor = when (decorType) {
                    DECOR_TYPE_LINE -> {
                        val style = LineDecor.Style.Builder(nextFill()).also {
                            val t = textEditText.text.toString()

                            if (t.isNotBlank()) {
                                it.text(t, textSize = 13f, textColor = Color.BLACK)
                            }
                        }.build()

                        LineDecor(style)
                    }
                    DECOR_TYPE_SHAPE -> {
                        val style = ShapeDecor.Style.Builder(RectangleShape, 20f, nextFill()).build()

                        ShapeDecor(style)
                    }
                    else -> throw RuntimeException("Invalid decor type")
                }

                val date = LocalDate.of(rangeCalendar.selectedCalendarYear, rangeCalendar.selectedCalendarMonth, day)

                rangeCalendar.insertDecorations(positionInCell, arrayOf(decor), date)
            }
        }

        findViewById<Button>(R.id.removeButton).setOnClickListener {
            safeOperation {
                val date = LocalDate.of(rangeCalendar.selectedCalendarYear, rangeCalendar.selectedCalendarMonth, day)

                rangeCalendar.removeDecorationRange(positionInCell, positionInCell, date)
            }
        }
    }

    private fun nextFill(): Fill {
        return Fill.solid(nextColor())
    }

    @ColorInt
    private fun nextColor(): Int {
        return colors[(colorIndex++) % colors.size]
    }

    private inline fun safeOperation(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e("MainActivity", null, e)

            Toast.makeText(this, "Failed to do the operation", Toast.LENGTH_LONG).show()
        }
    }

    private fun initDecorTypeRadioButton(@IdRes buttonId: Int) {
        findViewById<RadioButton>(buttonId).apply {
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    onDecorTypeSelected(buttonId)
                }
            }
        }
    }

    private fun onDecorTypeSelected(buttonId: Int) {
        when(buttonId) {
            R.id.decorType_line -> {
                textEditText.visibility = View.VISIBLE

                decorType = DECOR_TYPE_LINE
            }
            R.id.decorType_shape -> {
                textEditText.visibility = View.GONE

                decorType = DECOR_TYPE_SHAPE
            }
        }
    }

    private fun createItemSelectedListener(onItemSelected: (position: Int) -> Unit): AdapterView.OnItemSelectedListener {
        return object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) = onItemSelected(position)

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
    }

    private fun createNumberAdapter(max: Int): ArrayAdapter<String> {
        val elements = buildList {
            for (number in 1..max) {
                add(number.toString())
            }
        }

        return ArrayAdapter(this, android.R.layout.simple_spinner_item, elements)
    }

    companion object {
        private val colors = intArrayOf(
            Color.RED,
            Color.GREEN,
            Color.BLUE,
            Color.CYAN,
        )

        private const val DECOR_TYPE_LINE = 0
        private const val DECOR_TYPE_SHAPE = 1
    }
}