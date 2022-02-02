package com.github.pelmenstar1.rangecalendar;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.AbsSavedState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SavedState extends AbsSavedState {
    public int ym;

    public int selectionType;
    public long selectionData;

    public SavedState(@NotNull Parcelable superState) {
        super(superState);
    }

    public SavedState(@NotNull Parcel source) {
        super(source);

        ym = source.readInt();
        selectionType = source.readInt();
        selectionData = source.readLong();
    }

    @Override
    public void writeToParcel(@NotNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);

        dest.writeInt(ym);
        dest.writeInt(selectionType);
        dest.writeLong(selectionData);
    }

    @NotNull
    public static final Parcelable.Creator<SavedState> CREATOR = new Creator<SavedState>() {
        @Override
        @NotNull
        public SavedState createFromParcel(@NotNull Parcel source) {
            return new SavedState(source);
        }

        @Override
        public @Nullable SavedState @NotNull [] newArray(int size) {
            return new SavedState[size];
        }
    };
}
