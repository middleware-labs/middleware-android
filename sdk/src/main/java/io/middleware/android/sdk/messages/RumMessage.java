package io.middleware.android.sdk.messages;

import io.opentelemetry.api.common.Attributes;

public class RumMessage {
    private final String sessionId;
    private final String timestamp;
    private final String type;
    private final String version;
    private final String os;
    private final String osVersion;
    private final String platform;
    private final String data;

    private final Attributes attributes;

    public String getVersion() {
        return version;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public String getPlatform() {
        return platform;
    }

    public String getData() {
        return data;
    }

    public String getOs() {
        return os;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public static class Builder {

        private String sessionId;
        private String timestamp;
        private final String type;
        private String version;
        private String os;
        private String osVersion;
        private String platform;
        private String data;

        private Attributes attributes;

        public Builder(String type) {
            this.type = type;
        }

        public Builder setAttributes(Attributes attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder os(String os) {
            this.os = os;
            return this;
        }

        public Builder osVersion(String osVersion) {
            this.osVersion = osVersion;
            return this;
        }

        public Builder platform(String platform) {
            this.platform = platform;
            return this;
        }

        public Builder data(String data) {
            this.data = data;
            return this;
        }

        public RumMessage build() {
            return new RumMessage(this);
        }
    }

    private RumMessage(Builder builder) {
        type = builder.type;
        sessionId = builder.sessionId;
        timestamp = builder.timestamp;
        version = builder.version;
        os = builder.os;
        osVersion = builder.osVersion;
        platform = builder.platform;
        data = builder.data;
        attributes = builder.attributes;
    }
}

