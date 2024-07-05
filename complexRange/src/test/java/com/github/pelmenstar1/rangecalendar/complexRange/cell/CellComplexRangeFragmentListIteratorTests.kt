package com.github.pelmenstar1.rangecalendar.complexRange.cell

import com.github.pelmenstar1.rangecalendar.complexRange.BaseListIteratorTests

class CellComplexRangeFragmentListIteratorTests : BaseListIteratorTests<CellFragment>() {
    override fun createIterator(elements: Array<CellFragment>): ListIterator<CellFragment> {
        return CellComplexRange(elements).fragments().listIterator()
    }

    override fun createSubIterator(
        elements: Array<CellFragment>,
        subRange: IntRange
    ): ListIterator<CellFragment> {
        return CellComplexRange(elements)
            .fragments()
            .subList(subRange.first, subRange.last + 1)
            .listIterator()
    }

    override fun iterateForwardBackwardDataset(): List<Array<CellFragment>> {
        return listOf(
            emptyArray(),
            arrayOf(CellFragment(1, 3)),
            arrayOf(CellFragment(1, 3), CellFragment(7..10)),
            arrayOf(CellFragment(1, 3), CellFragment(7..10), CellFragment(12..14))
        )
    }

    override fun iterateSubListForwardBackwardDataset(): List<SubListData<CellFragment>> {
        return listOf(
            SubListData(arrayOf(CellFragment(1, 3)), 0..0),
            SubListData(arrayOf(CellFragment(1, 3), CellFragment(7..9)), 0..1),
            SubListData(arrayOf(CellFragment(1, 3), CellFragment(7..9)), 0..0),
            SubListData(arrayOf(CellFragment(1, 3), CellFragment(7..9)), 1..1)
        )
    }
}