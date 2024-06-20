package com.github.pelmenstar1.rangecalendar.utils

private object EmptyListIterator : ListIterator<Nothing> {
    override fun hasNext(): Boolean = false
    override fun hasPrevious(): Boolean = false

    override fun next(): Nothing = throw NoSuchElementException()
    override fun previous(): Nothing = throw NoSuchElementException()

    override fun nextIndex(): Int = 0
    override fun previousIndex(): Int = -1
}

fun<T> emptyIterator(): ListIterator<T> = EmptyListIterator

fun<T> singleItemIterator(value: T): ListIterator<T> {
    return object: ListIterator<T> {
        private var index = 0

        override fun hasNext(): Boolean {
            return index == 0
        }

        override fun hasPrevious(): Boolean {
            return index == 1
        }

        override fun next(): T {
            if (index == 0) {
                index = 1
                return value
            }

            throw NoSuchElementException()
        }

        override fun previous(): T {
            if (index == 1) {
                index = 0
                return value
            }

            throw NoSuchElementException()
        }

        override fun nextIndex(): Int = index
        override fun previousIndex(): Int = index - 1
    }
}