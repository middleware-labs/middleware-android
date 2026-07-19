package io.middleware.android.sdk.core.replay.v2;

import io.middleware.android.sdk.core.replay.RecordingFrequency;
import io.middleware.android.sdk.core.replay.RecordingQuality;

public class RecordingOptions {
    private final RecordingQuality quality;
    private final RecordingFrequency frequency;
    private final boolean maskAllTextInputs;
    private final boolean maskAllImages;

    private RecordingOptions(Builder builder) {
        this.frequency = builder.frequency;
        this.quality = builder.quality;
        this.maskAllTextInputs = builder.maskAllTextInputs;
        this.maskAllImages = builder.maskAllImages;
    }

    public long getScreenshotInterval() {
        return frequency.getIntervalMs();
    }

    public int getQualityValue() {
        return quality.getValue();
    }

    public boolean isMaskAllTextInputs() {
        return maskAllTextInputs;
    }

    public boolean isMaskAllImages() {
        return maskAllImages;
    }

    public static class Builder {
        // Default values
        private RecordingFrequency frequency = RecordingFrequency.LOW;
        private RecordingQuality quality = RecordingQuality.LOW;
        private boolean maskAllTextInputs = true;
        private boolean maskAllImages = true;

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

        /**
         * Masks every text input in v3 session recording. When disabled, only
         * password-type inputs are masked. Default is {@code true}.
         */
        public Builder setMaskAllTextInputs(boolean maskAllTextInputs) {
            this.maskAllTextInputs = maskAllTextInputs;
            return this;
        }

        /**
         * Masks image content in v3 session recording. Default is {@code true}.
         */
        public Builder setMaskAllImages(boolean maskAllImages) {
            this.maskAllImages = maskAllImages;
            return this;
        }

        public RecordingOptions build() {
            return new RecordingOptions(this);
        }
    }
}
