package io.middleware.android.sdk.utils;

import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import java.time.Duration;

import io.opentelemetry.api.common.AttributeKey;

public final class Constants {

    public static final Duration DEFAULT_SLOW_RENDERING_DETECTION_POLL_INTERVAL =
            Duration.ofSeconds(1);
    public static final AttributeKey<String> COMPONENT_KEY = AttributeKey.stringKey("component");
    public static final AttributeKey<String> ERROR_TYPE_KEY = stringKey("error.type");
    public static final AttributeKey<String> ERROR_MESSAGE_KEY = stringKey("error.message");
    public static final AttributeKey<String> WORKFLOW_NAME_KEY = stringKey("workflow.name");
    public static final AttributeKey<Double> LOCATION_LATITUDE_KEY = doubleKey("location.lat");
    public static final AttributeKey<Double> LOCATION_LONGITUDE_KEY = doubleKey("location.long");

    public static final String COMPONENT_APPSTART = "appstart";
    public static final String COMPONENT_UI = "ui";
    public static final String COMPONENT_CRASH = "crash";
    public static final String COMPONENT_ERROR = "error";
    public static final String LOG_TAG = "Middleware";
    public static final String RUM_TRACER_NAME = "Middleware";

    public static final AttributeKey<String> LINK_TRACE_ID_KEY = stringKey("link.traceId");
    public static final AttributeKey<String> LINK_SPAN_ID_KEY = stringKey("link.spanId");

    public static final AttributeKey<String> LINK_PARENT_SPAN_ID_KEY = stringKey("link.parentSpanId");

    public static final AttributeKey<String> APP_NAME_KEY = stringKey("app");

}
