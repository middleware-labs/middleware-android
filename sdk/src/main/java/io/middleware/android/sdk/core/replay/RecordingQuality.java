package io.middleware.android.sdk.core.replay;

public enum RecordingQuality {
    LOW(25),
    MEDIUM(50),
    HIGH(75);

    private final int value;

    RecordingQuality(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
