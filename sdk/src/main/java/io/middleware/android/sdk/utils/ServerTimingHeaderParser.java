package io.middleware.android.sdk.utils;

import androidx.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerTimingHeaderParser {

    private static final String[] UNPARSEABLE_RESULT = new String[0];

    private static final Pattern headerPattern =
            Pattern.compile("traceparent;desc=['\"]00-([0-9a-f]{32})-([0-9a-f]{16})-01['\"]");

    public String[] parse(@Nullable String header) {
        if (header == null) {
            return UNPARSEABLE_RESULT;
        }
        Matcher matcher = headerPattern.matcher(header);
        if (!matcher.matches()) {
            return UNPARSEABLE_RESULT;
        }
        String traceId = matcher.group(1);
        String spanId = matcher.group(2);
        return new String[] {traceId, spanId};
    }
}