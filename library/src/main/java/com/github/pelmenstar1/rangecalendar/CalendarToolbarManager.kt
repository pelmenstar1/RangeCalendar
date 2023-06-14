package com.github.pelmenstar1.rangecalendar

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import kotlin.math.abs

internal class CalendarToolbarManager(
    private val context: Context,
    iconColor: ColorStateList,
    private val prevButton: AppCompatImageButton,
    private val nextButton: AppCompatImageButton,
    private val infoView: TextView
) {
    private var svAnimator: ValueAnimator? = null

    private var isSvTransitionForward = false

    private var isSvOnScreen = false

    private val prevIcon: MoveButtonDrawable
    private val nextIcon: MoveButtonDrawable

    var prevMonthDescription: CharSequence = ""
        set(value) {
            field = value
            prevButton.contentDescription = value
        }

    var nextMonthDescription: CharSequence = ""
        set(value) {
            field = value

            if (!isNextButtonActClear) {
                nextButton.contentDescription = value
            }
        }

    var clearSelectionDescription: CharSequence = ""
        set(value) {
            field = value

            if (isNextButtonActClear) {
                nextButton.contentDescription = value
            }
        }

    var selectionView: View? = null
    var selectionViewTransitionDuration = DEFAULT_SV_TRANSITION_DURATION

    var selectionViewTransitionInterpolator: TimeInterpolator = LINEAR_INTERPOLATOR
    var selectionViewLayoutParams = CalendarSelectionViewLayoutParams.DEFAULT
    var hasSelectionViewClearButton = true
        set(value) {
            field = value

            // If the selection view is already on screen, then update values to match
            // the contract of hasSelectionViewClearButton. There's no animation because
            // it's very unlikely scenario.
            if (isSvOnScreen) {
                if (value) {
                    prevIcon.setAnimationFraction(0f)
                    nextIcon.setAnimationFraction(1f)

                    prevButton.visibility = View.GONE
                } else {
                    prevIcon.setAnimationFraction(1f)
                    nextIcon.setAnimationFraction(0f)

                    prevButton.visibility = View.VISIBLE
                }
            }
        }

    val isNextButtonActClear: Boolean
        get() = isSvOnScreen && hasSelectionViewClearButton

    init {
        prevIcon = MoveButtonDrawable(
            context,
            iconColor,
            MoveButtonDrawable.DIRECTION_LEFT,
            MoveButtonDrawable.ANIM_TYPE_VOID_TO_ARROW
        ).apply {
            // The end state of MoveButtonDrawable when animation type is ANIM_TYPE_VOID_TO_ARROW is the arrow.
            // That's what we need.
            setAnimationFraction(1f)
            setStateChangeDuration(STATE_CHANGE_DURATION)
        }

        nextIcon = MoveButtonDrawable(
            context,
            iconColor,
            MoveButtonDrawable.DIRECTION_RIGHT,
            MoveButtonDrawable.ANIM_TYPE_ARROW_TO_CROSS
        ).apply {
            setStateChangeDuration(STATE_CHANGE_DURATION)
        }

        prevButton.setImageDrawable(prevIcon)
        nextButton.setImageDrawable(nextIcon)

        // To update content descriptions of buttons
        onLocaleChanged()
    }

    fun onPageScrolled(fraction: Float) {
        // 2 * |0.5 - x| is a function that increases from 0 to 1 on [0; 0.5) and
        // decreases from 1 to 0 on (0.5; 1].
        // Alpha of drawable is an integer from 0 to 255, so the value should be also multiplied by 255
        // and converted to int.
        val alpha = (255f * 2f * abs(0.5f - fraction)).toInt()
        setButtonAlphaIfEnabled(prevButton, alpha)

        if (!isNextButtonActClear) {
            setButtonAlphaIfEnabled(nextButton, alpha)
        }
    }

    fun restoreButtonsAlpha() {
        setButtonAlphaIfEnabled(prevButton, alpha = 255)
        setButtonAlphaIfEnabled(nextButton, alpha = 255)
    }

    fun onLocaleChanged() {
        context.resources.apply {
            prevMonthDescription = getText(R.string.previousMonthDescription)
            nextMonthDescription = getText(R.string.nextMonthDescription)
            clearSelectionDescription = getText(R.string.clearSelectionDescription)
        }
    }

    fun onSelection() {
        startSelectionViewTransition(forward = true)
    }

    fun onSelectionCleared() {
        startSelectionViewTransition(forward = false)
    }

    /**
     * Hides selection view from the RangeCalendarView. The method expects that [selectionView] is not null.
     */
    fun hideSelectionView() {
        if (hasSelectionViewClearButton) {
            prevIcon.setAnimationFraction(1f)
            nextIcon.setAnimationFraction(0f)
        }

        infoView.translationY = 0f
        setSelectionViewOnScreen(state = false, duringAnimation = false)
    }

    private fun startSelectionViewTransition(forward: Boolean) {
        if (selectionView == null) {
            return
        }

        var animator = svAnimator

        // Don't continue if we want to show selection view and it's already shown and vise versa,
        // but continue if animation is currently running and direction of current animation is not equals to new one.
        if (animator != null &&
            (!animator.isRunning || isSvTransitionForward == forward) &&
            forward == isSvOnScreen
        ) {
            return
        }

        if (animator == null) {
            animator = AnimationHelper.createFractionAnimator(::onSvTransitionTick)
        }

        isSvTransitionForward = forward

        var startPlaytime = 0L
        if (animator.isRunning) {
            startPlaytime = animator.currentPlayTime
            animator.end()
        }

        animator.interpolator = selectionViewTransitionInterpolator
        animator.duration = selectionViewTransitionDuration

        // ValueAnimator.setCurrentFraction() could be used, but it's available only from API >= 22,
        animator.currentPlayTime = startPlaytime

        if (forward) {
            animator.start()
        } else {
            animator.reverse()
        }
    }

    private fun onSvTransitionTick(fraction: Float) {
        // The buttons are only animated when next button acts like 'clear selection' button on selection.
        if (hasSelectionViewClearButton) {
            prevIcon.setAnimationFraction(1f - fraction)
            nextIcon.setAnimationFraction(fraction)
        }

        if (fraction < 0.5f) {
            // When fraction < 0.5, the info textview should be moved from the initial position
            // to the top until it's completely invisible. So the fraction is scaled from [0; 0.5] to [0; 1]
            // and than negated in order to make translationY negative as well.
            val f = fraction * -2f
            infoView.translationY = infoView.bottom * f

            setSelectionViewOnScreen(state = false, duringAnimation = true)
        } else {
            val sv = selectionView!!

            // Now that the info textview is invisible, the selection view should be moved from the top where
            // it's completely invisible to the final position.
            //
            // 2 - 2x is a function that decreases from 1 to 0 on [0.5; 1]
            // It should also be negated in order to achieve the animation from the top to the final position.
            // In result: 2x - 2 that multiplied by sv.bottom
            val f = 2f * fraction - 2f
            sv.translationY = f * sv.bottom

            setSelectionViewOnScreen(state = true, duringAnimation = true)
        }
    }

    private fun setSelectionViewOnScreen(state: Boolean, duringAnimation: Boolean) {
        if (isSvOnScreen == state) {
            return
        }

        isSvOnScreen = state
        val sv = selectionView!!
        val hasClearButton = hasSelectionViewClearButton

        if (state) {
            sv.visibility = View.VISIBLE
            infoView.visibility = View.INVISIBLE

            if (hasClearButton) {
                if (!duringAnimation) {
                    prevButton.visibility = View.GONE
                }

                nextButton.contentDescription = clearSelectionDescription
            }
        } else {
            sv.visibility = View.INVISIBLE
            infoView.visibility = View.VISIBLE

            if (hasClearButton) {
                prevButton.visibility = View.VISIBLE
                nextButton.contentDescription = nextMonthDescription
            }
        }
    }

    companion object {
        private const val DEFAULT_SV_TRANSITION_DURATION = 300L
        private const val STATE_CHANGE_DURATION = 300L

        private fun setButtonAlphaIfEnabled(button: ImageButton, alpha: Int) {
            if (button.isEnabled) {
                button.drawable?.alpha = alpha
            }
        }
    }
}