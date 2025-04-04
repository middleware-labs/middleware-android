package io.middleware.android.sdk.builders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import android.app.Application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.Attributes;

class MiddlewareBuilderTest {

    private Application application;

    @BeforeEach
    void setup() {
        application = mock(Application.class);
    }

    @Test
    void shouldThrowExceptionIfRequiredFieldsAreMissing() {
        assertThrows(IllegalStateException.class, () -> Middleware.builder().build(application));
        assertThrows(IllegalStateException.class, () -> Middleware
                .builder()
                .setProjectName("some name")
                .build(application));
        assertThrows(IllegalStateException.class, () -> Middleware
                .builder()
                .setProjectName("project")
                .setServiceName("service")
                .build(application));
        assertThrows(IllegalStateException.class, () -> Middleware
                .builder()
                .setProjectName("project")
                .setServiceName("service")
                .setTarget("target")
                .build(application));
        assertThrows(IllegalStateException.class, () -> Middleware
                .builder()
                .setTarget("target")
                .setRumAccessToken("token")
                .build(application));
    }

    @Test
    void checkDefaultConfig() {
        final MiddlewareBuilder middlewareBuilder = Middleware.builder();
        assertTrue(middlewareBuilder.isAnrDetectionEnabled());
        assertTrue(middlewareBuilder.isCrashReportingEnabled());
        assertTrue(middlewareBuilder.isNetworkMonitorEnabled());
        assertTrue(middlewareBuilder.isSlowRenderingDetectionEnabled());
        assertEquals(Attributes.empty(), middlewareBuilder.globalAttributes);
        assertNull(middlewareBuilder.projectName);
        assertNull(middlewareBuilder.serviceName);
        assertNull(middlewareBuilder.target);
        assertNull(middlewareBuilder.rumAccessToken);
        assertNull(middlewareBuilder.deploymentEnvironment);
    }

    @Test
    void shouldSetTarget() {
        MiddlewareBuilder middlewareBuilder = Middleware.builder()
                .setTarget("https://middleware.io");
        assertNotNull(middlewareBuilder.target);
        assertEquals("https://middleware.io", middlewareBuilder.target);
    }

    @Test
    void shouldSetRumAccessToken() {
        MiddlewareBuilder middlewareBuilder = Middleware.builder()
                .setRumAccessToken("qwertytoken");
        assertNotNull(middlewareBuilder.rumAccessToken);
        assertEquals("qwertytoken", middlewareBuilder.rumAccessToken);
    }

    @Test
    void shouldDisableCrashReporting() {
        MiddlewareBuilder middlewareBuilder = Middleware.builder()
                .disableCrashReporting();
        assertFalse(middlewareBuilder.isCrashReportingEnabled());
    }
}
