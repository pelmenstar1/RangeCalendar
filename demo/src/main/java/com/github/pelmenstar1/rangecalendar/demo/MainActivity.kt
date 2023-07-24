package com.github.pelmenstar1.rangecalendar.demo

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.github.pelmenstar1.rangecalendar.RangeCalendarView

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var isNight = false
        findViewById<Button>(R.id.switchTheme).setOnClickListener {
            if (isNight) {
                isNight = false

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                isNight = true

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }

        val rangeCalendar = findViewById<RangeCalendarView>(R.id.rangeCalendar)

        findViewById<Button>(R.id.clearSelectionButton).apply {
            setOnClickListener {
                rangeCalendar.clearSelection()
            }
        }
    }
}