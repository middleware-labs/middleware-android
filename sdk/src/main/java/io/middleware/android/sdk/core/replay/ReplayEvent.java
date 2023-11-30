package io.middleware.android.sdk.core.replay;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReplayEvent {

    private final String replayId;

    public static final class JsonKeys {
        public static final String TYPE = "type";
        public static final String REPLAY_TYPE = "replay_type";
        public static final String REPLAY_ID = "replay_id";
        public static final String SEGMENT_ID = "segment_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String REPLAY_START_TIMESTAMP = "replay_start_timestamp";
        public static final String URLS = "urls";
        public static final String ERROR_IDS = "error_ids";
        public static final String TRACE_IDS = "trace_ids";
    }

    private @Nullable String type;
    private @Nullable String replayType;
    private @Nullable Integer segmentId;
    private @Nullable Double timestamp;
    private @Nullable Double replayStartTimestamp;
    private @Nullable List<String> urls;
    private @Nullable List<String> errorIds;
    private @Nullable List<String> traceIds;
    private @Nullable Map<String, Object> unknown;

    public ReplayEvent() {
        this.replayId = UUID.randomUUID().toString();
        this.type = "replay_event";
        this.replayType = "session";
        this.errorIds = new ArrayList<>();
        this.traceIds = new ArrayList<>();
        this.urls = new ArrayList<>();
    }

    @Nullable
    public String getType() {
        return type;
    }

    public void setType(final @Nullable String type) {
        this.type = type;
    }

    @Nullable
    public Integer getSegmentId() {
        return segmentId;
    }

    public void setSegmentId(final @Nullable Integer segmentId) {
        this.segmentId = segmentId;
    }

    @Nullable
    public Double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final @Nullable Double timestamp) {
        this.timestamp = timestamp;
    }

    @Nullable
    public Double getReplayStartTimestamp() {
        return replayStartTimestamp;
    }

    public void setReplayStartTimestamp(final @Nullable Double replayStartTimestamp) {
        this.replayStartTimestamp = replayStartTimestamp;
    }

    @Nullable
    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(final @Nullable List<String> urls) {
        this.urls = urls;
    }

    @Nullable
    public List<String> getErrorIds() {
        return errorIds;
    }

    public void setErrorIds(final @Nullable List<String> errorIds) {
        this.errorIds = errorIds;
    }

    @Nullable
    public List<String> getTraceIds() {
        return traceIds;
    }

    public void setTraceIds(final @Nullable List<String> traceIds) {
        this.traceIds = traceIds;
    }

    @Nullable
    public String getReplayType() {
        return replayType;
    }

    public void setReplayType(@Nullable String replayType) {
        this.replayType = replayType;
    }
}