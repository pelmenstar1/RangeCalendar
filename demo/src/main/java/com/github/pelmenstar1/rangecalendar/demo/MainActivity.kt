package com.github.pelmenstar1.rangecalendar.demo

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.github.pelmenstar1.rangecalendar.RangeCalendarView

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var isNight = true
        findViewById<Button>(R.id.switchTheme).setOnClickListener {
            if(isNight) {
                isNight = false

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                isNight = true

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }

        findViewById<RangeCalendarView>(R.id.rangeCalendar).also {
            it.showAdjacentMonths = false
            it.allowedSelectionTypes().customRange(false)

            it.selectionView = MaterialButton(this).apply {
                text = "Action"
            }

            it.commonAnimationDuration = 2000
            it.hoverAnimationDuration = 1000
        }
    }
}