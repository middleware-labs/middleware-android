package io.middleware.android.sdk.core.instrumentations.ui

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScreenViewsTest {

    private val exporter = InMemorySpanExporter.create()
    private val provider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build()

    @Before
    fun setup() {
        ScreenViews.resetForTest()
    }

    @After
    fun teardown() {
        ScreenViews.resetForTest()
        provider.close()
    }

    @Test
    fun emitsNothingUntilInstalled() {
        ScreenViews.record("Home")
        assertTrue(exporter.finishedSpanItems.isEmpty())
    }

    @Test
    fun emitsOneScreenViewPerScreenChange() {
        ScreenViews.install(provider.get("test"))

        ScreenViews.record("Home")
        ScreenViews.record("Home")
        ScreenViews.record("Cart")
        ScreenViews.record("Home")

        val spans = exporter.finishedSpanItems
        assertEquals(listOf("Home", "Cart", "Home"), spans.map { it.name })
        spans.forEach {
            assertEquals("screen_view", it.attributes.get(AttributeKey.stringKey("event.type")))
            assertEquals(it.name, it.attributes.get(AttributeKey.stringKey("screen.name")))
        }
        assertNull(spans[0].attributes.get(AttributeKey.stringKey("last.screen.name")))
        assertEquals("Home", spans[1].attributes.get(AttributeKey.stringKey("last.screen.name")))
    }
}
