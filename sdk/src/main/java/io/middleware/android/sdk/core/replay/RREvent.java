package io.middleware.android.sdk.core.replay;

import java.util.Map;

public class RREvent {
    private Long timestamp;
    private Integer type;

    private Map<String, Object> data;

    public RREvent(Long timestamp, Integer type, Map<String, Object> data) {
        this.timestamp = timestamp;
        this.type = type;
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public Integer getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
