package io.middleware.android.sdk.core.replay.v3

import android.app.Application
import io.middleware.android.sdk.Middleware
import io.middleware.android.sdk.builders.MiddlewareBuilder
import io.middleware.android.sdk.core.replay.SessionRecorder
import io.middleware.android.sdk.core.replay.v2.LifecycleManager
import io.middleware.android.sdk.utils.Constants.BASE_ORIGIN

/**
 * Assembles the v3 recorder with its exporter. The resource attributes are
 * resolved lazily per batch so session id rotations and runtime resource
 * updates (e.g. native session binding) are always reflected.
 */
internal object ReplayV3Factory {

    @JvmStatic
    fun create(
        application: Application,
        builder: MiddlewareBuilder,
        lifecycleManager: LifecycleManager,
    ): SessionRecorder {
        val exporter = RRWebExporterV3(
            builder.target,
            builder.rumAccessToken,
        ) { sessionId -> resourceAttributes(sessionId) }
        return ReplayRecorderV3(application, builder, lifecycleManager, exporter) {
            Middleware.getInstance().rumSessionId ?: ""
        }
    }

    private fun resourceAttributes(sessionId: String): Map<String, String> {
        val attributes = LinkedHashMap<String, String>()
        try {
            Middleware.getInstance().middlewareRum?.resource?.attributes?.forEach { key, value ->
                attributes[key.key] = value.toString()
            }
        } catch (ignored: Throwable) {
            // resource not available yet; the batch still carries the session id
        }
        attributes["session.id"] = sessionId
        attributes["mw.client_origin"] = BASE_ORIGIN
        attributes["rum_origin"] = BASE_ORIGIN
        attributes["origin"] = BASE_ORIGIN
        return attributes
    }
}
