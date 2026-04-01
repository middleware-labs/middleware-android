package io.middleware.sourcemap

import org.gradle.api.Plugin
import org.gradle.api.Project
import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApplicationVariant

/**
 * Middleware Android Source Map Plugin
 *
 * Automatically registers an upload task for every build variant that has
 * minification (ProGuard / R8) enabled.
 *
 * Usage in app/build.gradle:
 *
 *   plugins {
 *       id 'io.middleware.sourcemap'
 *   }
 *
 *   middlewareSourcemap {
 *       apiKey     = "YOUR_MW_API_KEY"      // or set MW_API_KEY env var
 *       backendUrl = "https://app.middleware.io/api/v1/rum/getSasUrl"
 *       // appVersion defaults to versionName; override if needed:
 *       // appVersion = "2.0.0-beta"
 *   }
 */
class MiddlewareSourcemapPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        // Create the extension so the user can configure the plugin in their build script.
        def extension = project.extensions.create(
                'middlewareSourcemap',
                MiddlewareSourcemapExtension
        )

        // Wait until after the project is evaluated so that all variants are available.
        project.afterEvaluate {

            def android = project.extensions.findByType(AppExtension)
            if (android == null) {
                project.logger.warn(
                        "[MiddlewareSourcemap] 'com.android.application' plugin not found. " +
                                "Make sure this plugin is applied after the Android plugin."
                )
                return
            }

            android.applicationVariants.all { ApplicationVariant variant ->
                registerUploadTask(project, extension, variant)
            }
        }
    }

    private static void registerUploadTask(
            Project project,
            MiddlewareSourcemapExtension extension,
            ApplicationVariant variant
    ) {
        // Only register for variants that produce a mapping file.
        if (!variant.buildType.minifyEnabled) {
            project.logger.info(
                    "[MiddlewareSourcemap] Skipping variant '${variant.name}' — minification disabled."
            )
            return
        }

        def variantName = variant.name.capitalize()
        def taskName    = "uploadMiddlewareMapping${variantName}"

        def uploadTask = project.tasks.register(taskName, UploadMappingTask) { task ->
            task.group       = "Middleware"
            task.description = "Upload ProGuard/R8 mapping for variant '${variant.name}' to Middleware."

            // Wire the mapping file output from the minification task.
            //
            // variant.mappingFileProvider returns Provider<FileCollection>.
            // Calling .map { files.singleFile } yields Provider<File> (java.io.File),
            // but RegularFileProperty.set() requires Provider<RegularFile>.
            // project.layout.file(Provider<File>) performs the conversion correctly.
            task.mappingFile.set(
                    project.layout.file(
                            variant.mappingFileProvider.map { files -> files.singleFile }
                    )
            )

            task.apiKey.set(
                    extension.apiKey ?: System.getenv("MW_API_KEY") ?: ""
            )
            task.appVersion.set(
                    extension.appVersion ?: variant.versionName ?: "latest"
            )
            task.backendUrl.set(
                    extension.backendUrl ?:
                            "https://app.middleware.io/api/v1/rum/getSasUrl"
            )
            task.deleteAfterUpload.set(extension.deleteAfterUpload ?: false)

            // Run after the minification task so the mapping file exists.
            def minifyTaskName = "minify${variantName}WithR8"
            def altMinifyTaskName = "minify${variantName}WithProguard"
            [minifyTaskName, altMinifyTaskName].each { tName ->
                def t = project.tasks.findByName(tName)
                if (t) task.dependsOn(t)
            }
        }

        // Optionally hook into assemble so the upload happens automatically.
        def assembleTask = project.tasks.findByName("assemble${variantName}")
        assembleTask?.finalizedBy(uploadTask)
    }
}
