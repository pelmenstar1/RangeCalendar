package com.github.pelmenstar1.rangecalendar.complexRange.cell.transition

import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellComplexRange
import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellFragment

sealed interface CellTransitionOperation {
    sealed class StructuralOperation(val fragment: CellFragment): CellTransitionOperation {
        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other == null || javaClass != other.javaClass) return false

            return fragment == (other as StructuralOperation).fragment
        }

        override fun hashCode(): Int {
            return fragment.hashCode()
        }

        override fun toString(): String {
            return "CellTransitionOperation.${javaClass.simpleName}(fragment=$fragment)"
        }
    }

    class Insert(fragment: CellFragment): StructuralOperation(fragment)
    class Remove(fragment: CellFragment): StructuralOperation(fragment)

    data class Transform(val origin: CellFragment, val destination: CellFragment): CellTransitionOperation

    data class Move(val origin: CellFragment, val destination: CellFragment): CellTransitionOperation

    data class Split(val origin: CellFragment, val destination: CellComplexRange): CellTransitionOperation

    data class Join(val origin: CellComplexRange, val destination: CellFragment): CellTransitionOperation
}