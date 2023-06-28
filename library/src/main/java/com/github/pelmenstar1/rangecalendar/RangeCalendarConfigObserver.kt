package com.github.pelmenstar1.rangecalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue
import java.util.TimeZone

/**
 * Responsible for observing the changes of some properties (like time zone, date). This is done via [BroadcastReceiver].
 * The specified [calendarView] is notified about these changes.
 *
 * The observer is not enabled by default, it should be done manually through [register]. As the class is based on [BroadcastReceiver],
 * the observer should also be unregistered (via [unregister]) when appropriate.
 * To simplify this process, [setLifecycle] can be used, that will unregister the observer when [Lifecycle.Event.ON_DESTROY] in the lifecycle.
 *
 * To partially disable some notifications about changes to [calendarView], [observeDateChanges] and [observeTimeZoneChanges] can be used.
 * Note that, setting them all to true won't register the observer. Alike to setting them all to false, it won't unregister the observer.
 */
class RangeCalendarConfigObserver(private val calendarView: RangeCalendarView) {
    private var lifecycle: Lifecycle? = null
    private var lifecycleObserver: LifecycleObserver? = null

    // Expected to be null when it's not registered.
    private var broadcastReceiver: BroadcastReceiver? = null

    /**
     * Gets or sets whether specified [RangeCalendarView] should be notified about current date changes. By default, it's `true`.
     *
     * Setting this property won't trigger the observer to be registered or unregistered, it should be done manually via [register]/[unregister].
     */
    var observeDateChanges: Boolean = true

    /**
     * Gets or sets whether specified [RangeCalendarView] should be notified about default time zone changes. By default, it's `true`.
     *
     * Setting this property won't trigger the observer to be registered or unregistered, it should be done manually via [register]/[unregister].
     */
    var observeTimeZoneChanges: Boolean = true

    /**
     * Sets [Lifecycle] instance. The specified [RangeCalendarView] is expected to have same lifecycle duration
     * as the specified one.
     *
     * The observer will be unregistered on [Lifecycle.Event.ON_DESTROY] event in the lifecycle.
     */
    fun setLifecycle(value: Lifecycle?) {
        // Remove observer from old lifecycle.
        removeLifecycleObserverFromLifecycle()

        lifecycle = value

        value?.also {
            it.addObserver(getLifecycleObserver())
        }
    }

    /**
     * Registers the observer. This will create and register [BroadcastReceiver]. The method does nothing if the observer
     * is already registered.
     */
    fun register() {
        if (broadcastReceiver != null) {
            return
        }

        val receiver = createBroadcastReceiver()
        calendarView.context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })

        broadcastReceiver = receiver
    }

    /**
     * Unregister the observer. This will unregister current [BroadcastReceiver]. The method does nothing if the observer
     * is not registered.
     */
    fun unregister() {
        broadcastReceiver?.let {
            calendarView.context.unregisterReceiver(it)

            broadcastReceiver = null
        }
    }

    private fun getLifecycleObserver(): LifecycleObserver {
        return getLazyValue(lifecycleObserver, ::createLifecycleObserver) { lifecycleObserver = it }
    }

    private fun createLifecycleObserver(): LifecycleObserver {
        return LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                unregister()

                removeLifecycleObserverFromLifecycle()
            }
        }
    }

    private fun removeLifecycleObserverFromLifecycle() {
        lifecycleObserver?.also {
            // Lifecycle can't be null if observer is not null.
            lifecycle!!.removeObserver(it)

            lifecycleObserver = null
        }
    }

    internal fun onBroadcastReceived(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED -> {
                if (observeTimeZoneChanges) {
                    val tzId = if (Build.VERSION.SDK_INT >= 30) {
                        intent.getStringExtra(Intent.EXTRA_TIMEZONE)
                    } else null

                    val tz = tzId?.let(TimeZone::getTimeZone) ?: TimeZone.getDefault()
                    calendarView.timeZone = tz
                }
            }

            Intent.ACTION_DATE_CHANGED -> {
                if (observeDateChanges) {
                    calendarView.notifyTodayChanged()
                }
            }
        }
    }

    private fun createBroadcastReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                onBroadcastReceived(intent)
            }
        }
    }
}