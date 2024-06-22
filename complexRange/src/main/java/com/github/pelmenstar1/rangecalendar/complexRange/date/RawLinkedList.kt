package com.github.pelmenstar1.rangecalendar.complexRange.date

import kotlin.NoSuchElementException

/**
 * Represents a linked list that exposes its underlying [RawLinkedList.Node] class
 * (unlike standard Java's LinkedList class) that allows some operations to be more efficient.
 */
class RawLinkedList<T> : MutableList<T> {
    /**
     * A node of the linked list.
     */
    class Node<T>(var value: T) {
        var previous: Node<T>? = null
        var next: Node<T>? = null
    }

    // This property should be changed manually whenever the list size actually changes
    private var _size = 0
    private var _head: Node<T>? = null
    private var _tail: Node<T>? = null

    /**
     * The head (first node) of the linked list. Might be null, if the list is empty.
     */
    val head: Node<T>?
        get() = _head

    /**
     * The tail (last node) of the linked list. Might null, if the list is empty.
     */
    val tail: Node<T>?
        get() = _tail

    /**
     * Gets the first value of the list. It throws [IllegalStateException] if the list is empty.
     */
    val firstValue: T
        get() = _head?.value ?: throw IllegalStateException("List is empty")

    /**
     * Gets the last value of the list. It throws [IllegalStateException] if the list is empty.
     */
    val lastValue: T
        get() = _tail?.value ?: throw IllegalStateException("List is empty")

    override val size: Int
        get() = _size

    constructor()

    internal constructor(head: Node<T>?, tail: Node<T>?, size: Int) {
        _head = head
        _tail = tail
        _size = size
    }

    override fun isEmpty(): Boolean {
        return _size == 0
    }

    /**
     * Iterates through each element's node of the list starting with given [startNode].
     */
    inline fun forEachNodeStartingWith(startNode: Node<T>?, action: (node: Node<T>) -> Unit) {
        var current = startNode
        val t = tail

        while (current != null) {
            action(current)

            if (current === t) {
                break
            }

            current = current.next
        }
    }

    /**
     * Iterates through each element's node of the list.
     */
    inline fun forEachNode(action: (node: Node<T>) -> Unit) {
        forEachNodeStartingWith(head, action)
    }

    /**
     * Iterates through each element of the list.
     */
    inline fun forEachForward(action: (value: T) -> Unit) {
        forEachNode { action(it.value) }
    }

    /**
     * Iterates through each element of the list in reversed direction.
     */
    inline fun forEachNodeReversed(action: (value: Node<T>) -> Unit) {
        forEachNodeReversedStartingWith(tail, action)
    }

    /**
     * Iterates through each element of the list in reversed direction.
     */
    inline fun forEachNodeReversedStartingWith(startNode: Node<T>?, action: (value: Node<T>) -> Unit) {
        var current = startNode
        val h = head

        while (current != null) {
            action(current)

            if (current === h) {
                break
            }

            current = current.previous
        }
    }

    inline fun anyElement(predicate: (T) -> Boolean): Boolean {
        forEachForward {
            if (predicate(it)) {
                return true
            }
        }

        return false
    }

    override fun clear() {
        _head = null
        _tail = null
        _size = 0
    }

    override fun get(index: Int): T {
        return getNode(index).value
    }

    override fun set(index: Int, element: T): T {
        val node = getNode(index)
        val previousValue = node.value
        node.value = element

        return previousValue
    }

    /**
     * Returns a node at given [index].
     *
     * @throws IndexOutOfBoundsException if given [index] is negative or greater than the list's size.
     */
    fun getNode(index: Int): Node<T> {
        return getNodeOrNull(index) ?: throw IndexOutOfBoundsException()
    }

    /**
     * Returns a node at given [index]. If given index is out of bounds, returns null.
     */
    fun getNodeOrNull(index: Int): Node<T>? {
        var count = 0

        forEachNode { node ->
            if (count == index) {
                return node
            }

            count++
        }

        return null
    }

    /**
     * Returns a first element's node matching given [predicate], or `null` if the list does not contain such element
     */
    inline fun findFirstNode(predicate: (value: T) -> Boolean): Node<T>? {
        forEachNode { node ->
            if (predicate(node.value)) {
                return node
            }
        }

        return null
    }

    /**
     * Returns a last element's node matching given [predicate], or `null` if the list does not contain such element
     */
    inline fun findLastNode(predicate: (value: T) -> Boolean): Node<T>? {
        forEachNodeReversed { node ->
            if (predicate(node.value)) {
                return node
            }
        }

        return null
    }

    /**
     * Returns a first element's node that is equal to the given [value].
     */
    fun findFirstNode(value: T): Node<T>? {
        return findFirstNode { it == value }
    }

    override fun contains(element: T): Boolean {
        return findFirstNode(element) != null
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return elements.all { contains(it) }
    }

    override fun indexOf(element: T): Int {
        var index = 0
        forEachForward {
            if (it == element) {
                return index
            }

            index++
        }

        return -1
    }

    override fun lastIndexOf(element: T): Int {
        var index = _size - 1

        forEachNodeReversed { node ->
            if (node.value == element) {
                return index
            }

            index--
        }

        return -1
    }

    override fun add(element: T): Boolean {
        addAndReturnNode(element)
        return true
    }

    fun addAndReturnNode(element: T): Node<T> {
        val newNode = Node(element)
        val t = tail

        if (t == null) {
            _head = newNode
            _tail = newNode
        } else {
            newNode.previous = t
            t.next = newNode

            _tail = newNode
        }

        _size++

        return newNode
    }

    override fun add(index: Int, element: T) {
        val node = getNode(index)

        insertAfterNode(element, node)
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        var lastNode = getNode(index)

        elements.forEach { value ->
            lastNode = insertAfterNode(value, lastNode)
        }

        return elements.isNotEmpty()
    }

    override fun addAll(elements: Collection<T>): Boolean {
        elements.forEach { add(it) }

        return elements.isNotEmpty()
    }

    /**
     * Inserts given [element] before specified [node]. The [node] must be in the list.
     */
    fun insertBeforeNode(element: T, node: Node<T>): Node<T> {
        val newNode = Node(element)

        val prevNode = node.previous

        newNode.next = node
        node.previous = newNode

        newNode.previous = prevNode
        prevNode?.next = newNode

        if (node === _head) {
            _head = newNode
        }

        _size++

        return newNode
    }

    /**
     * Inserts given [element] after specified [node]. The [node] must be in the list.
     */
    fun insertAfterNode(element: T, node: Node<T>): Node<T> {
        val nextNode = node.next
        val newNode = Node(element)

        newNode.next = nextNode
        nextNode?.previous = newNode

        newNode.previous = node
        node.next = newNode

        if (node === _tail) {
            _tail = newNode
        }

        _size++

        return newNode
    }

    fun insertFirst(value: T): Node<T> {
        val newNode = Node(value)
        val h = _head

        newNode.next = h
        h?.previous = newNode

        _head = newNode

        _size++

        return newNode
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        var modified = false
        forEachNode {
            if (it.value !in elements) {
                modified = true
                removeNode(it)
            }
        }

        return modified
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        var modified = false

        elements.forEach {
            modified = modified or remove(it)
        }

        return modified
    }

    override fun remove(element: T): Boolean {
        return findFirstNode(element)?.let { node ->
            removeNode(node)
            true
        } ?: false
    }

    /**
     * Removes given [node] from the list. This node must be in the list.
     */
    fun removeNode(node: Node<T>) {
        removeBetween(node, node)
    }

    /**
     * Removes elements starting from [startNode], ending with [endNode].
     *
     * Both nodes must be in the list. [startNode] must precede [endNode] or be equal to it.
     */
    fun removeBetween(startNode: Node<T>, endNode: Node<T>) {
        _size -= countBetweenNodes(startNode, endNode)

        val startNodePrev = startNode.previous
        val endNodeNext = endNode.next

        if (startNode === _head) {
            _head = endNodeNext
        } else {
            startNodePrev?.next = endNodeNext
        }

        if (endNode === _tail) {
            _tail = startNodePrev
        } else {
            endNodeNext?.previous = startNodePrev
        }
    }

    /**
     * Replaces elements between [startNode] and [endNode] with specified [value].
     *
     * In other words, it removes elements between [startNode] and [endNode],
     * and inserts a new node with [value] after [startNode]'s previous node.
     * If [startNode] does not have a previous node, a new node is set to be head.
     */
    fun replaceBetweenWith(value: T, startNode: Node<T>, endNode: Node<T>) {
        _size -= countBetweenNodes(startNode, endNode) - 1

        val endNodeNext = endNode.next

        startNode.value = value
        startNode.next = endNodeNext

        if (endNode === _tail) {
            _tail = startNode
        } else {
            endNodeNext?.previous = startNode
        }
    }

    override fun removeAt(index: Int): T {
        val node = getNode(index)
        removeNode(node)

        return node.value
    }

    /**
     * Returns a shallow copy of the list: it copies only nodes, but references the same values.
     */
    fun copyOf(): RawLinkedList<T> {
        val startNode = _head ?: return RawLinkedList()

        val newHead = Node(startNode.value)
        var newCurrent = newHead

        forEachNodeStartingWith(startNode.next) { current ->
            val newNode = Node(current.value).apply {
                previous = newCurrent
            }

            newCurrent.next = newNode
            newCurrent = newNode
        }

        return RawLinkedList(newHead, newCurrent, _size)
    }

    fun copyOf(startNode: Node<T>, endNodeInclusive: Node<T>): RawLinkedList<T> {
        if (startNode === endNodeInclusive) {
            val node = Node(startNode.value)

            return RawLinkedList(node, node, size = 1)
        }

        val newHead = Node(startNode.value)
        var newCurrent = newHead
        var current = startNode.next
        var newSize = 1

        while(current != null) {
            val newNode = Node(current.value).apply {
                previous = newCurrent
            }

            newCurrent.next = newNode
            newCurrent = newNode
            newSize++

            if (current === endNodeInclusive) {
                break
            }

            current = current.next
        }

        //val newSize = countBetweenNodes(startNode, endNodeInclusive)

        return RawLinkedList(newHead, newCurrent, newSize)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is RawLinkedList<*>) {
            return false
        }

        val s = _size
        if (s != other._size) {
            return false
        }

        var current = _head
        var otherCurrent = other._head

        var i = 0

        while (current != null && otherCurrent != null && i < s) {
            if (current.value != otherCurrent.value) {
                return false
            }

            current = current.next
            otherCurrent = otherCurrent.next

            i++
        }

        return true
    }

    override fun hashCode(): Int {
        var result = 1
        forEachForward { result = result * 31 + it.hashCode() }

        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("RangeFragmentList(")
        var isFirst = true

        forEachForward {
            if (!isFirst) {
                sb.append(", ")
            }

            sb.append(it)
            isFirst = false
        }

        sb.append(')')

        return sb.toString()
    }

    override fun subList(fromIndex: Int, toIndex: Int): RawLinkedList<T> {
        if (fromIndex > toIndex) {
            throw IndexOutOfBoundsException()
        }

        val startNode = getNode(fromIndex)
        val endNode = getNode(toIndex - 1)
        val newSize = toIndex - fromIndex

        return RawLinkedList(startNode, endNode, newSize)
    }

    override fun iterator(): MutableIterator<T> = listIterator()

    override fun listIterator(): MutableListIterator<T> {
        return ListIteratorImpl(_head)
    }

    override fun listIterator(index: Int): MutableListIterator<T> {
        return ListIteratorImpl(getNode(index))
    }

    companion object {
        /**
         * Returns amount of elements between given two nodes.
         */
        private fun <T> countBetweenNodes(start: Node<T>, end: Node<T>): Int {
            var count = 0
            var current: Node<T>? = start

            while (current != null) {
                if (current === end) {
                    return count + 1
                }

                count++
                current = current.next
            }

            throw IllegalArgumentException("Start and end nodes are not connected")
        }
    }

    private inner class ListIteratorImpl(
        startNode: Node<T>?,
    ) : MutableListIterator<T> {
        private var lastReturnedNode: Node<T>? = null
        private var nextNode: Node<T>? = startNode
        private var nextIndex = 0

        override fun hasNext(): Boolean {
            return nextIndex < _size
        }

        override fun hasPrevious(): Boolean {
            return nextIndex > 0
        }

        override fun next(): T {
            // If tail is null, then the list is empty which means there's no next element
            val t = _tail ?: throw NoSuchElementException()

            val nn = nextNode
            if (nn == null || nn === t.next) {
                throw NoSuchElementException()
            }

            lastReturnedNode = nn
            nextNode = nn.next
            nextIndex++

            return nn.value
        }

        override fun previous(): T {
            // If tail is null, then the list is empty which means there's no next element
            val t = _tail ?: throw NoSuchElementException()

            val nn = nextNode
            val l = if (nn == null || nn === t.next) t else nn.previous

            if (l == null) {
                throw NoSuchElementException()
            }

            nextNode = l
            lastReturnedNode = l
            nextIndex--

            return l.value
        }

        override fun nextIndex(): Int = nextIndex
        override fun previousIndex(): Int = nextIndex - 1

        override fun add(element: T) {
            lastReturnedNode = null

            val next = nextNode
            val t = _tail

            if (next == null || t == null || next === t.next) {
                this@RawLinkedList.add(element)
            } else {
                insertBeforeNode(element, next)
            }

            nextIndex++
        }

        override fun remove() {
            val lastReturned = lastReturnedNode
            checkNotNull(lastReturned)

            removeNode(lastReturned)

            if (nextNode === lastReturned) {
                nextNode = lastReturned.next
            } else {
                nextIndex--
            }

            lastReturnedNode = null
        }

        override fun set(element: T) {
            val lastReturned = lastReturnedNode
            checkNotNull(lastReturned)

            lastReturned.value = element
        }
    }
}