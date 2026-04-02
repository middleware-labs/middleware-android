package io.middleware.sourcemap

/**
 * DSL extension exposed to the build script as `middlewareSourcemap { ... }`.
 */
class MiddlewareSourcemapExtension {

    /** Middleware API key. Falls back to the MW_API_KEY environment variable. */
    String apiKey

    /**
     * The version string attached to the uploaded mapping.
     * Defaults to the variant's versionName.
     */
    String appVersion

    /**
     * Backend URL for obtaining a pre-signed upload URL.
     * Defaults to the Middleware hosted endpoint.
     */
    String backendUrl = "https://app.middleware.io/api/v1/android/getSasUrl"

    /**
     * When true, deletes the local mapping.txt after a successful upload.
     * Useful in CI to avoid accidentally shipping mapping files.
     */
    boolean deleteAfterUpload = false
}
