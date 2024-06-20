package com.github.pelmenstar1.rangecalendar.complexRange.date

import com.github.pelmenstar1.rangecalendar.PackedDate
import com.github.pelmenstar1.rangecalendar.max
import com.github.pelmenstar1.rangecalendar.min

private typealias FragmentNode = RawLinkedList.Node<DateFragment>

internal abstract class LinkedListDateComplexRangeBaseBuilder {
    protected val fragments: RawLinkedList<DateFragment>

    protected constructor() {
        fragments = RawLinkedList()
    }

    protected constructor(fragments: RawLinkedList<DateFragment>) {
        this.fragments = fragments
    }

    protected fun includeValue(newValue: PackedDate): FragmentNode {
        fragments.forEachNode { node ->
            tryIncludeValueFromNodeForward(newValue, node)?.let {
                return it
            }
        }

        return fragments.addAndReturnNode(DateFragment(newValue, newValue))
    }

    private fun tryIncludeValueFromNodeBase(newValue: PackedDate, node: FragmentNode): FragmentNode? {
        val frag = node.value

        return when {
            // Bail out. The complex range already contains this value.
            frag.contains(newValue) -> node
            frag.isAdjacentLeft(newValue) -> uniteWithValueRight(newValue, node)
            frag.isAdjacentRight(newValue) -> uniteWithValueLeft(newValue, node)
            else -> null
        }
    }

    private fun tryIncludeValueFromNodeForward(
        newValue: PackedDate,
        node: FragmentNode
    ): FragmentNode? {
        var result = tryIncludeValueFromNodeBase(newValue, node)
        if (result == null && newValue < node.value.start) {
            result = fragments.insertBeforeNode(DateFragment(newValue, newValue), node)
        }

        return result
    }

    private fun tryIncludeValueFromNodeBackward(
        newValue: PackedDate,
        node: FragmentNode
    ): FragmentNode? {
        var result = tryIncludeValueFromNodeBase(newValue, node)
        if (result == null && newValue > node.value.endInclusive) {
            result = fragments.insertAfterNode(DateFragment(newValue, newValue), node)
        }

        return result
    }

    private fun includeValueWithAnchor(
        newValue: PackedDate,
        anchorNode: FragmentNode
    ): FragmentNode {
        val anchorFrag = anchorNode.value

        tryIncludeValueFromNodeBase(newValue, anchorNode)?.let {
            return it
        }

        // If we can't include a newValue using anchorNode, then newValue is either before or after anchorFrag
        // (but not inside)

        if (newValue < anchorFrag.start) {
            fragments.forEachNodeReversedStartingWith(anchorNode.previous) { node ->
                tryIncludeValueFromNodeBackward(newValue, node)?.let {
                    return it
                }
            }

            return fragments.insertFirst(DateFragment(newValue, newValue))
        } else {
            // Then newValue is after anchorFrag.

            fragments.forEachNodeStartingWith(anchorNode.next) { node ->
                tryIncludeValueFromNodeForward(newValue, node)?.let {
                    return it
                }
            }

            return fragments.addAndReturnNode(DateFragment(newValue, newValue))
        }
    }

    private fun uniteWithValueRight(
        newValue: PackedDate,
        currentNode: FragmentNode
    ): FragmentNode {
        val frag = currentNode.value
        val nextNode = currentNode.next

        if (nextNode != null) {
            val nextFrag = nextNode.value

            if (nextFrag.isAdjacentRight(newValue)) {
                val newFrag = DateFragment(frag.start, nextFrag.endInclusive)
                fragments.replaceBetweenWith(newFrag, currentNode, nextNode)

                return currentNode
            }
        }

        currentNode.value = DateFragment(frag.start, newValue)
        return currentNode
    }

    private fun uniteWithValueLeft(newValue: PackedDate, currentNode: FragmentNode): FragmentNode {
        val frag = currentNode.value
        val prevNode = currentNode.previous

        if (prevNode != null) {
            val prevFrag = prevNode.value

            if (prevFrag.isAdjacentLeft(newValue)) {
                val newFrag = DateFragment(prevFrag.start, frag.endInclusive)
                fragments.replaceBetweenWith(newFrag, prevNode, currentNode)

                return prevNode
            }
        }

        currentNode.value = DateFragment(newValue, frag.endInclusive)
        return currentNode
    }

    protected fun includeValues(values: Array<out PackedDate>) {
        var i = 0

        includeValues(hasNext = { i < values.size }, next = { values[i++] })
    }

    protected fun includeValues(values: Iterable<PackedDate>) {
        val iter = values.iterator()

        includeValues(iter::hasNext, iter::next)
    }

    private inline fun includeValues(
        hasNext: () -> Boolean,
        next: () -> PackedDate
    ) {
        if (!hasNext()) {
            return
        }

        var lastNode = includeValue(next())

        while (hasNext()) {
            lastNode = includeValueWithAnchor(next(), lastNode)
        }
    }

    protected fun includeFragment(fragment: DateFragment) {
        fragments.forEachNode { node ->
            val thisFragment = node.value

            if (thisFragment.containsCompletely(fragment)) {
                // Bail out. No sense in adding the fragment that is already in the 'fragments' list.
                // (Even if thisFragment isn't equal to 'fragment', thisFragment contains all the values from 'fragment')
                return
            }

            if (thisFragment.canUniteWith(fragment)) {
                val minElement = min(thisFragment.start, fragment.start)
                val maxElement = max(thisFragment.endInclusive, fragment.endInclusive)

                includeFragmentWithUniting(minElement, maxElement, node)
                return
            }

            if (fragment.isBefore(thisFragment)) {
                fragments.insertBeforeNode(fragment, node)
                return
            }
        }

        // If we can't unite given fragment with any other fragments and can't find a position to insert the fragment,
        // then add it to the end - it's greater than other fragments
        fragments.add(fragment)
    }

    private fun includeFragmentWithUniting(
        minElement: PackedDate,
        initialMaxElement: PackedDate,
        startNode: FragmentNode
    ) {
        var maxElement = initialMaxElement

        // Last node cannot be null because the list is not empty.
        var endNode = fragments.tail

        fragments.forEachNodeStartingWith(startNode.next) { node ->
            val currentFragment = node.value

            if (currentFragment.overlapsWith(minElement, maxElement) || currentFragment.isAdjacentRight(maxElement)) {
                // currentFragment.endInclusive is always greater than current value of maxElement
                maxElement = currentFragment.endInclusive
            } else {
                // node.previous cannot be null, because we start iterating from startNode.next
                // (which is not null if we're here)
                endNode = node.previous

                // Bail out from the loop - if currentFragment cannot be united with fragment [minElement, maxElement],
                // then no next fragments can.
                return@forEachNodeStartingWith
            }
        }

        val unitedFragment = DateFragment(minElement, maxElement)
        fragments.replaceBetweenWith(unitedFragment, startNode, endNode!!)
    }

    protected fun excludeFragment(fragment: DateFragment) {
        val affectedStartNode = findFirstOverlapFragmentNode(fragment)
        val affectedEndNode = findLastOverlapFragmentNode(fragment)

        if (affectedStartNode == null || affectedEndNode == null) {
            return
        }

        if (affectedStartNode === affectedEndNode) {
            excludeFragmentWithOneAffected(fragment, affectedStartNode)
        } else {
            excludeFragmentWithRangeAffected(fragment, affectedStartNode, affectedEndNode)
        }
    }

    private fun excludeFragmentWithOneAffected(
        excludeFragment: DateFragment,
        affectedNode: FragmentNode
    ) {
        val affectedFragment = affectedNode.value

        if (excludeFragment.containsCompletely(affectedFragment)) {
            // Simply remove the whole fragment
            fragments.removeNode(affectedNode)
        } else if (affectedFragment.containsExclusive(excludeFragment)) {
            splitFragmentWithExcludingOtherFragment(affectedNode, excludeFragment)
        } else if (affectedFragment.leftContains(excludeFragment)) {
            // affectedFragment:    [] [] | [] []
            // excludeFragment:  [] [] [] |
            // result:                    | [] []
            // So we need to remove the part where these fragments intersect
            //
            // Existence of excludeFragment.endExclusive is implied by existence of affectedFragment.endInclusive,
            // that is greater than excludeFragment.endExclusive
            affectedNode.value = affectedFragment.withStart(excludeFragment.endExclusive)
        } else {
            // affectedFragment is not fragmentToRemove, affectedFragment doesn't contain the fragmentToRemove (exclusively)
            // and the affectedFragment doesn't left-contains the fragmentToRemove,
            // then the affectedFragment right-contains the fragmentToRemove
            //
            // affectedFragment: [] [] | [] []
            // excludeFragment:        | [] [] []
            // result:           [] [] |
            //
            // Existence of excludeFragment.start.previous() is implied by existence of affectedFragment.start
            // that is lesser than excludeFragment.start
            affectedNode.value = affectedFragment.withEndExclusive(excludeFragment.start)
        }
    }

    private fun excludeFragmentWithRangeAffected(
        excludeFragment: DateFragment,
        affectedStartNode: FragmentNode,
        affectedEndNode: FragmentNode
    ) {
        val affectedStartFrag = affectedStartNode.value
        val affectedEndFrag = affectedEndNode.value

        val removalStartNode: FragmentNode?
        val removalEndNode: FragmentNode?

        if (excludeFragment.isBefore(affectedStartFrag)) {
            // affectedStartFrag:    [] [] []
            // excludeFragment:   [] [] [] [] ...
            // So we need to remove the whole affectedStartFrag
            removalStartNode = affectedStartNode
        } else {
            // affectedStartFrag: [] [] | [] [] []
            // excludeFragment:         | [] [] [] ...
            // result:            [] []
            // So we need to narrow the affectedStartFrag and remove starting from the next fragment
            removalStartNode = affectedStartNode.next

            // Existence of excludeFragment.start.previous() is implied by existence of affectedStartFrag.start
            // that is lesser than excludeFragment.start
            affectedStartNode.value = affectedStartFrag.withEndExclusive(excludeFragment.start)
        }

        if (excludeFragment.isAfter(affectedEndFrag)) {
            // Something like:
            // affectedEndFrag:     [] [] []
            // excludeFragment: ... [] [] [] []
            // So we need to remove whole affectedEndFrag
            removalEndNode = affectedEndNode
        } else {
            // Something like:
            // affectedEndFrag:     [] [] | [] []
            // excludeFragment: ... [] [] |
            // result:                      [] []
            // So we need no narrow affectedEndFrag and remove ending with the previous fragment
            removalEndNode = affectedEndNode.previous

            // Existence of excludeFragment.endExclusive is implied by existence of affectedEndFrag.endInclusive
            // that is greater than excludeFragment.endInclusive
            affectedEndNode.value = affectedEndFrag.withStart(excludeFragment.endExclusive)
        }

        if (removalStartNode != null && removalEndNode != null) {
            if (removalStartNode.value.isBefore(removalEndNode.value)) {
                fragments.removeBetween(removalStartNode, removalEndNode)
            }
        }
    }

    private fun splitFragmentWithExcludingOtherFragment(
        splitNode: FragmentNode,
        excludeFragment: DateFragment
    ) {
        // Range in splitNode: [] [] [] [] [] []
        // excludeFragment:           [] []
        // result:              [] []       [] []
        // Existence of excludeFragment.start.previous() and excludeFragment.endExclusive is implied by existence of
        // start and endInclusive of the range in splitNode.

        val splitFragment = splitNode.value

        // Change range at splitIndex to the left part of the split.
        splitNode.value = splitFragment.withEndExclusive(excludeFragment.start)

        // Insert the right part of the split after splitIndex
        fragments.insertAfterNode(splitFragment.withStart(excludeFragment.endExclusive), splitNode)
    }

    private fun findFirstOverlapFragmentNode(fragment: DateFragment): FragmentNode? {
        return fragments.findFirstNode { fragment.overlapsWith(it) }
    }

    private fun findLastOverlapFragmentNode(fragment: DateFragment): FragmentNode? {
        return fragments.findLastNode { fragment.overlapsWith(it) }
    }
}