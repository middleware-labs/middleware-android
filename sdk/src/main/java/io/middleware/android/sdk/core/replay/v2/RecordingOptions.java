package io.middleware.android.sdk.core.replay.v2;

import io.middleware.android.sdk.core.replay.RecordingFrequency;
import io.middleware.android.sdk.core.replay.RecordingQuality;

public class RecordingOptions {
    private final RecordingQuality quality;
    private final RecordingFrequency frequency;

    private RecordingOptions(Builder builder) {
        this.frequency = builder.frequency;
        this.quality = builder.quality;
    }

    public long getScreenshotInterval() {
        return frequency.getIntervalMs();
    }

    public int getQualityValue() {
        return quality.getValue();
    }

    public static class Builder {
        // Default values
        private RecordingFrequency frequency = RecordingFrequency.LOW;
        private RecordingQuality quality = RecordingQuality.LOW;

        public Builder() {
        }

        /**
         * Sets the recording frequency (FPS).
         * Default is LOW (~1 FPS).
         */
        public Builder setFrequency(RecordingFrequency frequency) {
            this.frequency = frequency;
            return this;
        }

        /**
         * Sets the recording image quality (JPEG Compression).
         * Default is LOW (25).
         */
        public Builder setQuality(RecordingQuality quality) {
            this.quality = quality;
            return this;
        }

        public RecordingOptions build() {
            return new RecordingOptions(this);
        }
    }
}