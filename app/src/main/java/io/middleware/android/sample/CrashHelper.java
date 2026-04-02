package io.middleware.android.sample;

public class CrashHelper {
    public void executeRiskOperation() {
        // Nested call to ensure a deeper obfuscated stacktrace
        performDeepNestedTask();
    }

    private void performDeepNestedTask() {
        throw new RuntimeException("Middleware Test Crash: " + System.currentTimeMillis());
    }
}
