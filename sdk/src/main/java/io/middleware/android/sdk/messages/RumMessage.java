package io.middleware.android.sdk.messages;

import java.util.List;

import io.middleware.android.sdk.core.replay.RREvent;
import io.opentelemetry.api.common.Attributes;

public class RumMessage {
    private final String session_id;
    private final String version;
    private final List<RREvent> events;

    public String getSessionId() {
        return session_id;
    }

    public List<RREvent> getEvents() {
        return events;
    }

    public static class Builder {

        private String session_id;
        private String version;
        private List<RREvent> events;

        public Builder sessionId(String session_id) {
            this.session_id = session_id;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder events(List<RREvent> events) {
            this.events = events;
            return this;
        }

        public RumMessage build() {
            return new RumMessage(this);
        }
    }

    private RumMessage(Builder builder) {
        session_id = builder.session_id;
        events = builder.events;
        version = builder.version;
    }
}

