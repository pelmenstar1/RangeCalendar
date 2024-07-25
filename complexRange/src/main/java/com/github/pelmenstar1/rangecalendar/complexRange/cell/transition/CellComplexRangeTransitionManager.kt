package com.github.pelmenstar1.rangecalendar.complexRange.cell.transition

import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellComplexRange
import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellFragment
import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellFragmentIterator
import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellFragmentProximityDetector

class CellComplexRangeTransitionManager(
    private val proximityDetector: CellFragmentProximityDetector,
    private val emitNoOps: Boolean = true
) {
    fun createTransition(
        origin: CellComplexRange,
        destination: CellComplexRange
    ): CellComplexRangeTransition {
        val groups = ArrayList<CellTransitionGroup>()

        val originIter = origin.fragments().fragmentIterator()
        val destIter = destination.fragments().fragmentIterator()

        var originHasNext = originIter.moveNext()
        var destHasNext = destIter.moveNext()

        val noOps = ArrayList<CellFragment>()

        while (true) {
            if (!originHasNext || !destHasNext) {
                if (originHasNext) {
                    groups.addRemoveAllTransition(originIter)
                } else if (destHasNext) {
                    groups.addInsertAllTransition(destIter)
                }

                break
            }

            originIter.mark()
            destIter.mark()

            val originFrag = originIter.current
            val destFrag = destIter.current

            var shouldMoveOrigin = true
            var shouldMoveDest = true

            if (originFrag == destFrag) {
                noOps.add(originFrag)
            } else {
                if (originFrag.overlapsWith(destFrag)) {
                    consumeElementsForTransformGroup(originFrag, originIter, destIter)

                    val originGroupRange = originIter.subRange()
                    val destGroupRange = destIter.subRange()

                    groups.add(createTransformGroup(originGroupRange, destGroupRange))
                } else {
                    if (proximityDetector.canMove(originFrag, destFrag)) {
                        groups.addSingleOpGroup(
                            CellTransitionOperation.Transform(
                                originFrag,
                                destFrag
                            )
                        )
                    } else {
                        if (dependsOnOtherGroup(originFrag, destIter)) {
                            shouldMoveOrigin = false
                        } else {
                            groups.addSingleOpGroup(CellTransitionOperation.Remove(originFrag))
                        }

                        if (dependsOnOtherGroup(destFrag, originIter)) {
                            shouldMoveDest = false
                        } else {
                            groups.addSingleOpGroup(CellTransitionOperation.Insert(destFrag))
                        }
                    }
                }
            }

            if (shouldMoveOrigin) {
                originHasNext = originIter.moveNext()
            }

            if (shouldMoveDest) {
                destHasNext = destIter.moveNext()
            }
        }

        if (emitNoOps && noOps.isNotEmpty()) {
            groups.addSingleOpGroup(CellTransitionOperation.NoOp(noOps))
        }

        return CellComplexRangeTransition(groups)
    }

    // Internal for tests
    internal fun consumeElementsForTransformGroup(
        firstOriginGroupFrag: CellFragment,
        originIter: CellFragmentIterator,
        destIter: CellFragmentIterator
    ) {
        var lastOriginFrag = firstOriginGroupFrag
        var lastDestFrag: CellFragment

        while (true) {
            val nonOverlappingDestFrag = consumeLaneForTransform(lastOriginFrag, destIter)
            lastDestFrag = destIter.current

            if (nonOverlappingDestFrag == null) {
                consumeLaneForTransform(lastDestFrag, originIter)
                break
            }

            if (lastOriginFrag == nonOverlappingDestFrag) {
                break
            }

            if (originIter.moveNext()) {
                val nextOriginFrag = originIter.current

                if (!nextOriginFrag.overlapsWith(lastDestFrag)) {
                    originIter.movePrevious()
                    break
                }
            }

            val nonOverlappingOriginFrag = consumeLaneForTransform(lastDestFrag, originIter)
            lastOriginFrag = originIter.current

            if (nonOverlappingOriginFrag == null) {
                consumeLaneForTransform(lastOriginFrag, destIter)

                break
            }

            if (lastDestFrag == nonOverlappingOriginFrag) {
                break
            }

            if (destIter.moveNext()) {
                val nextDestFrag = destIter.current

                if (!nextDestFrag.overlapsWith(lastOriginFrag)) {
                    destIter.movePrevious()
                    break
                }
            }
        }
    }

    // Returns the first fragment that doesn't overlap with anchorFrag or equal to anchorFrag.
    // If there's no such fragment, returns null
    private fun consumeLaneForTransform(
        anchorFrag: CellFragment,
        iter: CellFragmentIterator
    ): CellFragment? {
        while (iter.moveNext()) {
            val frag = iter.current

            if (frag == anchorFrag || !frag.overlapsWith(anchorFrag)) {
                iter.movePrevious()
                return frag
            }
        }

        return null
    }

    private fun createTransformGroup(
        originGroupRange: CellComplexRange,
        destGroupRange: CellComplexRange
    ): CellTransitionGroup {
        val ops = ArrayList<CellTransitionOperation>(3)

        val originFrags = originGroupRange.fragments()
        val destFrags = destGroupRange.fragments()

        val originSize = originFrags.size
        val destSize = destFrags.size

        if (originSize == 1) {
            val originFrag = originFrags[0]

            if (destSize == 1) {
                // Ops:
                // - Transform
                val destFrag = destFrags[0]

                ops.add(CellTransitionOperation.Transform(originFrag, destFrag))
            } else {
                // Ops:
                // - Transform
                // - Split

                val minFrag = destFrags[0]
                val maxFragEnd = destFrags.getLastFragmentEndInclusive()

                val destTransformFrag = CellFragment(minFrag.start, maxFragEnd)

                if (originFrag != destTransformFrag) {
                    ops.add(CellTransitionOperation.Transform(originFrag, destTransformFrag))
                }

                ops.add(CellTransitionOperation.Split(destTransformFrag, destGroupRange))
            }
        } else {
            // originSize > 1
            val minOriginFrag = originFrags[0]
            val maxOriginFragEnd = originFrags.getLastFragmentEndInclusive()
            val originTransformFrag = CellFragment(minOriginFrag.start, maxOriginFragEnd)

            if (destSize == 1) {
                val destFrag = destFrags[0]

                // Ops:
                // - Join
                // - Transform? (if joined fragment is not destination fragment)
                ops.add(CellTransitionOperation.Join(originGroupRange, originTransformFrag))

                if (destFrag != originTransformFrag) {
                    ops.add(CellTransitionOperation.Transform(originTransformFrag, destFrag))
                }
            } else {
                // Ops:
                // - Join
                // - Transform
                // - Split

                val minDestFrag = destFrags[0]
                val maxDestFragEnd = destFrags.getLastFragmentEndInclusive()

                val destTransformFrag = CellFragment(minDestFrag.start, maxDestFragEnd)

                ops.add(CellTransitionOperation.Join(originGroupRange, originTransformFrag))

                if (originTransformFrag != destTransformFrag) {
                    ops.add(
                        CellTransitionOperation.Transform(
                            originTransformFrag,
                            destTransformFrag
                        )
                    )
                }

                ops.add(CellTransitionOperation.Split(destTransformFrag, destGroupRange))
            }
        }

        return CellTransitionGroup(ops)
    }

    companion object {
        private fun dependsOnOtherGroup(
            fragment: CellFragment,
            iterator: CellFragmentIterator
        ): Boolean {
            val nextFrag = iterator.pickNext()

            return nextFrag?.overlapsWith(fragment) ?: false
        }

        private fun MutableList<CellTransitionGroup>.addSingleOpGroup(op: CellTransitionOperation) {
            add(CellTransitionGroup.create(op))
        }

        private fun MutableList<CellTransitionGroup>.addInsertAllTransition(iter: CellFragmentIterator) {
            addOperationAllTransition(iter, CellTransitionOperation::Insert)
        }

        private fun MutableList<CellTransitionGroup>.addRemoveAllTransition(iter: CellFragmentIterator) {
            addOperationAllTransition(iter, CellTransitionOperation::Remove)
        }

        private inline fun MutableList<CellTransitionGroup>.addOperationAllTransition(
            iter: CellFragmentIterator,
            createOp: (CellFragment) -> CellTransitionOperation
        ) {
            do {
                val fragment = iter.current

                val op = createOp(fragment)
                add(CellTransitionGroup.create(op))
            } while (iter.moveNext())
        }
    }
}