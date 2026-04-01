package io.middleware.sourcemap

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that:
 *  1. Calls the backend to obtain a pre-signed upload URL.
 *  2. PUTs the mapping.txt directly to blob storage using that URL.
 *  3. Optionally deletes the local mapping file afterwards.
 */
abstract class UploadMappingTask extends DefaultTask {

    /** The ProGuard / R8 mapping.txt produced by minification. */
    @InputFile
    abstract RegularFileProperty getMappingFile()

    @Input
    abstract Property<String> getApiKey()

    @Input
    abstract Property<String> getAppVersion()

    @Input
    abstract Property<String> getBackendUrl()

    @Input
    abstract Property<Boolean> getDeleteAfterUpload()

    @TaskAction
    void upload() {
        def key     = apiKey.get()
        def version = appVersion.get()
        def file    = mappingFile.get().asFile

        if (!key) {
            throw new GradleException(
                    "[MiddlewareSourcemap] apiKey is not set. " +
                            "Set it in the middlewareSourcemap block or via the MW_API_KEY environment variable."
            )
        }

        if (!file.exists()) {
            logger.warn("[MiddlewareSourcemap] mapping file not found: ${file.absolutePath}. Skipping upload.")
            return
        }

        logger.lifecycle("[MiddlewareSourcemap] Uploading mapping for version=$version")

        // ----------------------------------------------------------------
        // Step 1 — Obtain a pre-signed upload URL from the backend
        // ----------------------------------------------------------------
        def sasUrl = fetchSasUrl(key, version)

        // ----------------------------------------------------------------
        // Step 2 — PUT the mapping file directly to blob storage
        // ----------------------------------------------------------------
        putFile(file, sasUrl)

        logger.lifecycle("[MiddlewareSourcemap] Upload complete: ${file.name} -> $sasUrl")

        // ----------------------------------------------------------------
        // Step 3 — Optionally remove the local mapping file
        // ----------------------------------------------------------------
        if (deleteAfterUpload.get()) {
            file.delete()
            logger.lifecycle("[MiddlewareSourcemap] Deleted local mapping file: ${file.absolutePath}")
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String fetchSasUrl(String key, String version) {
        // Build the JSON the backend expects after base64-decoding.
        // Backend struct: { FileNames []string, Version string }
        def json = JsonOutput.toJson([
                fileNames: ["mapping.txt"],
                version  : version,
        ])

        // The Go handler does b64.StdEncoding.DecodeString(body) before JSON parsing,
        // so the raw HTTP body must be the standard base64 encoding of the JSON string.
        def body = Base64.encoder.encodeToString(json.getBytes("UTF-8"))

        def url        = new URL(backendUrl.get())
        def connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput      = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15_000
            connection.readTimeout    = 30_000

            connection.outputStream.withWriter("UTF-8") { it.write(body) }

            def code = connection.responseCode
            if (code < 200 || code >= 300) {
                def err = connection.errorStream?.text ?: "(no body)"
                throw new GradleException(
                        "[MiddlewareSourcemap] getSasUrl returned HTTP $code: $err"
                )
            }

            def json2 = new JsonSlurper().parseText(connection.inputStream.text)
            if (!json2?.status || !json2?.data || json2.data.size() == 0) {
                throw new GradleException(
                        "[MiddlewareSourcemap] getSasUrl returned an unexpected response: $json2"
                )
            }

            return json2.data[0] as String

        } finally {
            connection.disconnect()
        }
    }

    private void putFile(File file, String uploadUrl) {
        def url        = new URL(uploadUrl)
        def connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PUT"
            connection.doOutput      = true
            connection.setRequestProperty("x-ms-blob-type", "BlockBlob")
            connection.setRequestProperty("Content-Type", "text/plain")
            connection.setRequestProperty("Content-Length", file.length().toString())
            connection.connectTimeout = 15_000
            connection.readTimeout    = 120_000

            connection.outputStream.withStream { out ->
                file.withInputStream { inp ->
                    byte[] buf = new byte[64 * 1024]
                    int read
                    while ((read = inp.read(buf)) != -1) {
                        out.write(buf, 0, read)
                    }
                }
            }

            def code = connection.responseCode
            if (code < 200 || code >= 300) {
                def err = connection.errorStream?.text ?: "(no body)"
                throw new GradleException(
                        "[MiddlewareSourcemap] blob upload returned HTTP $code: $err"
                )
            }

        } finally {
            connection.disconnect()
        }
    }
}
