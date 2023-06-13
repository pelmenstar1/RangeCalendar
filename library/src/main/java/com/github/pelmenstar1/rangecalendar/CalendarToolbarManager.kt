package com.github.pelmenstar1.rangecalendar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
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
    fun interface OnScreenChangedListener {
        fun onChanged(newValue: Boolean)
    }

    private var svAnimator: ValueAnimator? = null

    // valid while svAnimator is running
    private var isTransitionForward = false

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
    var selectionViewTransitionDuration = SV_TRANSITION_DURATION
        set(value) {
            field = value

            val stateChangeDuration = SV_TRANSITION_DURATION / 2
            prevIcon.setStateChangeDuration(stateChangeDuration)
            nextIcon.setStateChangeDuration(stateChangeDuration)
        }
    var selectionViewTransitionInterpolator: TimeInterpolator = LINEAR_INTERPOLATOR
    var selectionViewLayoutParams = CalendarSelectionViewLayoutParams.DEFAULT
    var hasSelectionViewClearButton = true

    var selectedViewOnScreenChanged: OnScreenChangedListener? = null

    val isNextButtonActClear: Boolean
        get() = isSvOnScreen && hasSelectionViewClearButton

    init {
        val stateChangeDuration = SV_TRANSITION_DURATION / 2

        prevIcon = MoveButtonDrawable(
            context, iconColor,
            MoveButtonDrawable.DIRECTION_LEFT, MoveButtonDrawable.ANIM_TYPE_VOID_TO_ARROW
        ).apply {
            setAnimationFraction(1f)
            setStateChangeDuration(stateChangeDuration)
        }

        nextIcon = MoveButtonDrawable(
            context, iconColor,
            MoveButtonDrawable.DIRECTION_RIGHT, MoveButtonDrawable.ANIM_TYPE_ARROW_TO_CROSS
        ).apply {
            setStateChangeDuration(stateChangeDuration)
        }

        prevButton.setImageDrawable(prevIcon)
        nextButton.setImageDrawable(nextIcon)

        // To update content descriptions of buttons
        onLocaleChanged()
    }

    fun onPageScrolled(fraction: Float) {
        val alpha = (510f * abs(0.5f - fraction)).toInt()
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
        startTransition(forward = true)
    }

    fun onSelectionCleared() {
        startTransition(forward = false)
    }

    fun hideSelectionView() {
        if (hasSelectionViewClearButton) {
            prevIcon.setAnimationFraction(1f)
            nextIcon.setAnimationFraction(0f)
        }

        infoView.translationY = 0f
        setSelectionViewOnScreen(false)
    }

    private fun startTransition(forward: Boolean) {
        if (selectionView == null) {
            return
        }

        var animator = svAnimator

        // Don't continue if we want to show selection view and it's already shown and vise versa,
        // but continue if animation is currently running and direction of current animation is not equals to new one.
        if (animator != null &&
            (!animator.isRunning || isTransitionForward == forward) &&
            forward == isSvOnScreen
        ) {
            return
        }

        if (animator == null) {
            animator = AnimationHelper.createFractionAnimator(::onSVTransitionTick)

            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    if (!isTransitionForward) {
                        prevButton.visibility = ViewGroup.VISIBLE
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (isTransitionForward) {
                        prevButton.visibility = ViewGroup.GONE
                    }
                }
            })
        }

        isTransitionForward = forward

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

    private fun onSVTransitionTick(fraction: Float) {
        val sv = selectionView!!

        if (hasSelectionViewClearButton) {
            prevIcon.setAnimationFraction(1f - fraction)
            nextIcon.setAnimationFraction(fraction)
        }

        if (fraction < 0.5f) {
            val f = fraction * -2f
            infoView.translationY = infoView.bottom * f

            setSelectionViewOnScreen(false)
        } else {
            val f = 2f * fraction - 2f
            sv.translationY = f * sv.bottom

            setSelectionViewOnScreen(true)
        }
    }

    private fun setSelectionViewOnScreen(state: Boolean) {
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
                nextButton.contentDescription = clearSelectionDescription
            }
        } else {
            sv.visibility = View.INVISIBLE
            infoView.visibility = View.VISIBLE

            if (hasClearButton) {
                nextButton.contentDescription = nextMonthDescription
            }
        }

        selectedViewOnScreenChanged?.onChanged(state)
    }

    companion object {
        const val SV_TRANSITION_DURATION = 300L

        private fun setButtonAlphaIfEnabled(button: ImageButton, alpha: Int) {
            if (button.isEnabled) {
                button.drawable?.alpha = alpha
            }
        }
    }
}