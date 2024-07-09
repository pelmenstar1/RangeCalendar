package com.github.pelmenstar1.rangecalendar

enum class SelectionMode {
    SINGLE_FRAGMENT,
    MULTI_FRAGMENT;

    companion object {
        fun ofOrdinal(value: Int) = when(value) {
            0 -> SINGLE_FRAGMENT
            1 -> MULTI_FRAGMENT
            else -> throw IllegalArgumentException("value")
        }
    }
}