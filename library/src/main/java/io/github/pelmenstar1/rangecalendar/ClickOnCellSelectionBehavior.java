package io.github.pelmenstar1.rangecalendar;

/**
 * Defines all possible behaviors when user clicks on already selected cell
 */
public final class ClickOnCellSelectionBehavior {
    /**
     * Nothing happens. The cell remains selected.
     */
    public static final int NONE = 0;

    /**
     * Unselects the cell.
     */
    public static final int CLEAR = 1;

    private ClickOnCellSelectionBehavior() {}
}
