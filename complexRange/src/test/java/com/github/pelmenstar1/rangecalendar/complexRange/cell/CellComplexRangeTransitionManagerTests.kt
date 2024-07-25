package com.github.pelmenstar1.rangecalendar.complexRange.cell

import com.github.pelmenstar1.rangecalendar.complexRange.cell.transition.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CellComplexRangeTransitionManagerTests {
    private fun createComplexRange(ranges: List<IntRange>): CellComplexRange {
        return CellComplexRange(ranges.map { CellFragment(it) })
    }

    @Test
    fun createEmptyToEmptyTest() {
        val origin = CellComplexRange.Empty
        val dest = CellComplexRange.Empty

        val transition = createTransition(origin, dest)

        assertTrue(transition.groups.isEmpty())
    }

    @Test
    fun createEmptyToNonEmptyTest() {
        val origin = CellComplexRange.Empty
        val dest = createComplexRange(listOf(0..2, 4..5))
        val actualTransition = createTransition(origin, dest)

        assertGroupsEquals(actualTransition) {
            group {
                insert(0..2)
            }

            group {
                insert(4..5)
            }
        }
    }

    @Test
    fun createNonEmptyToEmptyTest() {
        val origin = createComplexRange(listOf(0..2, 4..5))
        val dest = CellComplexRange.Empty
        val actualTransition = createTransition(origin, dest)

        assertGroupsEquals(actualTransition) {
            group {
                remove(0..2)
            }

            group {
                remove(4..5)
            }
        }
    }

    @Test
    fun createNonEmptyToNonEmpty_singleGroupTest() {
        // Join + Transform

        transitionTestHelper(
            origin = listOf(1..2, 4..5),
            dest = listOf(2..4)
        ) {
            group {
                join(originRanges = arrayOf(1..2, 4..5), destRange = 1..5)
                transform(origin = 1..5, dest = 2..4)
            }
        }

        transitionTestHelper(
            origin = listOf(1..2, 4..4, 6..6),
            dest = listOf(2..7)
        ) {
            group {
                join(originRanges = arrayOf(1..2, 4..4, 6..6), destRange = 1..6)
                transform(origin = 1..6, dest = 2..7)
            }
        }

        transitionTestHelper(
            origin = listOf(1..1, 3..3),
            dest = listOf(1..3)
        ) {
            group {
                join(originRanges = arrayOf(1..1, 3..3), destRange = 1..3)
            }
        }

        transitionTestHelper(
            origin = listOf(5..6, 10..11),
            dest = listOf(5..13)
        ) {
            group {
                join(
                    originRanges = arrayOf(5..6, 10..11),
                    destRange = 5..11,
                )

                transform(origin = 5..11, dest = 5..13,)
            }
        }

        // Join + Transform + Split

        transitionTestHelper(
            origin = listOf(1..2, 4..11),
            dest = listOf(2..4, 7..10)
        ) {
            group {
                join(originRanges = arrayOf(1..2, 4..11), destRange = 1..11)
                transform(origin = 1..11, dest = 2..10)
                split(originRange = 2..10, destRanges = arrayOf(2..4, 7..10))
            }
        }

        // Transform

        transitionTestHelper(
            origin = listOf(1..2),
            dest = listOf(2..3)
        ) {
            group {
                transform(origin = 1..2, dest = 2..3)
            }
        }

        // Ignore same fragments
        transitionTestHelper(
            origin = listOf(1..2),
            dest = listOf(1..2)
        ) {
        }

        // Transform + Split

        transitionTestHelper(
            origin = listOf(1..5),
            dest = listOf(2..2, 4..4)
        ) {
            group {
                transform(origin = 1..5, dest = 2..4)
                split(originRange = 2..4, destRanges = arrayOf(2..2, 4..4))
            }
        }

        // Remove + Insert

        transitionTestHelper(
            origin = listOf(1..2),
            dest = listOf(3..4)
        ) {
            group {
                remove(1..2)
            }

            group {
                insert(3..4)
            }
        }

        transitionTestHelper(
            origin = listOf(1..2),
            dest = listOf(3..4)
        ) {
            group {
                remove(1..2)
            }

            group {
                insert(3..4)
            }
        }

        // Move

        transitionTestHelper(
            origin = listOf(1..2),
            dest = listOf(3..4),
            maxMoveDist = 1
        ) {
            group {
                transform(origin = 1..2, dest = 3..4)
            }
        }

        transitionTestHelper(
            origin = listOf(1..2),
            dest = listOf(4..5),
            maxMoveDist = 0
        ) {
            group {
                remove(1..2)
            }

            group {
                insert(4..5)
            }
        }

        transitionTestHelper(
            origin = listOf(1..2),
            dest = listOf(5..6),
            maxMoveDist = 1
        ) {
            group {
                remove(1..2)
            }

            group {
                insert(5..6)
            }
        }
    }

    @Test
    fun createNonEmptyToNonEmpty_multipleGroupsTest() {
        // Ignore same elements
        transitionTestHelper(
            origin = listOf(1..2, 6..7),
            dest = listOf(2..3, 6..7)
        ) {
            group {
                transform(origin = 1..2, dest = 2..3)
            }
        }

        transitionTestHelper(
            origin = listOf(1..2, 6..7, 9..10),
            dest = listOf(2..3, 6..7, 10..11)
        ) {
            group {
                transform(origin = 1..2, dest = 2..3)
            }

            group {
                transform(origin = 9..10, dest = 10..11)
            }
        }

        transitionTestHelper(
            origin = listOf(5..6, 11..11),
            dest = listOf(7 ..13)
        ) {
            group {
                remove(5..6)
            }

            group {
                transform(origin = 11..11, dest = 7..13)
            }
        }

        transitionTestHelper(
            origin = listOf(5..6, 10..11, 24..26),
            dest = listOf(5..6, 10..11, 22..22, 24..26)
        ) {
            group {
                insert(22..22)
            }
        }
    }

    @Test
    fun consumeElementsForTransformGroupTest() {
        fun createIterator(ranges: List<IntRange>): CellFragmentIterator {
            return createComplexRange(ranges).fragments().fragmentIterator()
        }

        fun assertConsumed(
            expectedConsumed: Int,
            input: List<IntRange>,
            groupComplexRange: CellComplexRange,
            sourceType: String, testType: String
        ) {
            val resultGroupElements = groupComplexRange.fragments().toList()

            assertEquals(
                expectedConsumed,
                resultGroupElements.size,
                "$sourceType consumed ($testType)"
            )

            val slicedInput = input.take(expectedConsumed).map { CellFragment(it) }

            assertContentEquals(
                slicedInput,
                resultGroupElements,
                "$sourceType elements ($testType)"
            )
        }

        fun testCaseBase(
            origin: List<IntRange>, dest: List<IntRange>,
            expectedOriginConsumed: Int, expectedDestConsumed: Int,
            isForward: Boolean
        ) {
            val testType = if (isForward) "forward" else "backward"

            val originIter = createIterator(origin)
            val destIter = createIterator(dest)

            originIter.moveNext()
            destIter.moveNext()

            val originFirstFrag = originIter.current

            originIter.mark()
            destIter.mark()

            val manager =
                CellComplexRangeTransitionManager(CellFragmentProximityDetector.neverMove())
            manager.consumeElementsForTransformGroup(originFirstFrag, originIter, destIter)

            val originGroupRange = originIter.subRange()
            val destGroupRange = destIter.subRange()

            assertConsumed(expectedOriginConsumed, origin, originGroupRange, "origin", testType)
            assertConsumed(expectedDestConsumed, dest, destGroupRange, "dest", testType)
        }

        fun testCase(
            origin: List<IntRange>, dest: List<IntRange>,
            expectedOriginConsumed: Int, expectedDestConsumed: Int
        ) {
            // Grouping must be commutative
            testCaseBase(
                origin,
                dest,
                expectedOriginConsumed,
                expectedDestConsumed,
                isForward = true
            )
            testCaseBase(
                dest,
                origin,
                expectedDestConsumed,
                expectedOriginConsumed,
                isForward = false
            )
        }

        testCase(
            origin = listOf(1..3),
            dest = listOf(1..1),
            expectedOriginConsumed = 1,
            expectedDestConsumed = 1
        )

        testCase(
            origin = listOf(1..3),
            dest = listOf(1..1, 3..3),
            expectedOriginConsumed = 1,
            expectedDestConsumed = 2
        )

        testCase(
            origin = listOf(1..4, 6..8),
            dest = listOf(1..1, 3..4, 7..7),
            expectedOriginConsumed = 1,
            expectedDestConsumed = 2
        )

        testCase(
            origin = listOf(1..3, 5..8),
            dest = listOf(1..1, 3..6),
            expectedOriginConsumed = 2,
            expectedDestConsumed = 2
        )

        testCase(
            origin = listOf(1..1, 3..12, 14..16, 18..19),
            dest = listOf(1..3, 6..6, 8..9, 11..14, 16..16, 19..19),
            expectedOriginConsumed = 3,
            expectedDestConsumed = 5
        )

        testCase(
            origin = listOf(1..3, 5..8, 10..11),
            dest = listOf(1..1, 3..6),
            expectedOriginConsumed = 2,
            expectedDestConsumed = 2
        )

        testCase(
            origin = listOf(1..10),
            dest = listOf(0..2, 4..5, 7..11),
            expectedOriginConsumed = 1,
            expectedDestConsumed = 3
        )

        testCase(
            origin = listOf(1..2, 6..7),
            dest = listOf(2..3, 6..7),
            expectedOriginConsumed = 1,
            expectedDestConsumed = 1
        )
    }

    private fun transitionTestHelper(
        origin: List<IntRange>,
        dest: List<IntRange>,
        maxMoveDist: Int = -1,
        transitionBuild: CellTransitionBuilder.() -> Unit
    ) {
        val expected = CellComplexRangeTransition(transitionBuild)

        transitionTestHelper(origin, dest, maxMoveDist, expected)
    }

    private fun transitionTestHelper(
        origin: List<IntRange>,
        dest: List<IntRange>,
        maxMoveDist: Int = -1,
        expected: CellComplexRangeTransition
    ) {
        transitionTestHelperBase(origin, dest, maxMoveDist, expected)
        transitionTestHelperBase(dest, origin, maxMoveDist, expected.inverted())
    }

    private fun transitionTestHelperBase(
        origin: List<IntRange>,
        dest: List<IntRange>,
        maxMoveDist: Int,
        expected: CellComplexRangeTransition
    ) {
        val originComplexRange = createComplexRange(origin)
        val destComplexRange = createComplexRange(dest)

        val actualTransition = createTransition(originComplexRange, destComplexRange, maxMoveDist)

        val expectedGroups = expected.groups.toHashSet()
        val actualGroups = actualTransition.groups.toHashSet()

        assertEquals(expectedGroups, actualGroups)
    }

    private fun assertGroupsEquals(
        actual: CellComplexRangeTransition,
        expectedBuild: CellTransitionBuilder.() -> Unit
    ) {
        val expected = CellComplexRangeTransition(expectedBuild)

        assertEquals(expected, actual)
    }

    private fun createTransition(
        origin: CellComplexRange,
        dest: CellComplexRange,
        maxMoveDist: Int = -1
    ): CellComplexRangeTransition {
        return CellComplexRangeTransitionManager(
            CellFragmentProximityDetector.withMoveDistance(
                maxMoveDist
            ),
            emitNoOps = false
        ).createTransition(origin, dest)
    }
}