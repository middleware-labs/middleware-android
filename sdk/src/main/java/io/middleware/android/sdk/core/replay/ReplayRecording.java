package io.middleware.android.sdk.core.replay;

import android.util.JsonToken;

import com.google.gson.JsonDeserializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReplayRecording {

    public static final class JsonKeys {
        public static final String SEGMENT_ID = "segment_id";
    }

    private @Nullable Integer segmentId;
    private @Nullable Map<String, Object> unknown;

    // TODO spec it out, good enough for now
    private @Nullable List<RREvent> payload;

    @Nullable
    public Integer getSegmentId() {
        return segmentId;
    }

    public void setSegmentId(@Nullable Integer segmentId) {
        this.segmentId = segmentId;
    }

    @Nullable
    public List<RREvent> getPayload() {
        return payload;
    }

    public void setPayload(@Nullable List<RREvent> payload) {
        this.payload = payload;
    }

}