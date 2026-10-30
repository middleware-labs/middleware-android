# Changelog

All notable changes to the Middleware Android SDK (`io.github.middleware-labs:android-sdk`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

<!--
Maintaining this file:
- Every PR that changes SDK behavior adds a bullet under [Unreleased], in the
  right category (Added, Changed, Deprecated, Removed, Fixed, Security).
  Write it for app developers: what changes for them, not how it was done.
- To release: python3 scripts/changelog.py release <version>
  (moves [Unreleased] into a dated section; the release workflow refuses to
  publish a version that has no section and uses it as the GitHub release notes).
-->

## [Unreleased]

## [3.2.2] - 2026-10-28

### Fixed

- Hybrid hosts (React Native, Flutter) no longer get a second session on
  every launch that holds only a native `AppStart` span and a `Paused` event,
  with no replay. When `disableActivityLifecycleMonitoring()` is set, the SDK
  now also turns off OpenTelemetry Android's instrumentation discovery (which
  installed its own activity lifecycle instrumentation and opened `AppStart`
  under the SDK's auto-generated session id) and skips the `AppStart` /
  `Middleware.initialize` spans. ANR, crash, network, slow-rendering and tap
  instrumentation are unaffected.

## [3.2.1] - 2026-10-21

### Added

- Fragment screens are reported as screens. Single-activity apps (Jetpack
  Navigation or hand-rolled fragment transactions) now get one `screen_view`
  per fragment destination instead of a single `MainActivity` view, and taps
  and replay frames carry the fragment's `screen.name`. Navigation
  destinations are named from the nav graph (label, then route, then id
  resource name), so names survive R8; other fragments use their class name.
  Dialogs, bottom sheets and nested child fragments (tabs, embedded maps) do
  not change the screen. A name set with `setScreenName()` still wins.

## [3.2.0] - 2026-10-13

### Added

- `MiddlewareBuilder.setCaptureNetworkHeaders(boolean)` records the request
  and response headers of requests made through `createRumOkHttpCallFactory()`
  as `http.request.header.<name>` / `http.response.header.<name>`, with the
  name lowercased and `-` replaced by `_` (`content_type`). The value of
  `x-access-token` is always masked as `********`.
- `MiddlewareBuilder.setIgnoreHeaders(Collection<String>)` lists header names
  (case-insensitive) that are never recorded, even with header capture on.
- `MiddlewareBuilder.setCaptureResponseBodies(boolean)` records
  `http.response.body` for `application/json`, `*+json` and `text/plain`
  responses: the first 64 KiB, gzip decoded, read without consuming the body
  the app receives. Request bodies are never recorded. Off by default.

### Changed

- **Breaking:** Request and response headers are no longer recorded by
  default. Since 2.0.8 every header was recorded on every network span under
  its original name; call `setCaptureNetworkHeaders(true)` to keep them, and
  update queries to the normalized attribute names.

## [3.1.8] - 2026-10-01

### Added

- `screen_view` spans, the mobile counterpart of the browser `pageview`: each
  time the visible screen changes (an activity resumes, or `setScreenName()`
  is called with a new name) the SDK emits a span named after the screen with
  `event.type=screen_view`, `screen.name` and `last.screen.name`. They are
  counted as RUM views. Only emitted while UI instrumentation is enabled, so
  hybrid hosts that call `disableUIInstrumentation()` and emit their own are
  not double counted.

## [3.1.7] - 2026-10-01

### Added

- `mw.rum.sdk.version` resource attribute with the installed SDK version. A
  wrapper SDK (Flutter, React Native) can pass its own version under the same
  key in the global or resource attributes, and that value is used instead.

### Changed

- **Breaking:** Session replay masking defaults now match the browser SDK:
  `RecordingOptions.Builder.setMaskAllTextInputs` and `setMaskAllImages`
  default to `false` (were `true`), so screens are recorded as they are apart
  from sensitive inputs. Set both to `true` to keep the previous behavior.
  WebViews are now masked only when one of the two options is on.
- Sensitive inputs are always masked, whatever `maskAllTextInputs` says:
  password text fields, numeric PIN fields, fields using
  `PasswordTransformationMethod`, and (API 26+) fields with a password, OTP or
  credit-card autofill hint.

### Fixed

- Password detection reads the input class and variation correctly. Numeric
  PIN fields, and password fields with extra input-type flags, were not
  recognized as passwords before.

## [3.1.6] - 2026-09-23

### Fixed

- Session replay now captures `SurfaceView` content: Flutter's entire UI,
  video players and map views. These regions were missing from recordings
  because a window copy does not include them; each visible `SurfaceView` is
  now copied and composited in. While one is on screen the recorder also
  captures on a timer, since surface content changes without a view redraw.
  Identical frames are still dropped.
- The session sampling check no longer emits a `record init` span. Emitted at
  init, it landed in the SDK's auto-generated session before a hybrid host
  (Flutter, React Native) set its own, leaving a phantom one-span session on
  every launch.

## [3.1.5] - 2026-09-18

### Removed

- Network spans no longer carry the duplicate legacy `http.status_code` and
  `http.method` attributes; use the HTTP semantic-convention attributes the
  OkHttp instrumentation records.
- The `org.apache.commons:commons-compress` dependency, used only by the
  removed v2 recorder.

## [3.1.4] - 2026-09-04

### Removed

- **Breaking:** The legacy (v2) screenshot recorder. Session recording always
  uses the v3 rrweb recorder: `MiddlewareBuilder.disableSessionRecordingV3()`
  is gone and the `recordingV3` resource attribute is no longer sent.
- **Breaking:** `Middleware.getRecorder()` / `MiddlewareRecorder` and
  `Middleware.addRumEvent(ReplayRecording, Attributes)`, the original 1.0.2
  recorder API.

## [3.1.3] - 2026-09-02

### Added

- `MiddlewareBuilder.setTracePropagationTargets(List<Pattern>)` restricts
  which requests get trace headers, so backend correlation works for your own
  services without sending trace ids to third parties. Each regex is searched
  anywhere in the full request URL. Requests that match none are still traced,
  but their W3C trace-context, baggage and B3 headers are removed. Defaults to
  every URL (unchanged); `null` restores that, and an empty list turns
  propagation off while still recording HTTP spans. Applies to clients from
  `createRumOkHttpCallFactory()`.
- A warning is logged 10 seconds after init if no OkHttp client has been
  wrapped with `createRumOkHttpCallFactory()`. Without one the SDK records no
  HTTP spans and sends no trace headers, so backend traces cannot correlate
  with RUM sessions.

## [3.1.2] - 2026-08-31

### Fixed

- Sessions time out in the background again. Since 2.0.19 the SDK checked the
  session id every 15 seconds to keep recording in step with sampling, and
  each check reset OpenTelemetry Android's inactivity timer, so a backgrounded
  session only ended at the four-hour cap. The check now runs only while the
  app is in the foreground; a rotation that happened in the background is
  picked up on return.

## [3.1.1] - 2026-08-24

### Fixed

- `io.opentelemetry:opentelemetry-api` is now an `api` dependency, so apps can
  use `Attributes`, `AttributeKey` and `Span` with the SDK's public methods
  (`setGlobalAttributes`, `addEvent`, `startWorkflow`, ...) without declaring
  the OpenTelemetry API themselves.

## [3.1.0] - 2026-07-31

### Added

- `Middleware.startRecording()`, `stopRecording()` and `isRecording()` (also
  on `IMiddleware`) to control session recording at runtime.
  `startRecording()` starts recording immediately, even if it was turned off
  with `disableSessionRecording()` or the session was sampled out;
  `stopRecording()` stops it. The choice sticks across session rotations
  until the opposite call is made.

### Changed

- The `recording` / `recordingV3` resource attributes on replay data follow
  the live recording state after a runtime toggle. Spans keep the values from
  init.

### Fixed

- Views masked with `addSanitizedElement()` stay masked when the v2 recorder
  is stopped and restarted; they used to be unmasked on restart.

## [3.0.3] - 2026-07-28

### Changed

- Session inactivity timeout is back to 15 minutes (5 minutes since 2.0.18).

## [3.0.2] - 2026-07-27

### Added

- `Middleware.setScreenName(String)` sets the current screen name (for
  example from React Navigation). Once set, it replaces the activity class
  name in tap spans and in the replay's page URL
  (`android-app://<package>/<screen>`).
- `MiddlewareBuilder.setResourceAttributes(Attributes)` adds attributes to the
  OTLP resource, such as a wrapper SDK's identity. SDK-controlled attributes
  (`service.name`, `project.name`, recording flags, ...) win on conflict.

### Changed

- The `androidx.compose.ui:ui-android` dependency is lowered to 1.6.8 (was
  1.9.5). Compose 1.9 requires AGP 8.6+, which broke apps on older toolchains
  such as React Native 0.73; apps on newer Compose still resolve their own
  version.

## [3.0.1] - 2026-07-20

### Added

- Automatic tap instrumentation, on by default. Each tap emits a `tap` span
  (`event.type=tap`, `component=ui`) with the position in dp (`x`, `y`,
  `pageX`, `pageY`), the window size in dp (`viewport.width`,
  `viewport.height`), a `target_xpath` selector
  (`Screen/ViewClass#resource-id`), `screen.name`, and `target.class` /
  `target.resource_id` / `target.text` when available. This feeds the mobile
  click heatmap. Opt out with `MiddlewareBuilder.disableUIInstrumentation()`.

## [3.0.0] - 2026-07-19

### Added

- v3 session recording, on by default: masked screenshots are captured as the
  screen draws (throttled) and sent as rrweb events through
  `{target}/v1/metrics` (gzip, batched every 5 seconds), so mobile sessions
  replay in the same player as web sessions, with touches shown as
  interactions. Sets the `recordingV3` resource attribute. Call
  `MiddlewareBuilder.disableSessionRecordingV3()` to keep the v2 screenshot
  recorder.
- v3 masking options on `RecordingOptions.Builder`:
  `setMaskAllTextInputs(boolean)` (password inputs are always masked) and
  `setMaskAllImages(boolean)`, both `true` by default.
- Per-element masking in v3: Jetpack Compose `Modifier.mwSessionReplayMask()`
  and `Modifier.mwSessionReplayUnmask()` (in `MwReplayModifiers`); on classic
  views set `android:tag` or `contentDescription` to `mw-no-capture` (mask) or
  `mw-no-mask` (never mask). Unmask takes precedence.

### Changed

- The SDK now contains Kotlin code, so it brings the Kotlin standard library
  into the app.

## [2.0.19] - 2026-07-15

### Added

- `MiddlewareBuilder.setSessionSamplingRatio(double)` keeps that fraction of
  sessions (default `1.0`, clamped to 0–1). The decision is made per session
  id, so a session's traces and its recording are kept or dropped together;
  recording starts or stops to match and is re-evaluated when the session
  rotates.

## [2.0.18-rn] - 2026-03-27

### Fixed

- React Native line (forked from 2.0.7; see 2.0.7-rn). `Middleware.d/i/w/e/wtf`
  no longer throw a `NullPointerException` when called before the SDK's logger
  is initialized.

## [2.0.18] - 2026-04-22

### Changed

- Session inactivity timeout set to 5 minutes (OpenTelemetry Android's
  default is 15).

## [2.0.17-rn] - 2026-03-26

### Added

- React Native line (forked from 2.0.7; see 2.0.7-rn).
  `MiddlewareBuilder.setResourceAttributes(Attributes)`: when non-empty, these
  attributes become the whole OTLP resource, replacing the SDK's own (unlike
  3.0.2 on the main line, which merges them).
- Session recording uploads include the resource attributes.

### Changed

- **Breaking:** `Middleware.setNativeSessionId(String)` is replaced by
  `setNativeSession(String sessionId, String startTime)`, which also sets
  `session.id` and `session.start_time` on the resource and global attributes.
- `mw.account_key` is no longer sent as a resource attribute; the key is only
  sent in the `Authorization` header.

## [2.0.17] - 2026-04-20

### Added

- Crash spans carry `exception.framework=android`.
- Sourcemap plugin: first release of
  `io.github.middleware-labs:middleware-sourcemap-gradle-plugin` (plugin id
  `io.github.middleware-labs.sourcemap`). For every variant with
  `minifyEnabled`, it registers `uploadMiddlewareMapping<Variant>`, which
  uploads the R8/ProGuard `mapping.txt` to Middleware and runs after
  `assemble<Variant>`. Configure it in `middlewareSourcemap { apiKey,
  appVersion, backendUrl, deleteAfterUpload }`; `apiKey` falls back to the
  `MW_API_KEY` environment variable and `appVersion` to the variant's
  `versionName`.

## [2.0.16-rn] - 2026-03-12

### Fixed

- React Native line (forked from 2.0.7; see 2.0.7-rn). Stopping session
  recording while a capture was in flight could throw
  `RejectedExecutionException` or draw with a recycled bitmap; late captures
  are now dropped.

## [2.0.16] - 2026-03-29

### Added

- Device and OS resource attributes: `device.model.name`,
  `device.model.identifier`, `device.manufacturer`, `os.name` (`Android`),
  `os.type` (`linux`), `os.version` and `os.description`, plus
  `rum.sdk.version` (the bundled OpenTelemetry Android version).
- An `app.version` set in the global attributes is also sent as a resource
  attribute.
- Session recording uploads include the resource attributes, so replays
  carry the same app, device and session metadata as spans.

### Changed

- **Breaking:** `Middleware.setNativeSessionId(String)` is replaced by
  `setNativeSession(String sessionId, String startTime)`, which also sets
  `session.id` and `session.start_time` on the resource and global attributes.
- `session.start_time` is now a string (epoch milliseconds), taken from the
  SDK start time at init and also sent as a resource attribute (it was a
  numeric global attribute).
- `mw.account_key` is no longer sent as a resource attribute; the key is only
  sent in the `Authorization` header.

### Fixed

- `Middleware.d/i/w/e/wtf` no longer throw a `NullPointerException` when
  called before the SDK's logger is initialized.

## [2.0.15-rn] - 2026-03-11

### Fixed

- React Native line (forked from 2.0.7; see 2.0.7-rn). Same OutOfMemoryError
  fix as 2.0.15: recording archives are streamed from disk, one shared HTTP
  client is reused for uploads, and the mask bitmap is recycled when recording
  stops.

## [2.0.15] - 2026-03-12

### Fixed

- OutOfMemoryError in session recording. Recording archives are streamed from
  disk instead of being loaded into memory, one shared HTTP client (with
  connect/read/write timeouts) is reused instead of one per upload, and the
  mask bitmap is recycled when recording stops.
- Stopping session recording while a capture was in flight could throw
  `RejectedExecutionException` or draw with a recycled bitmap; late captures
  are now dropped.

## [2.0.14-rn] - 2026-02-18

### Changed

- React Native line (forked from 2.0.7; see 2.0.7-rn). Same change as 2.0.14:
  session recording compresses, stores and uploads screenshots on a background
  thread instead of the main thread.

## [2.0.14] - 2026-02-18

### Changed

- Session recording compresses, stores and uploads screenshots on a dedicated
  low-priority background thread instead of the main thread, reducing UI
  jank. A capture is skipped while the previous one is still being processed.

## [2.0.13-rn] - 2026-01-22

### Removed

- React Native line (forked from 2.0.7; see 2.0.7-rn). The
  `androidx.compose.ui:ui-android` dependency, and with it the
  Compose-specific capture and `SanitizableViewGroup` masking inside
  `ComposeView` hierarchies added in 2.0.9-rn.

## [2.0.13] - 2026-01-30

### Added

- `session.start_time` global attribute (session start, epoch milliseconds)
  on spans.

## [2.0.12-rn] - 2026-01-22

### Changed

- React Native line (forked from 2.0.7; see 2.0.7-rn). Built with compileSdk
  34 (was 35).

### Fixed

- `removeSanitizedElement(View)` is synchronized with the masking pass, so
  removing a masked view while a frame is being masked no longer races.

## [2.0.12] - 2025-12-02

### Added

- `MiddlewareBuilder.setRecordingOptions(RecordingOptions)` with a
  `RecordingFrequency` (`LOW` 1 fps, `STANDARD` about 3 fps, `HIGH` 10 fps)
  and a `RecordingQuality` (`LOW` / `MEDIUM` / `HIGH` = JPEG quality 25 / 50 /
  75).
- `SanitizableViewGroup`, a container whose whole area is masked in
  recordings, also when nested inside a `ComposeView`.
- Jetpack Compose content is drawn into session recordings.

### Changed

- **Breaking:** `Middleware.startRecording()` is removed; recording starts
  automatically at init when enabled. `stopRecording()` remains.
- `MiddlewareBuilder.build()` accepts any `Context` (`Application`, `Activity`,
  ...) instead of only an `Application`.
- Default recording rate is 1 fps (was about 3 fps) at JPEG quality 25.
- Masked views are covered with a pattern instead of solid black, and are held
  through weak references so a masked view no longer keeps its activity alive.
- New dependency: `androidx.compose.ui:ui-android` 1.9.5.

## [2.0.11-rn] - 2026-01-21

### Fixed

- React Native line (forked from 2.0.7; see 2.0.7-rn).
  `startNativeRecording(Activity)` threw a `NullPointerException` in
  2.0.10-rn because the SDK never kept its lifecycle tracker.

## [2.0.11] - 2025-07-28

### Changed

- `androidx.appcompat:appcompat` is no longer a dependency; apps that used it
  transitively must declare it themselves.
- Built with compileSdk 36. The OpenTelemetry logging exporter and OkHttp
  instrumentation are pinned to 1.48.0.

## [2.0.10-rn] - 2026-01-21

### Changed

- React Native line (forked from 2.0.7; see 2.0.7-rn). **Breaking:**
  `startNativeRecording()` takes an `Activity` again and records that
  activity.

## [2.0.9-rn] - 2026-01-21

### Added

- React Native line (forked from 2.0.7; see 2.0.7-rn). Brings the 2.0.12
  recorder rewrite: `RecordingFrequency` / `RecordingQuality` through the
  public `MiddlewareBuilder.recordingOptions` field (default 1 fps, JPEG
  quality 25), `SanitizableViewGroup`, Jetpack Compose capture, pattern
  masking and weakly held masked views.

### Changed

- **Breaking:** `startNativeRecording(Activity)` becomes
  `startNativeRecording()` and starts recording the current activity;
  `startRecording()` is removed. Recording does not start until it is called.
- `MiddlewareBuilder.build()` accepts any `Context`.
- New dependency `androidx.compose.ui:ui-android` 1.9.5; OkHttp
  instrumentation pinned to 1.32.1-alpha; built with compileSdk 35.

## [2.0.9] - 2025-04-04

### Changed

- Built with compileSdk 34 again (2.0.8 used 35).
- `opentelemetry-extension-trace-propagators` 1.48.0 (was 1.47.0), in line
  with the other OpenTelemetry artifacts.

## [2.0.8-rn] - 2025-07-25

### Removed

- React Native line (forked from 2.0.7; see 2.0.7-rn). The `user.status`
  metric is no longer emitted at startup.

## [2.0.8] - 2025-04-04

### Added

- `Middleware.startRecording()` / `stopRecording()` start and stop session
  recording and return whether the call took effect.
- Network spans record every request and response header as
  `http.request.header.<Name>` / `http.response.header.<Name>`, plus
  `http.status_code` and `http.method`.
- Crash spans carry `thread.id`, `thread.name`, `exception.message`,
  `exception.stacktrace`, `exception.type`, `error.message`, `error.stack`,
  `error.type` and `error.name`.
- `recording` resource attribute (`1` / `0`).

### Changed

- **Breaking:** Moved to OpenTelemetry Android 0.10.0-alpha (`android-agent`,
  was `instrumentation` 0.3.0-alpha), the OpenTelemetry BOM 1.48.0 (was
  1.32.0) and instrumentation API 2.14.0. Apps that pin OpenTelemetry
  artifacts must align with these versions.
- **Breaking:** `Middleware.startNativeRecording(Activity)` is removed; use
  `startRecording()`.
- Network spans are named `HTTP {METHOD} {path}`.
- Resource attributes: the deployment environment is sent as `env` (was
  `deployment.environment`), `os.name` is replaced by `os=Android`, and
  `browser.mobile` is dropped. Global attributes are no longer copied into the
  resource (they are still on every span).
- Built with compileSdk 35; desugar_jdk_libs 2.1.5, appcompat 1.7.0 and gson
  2.11.0.

### Removed

- OTLP metrics export, including the `user.status` counter.

## [2.0.7-rn] - 2025-04-07

### Added

- First build of the React Native line: a separate `react-native` branch
  forked from 2.0.7 for the Middleware React Native SDK, published as
  `-rn` versions of the same artifact. It is not a superset of the main
  line's 2.0.8+ releases. Compared with 2.0.7:
  - `Middleware.startRecording()` / `stopRecording()`.
  - Network spans record every request and response header as
    `http.request.header.<Name>` / `http.response.header.<Name>`, plus
    `http.status_code` and `http.method`.
  - Crash spans carry thread, `exception.*` and `error.*` attributes.
  - `os=Android` replaces `os.name`, and a `recording` (`1` / `0`) resource
    attribute is sent.

### Changed

- Stays on OpenTelemetry Android `instrumentation` 0.4.0-alpha (the main line
  moved to `android-agent` 0.10.0-alpha in 2.0.8) with the OTLP exporter and
  trace propagators at 1.48.0. The OpenTelemetry BOM is no longer exported as
  an `api` platform. Adds `androidx.fragment` 1.8.6 and drops `appcompat`.
- Session recording stops when the recorded activity is destroyed.

### Fixed

- `startNativeRecording(Activity)` no longer crashes when session recording
  is disabled.

## [2.0.7] - 2024-10-03

### Changed

- The `mw_agent=true` resource attribute is renamed to `mw.rum=true`.

## [2.0.6] - 2024-06-11

### Changed

- Global attributes set with `setGlobalAttributes()` are also added to the
  resource attributes (as strings).

## [2.0.5] - 2024-04-29

### Fixed

- Session recording starts only once the activity layout is attached, instead
  of capturing before the window is ready.
- Crashes in session recording when a masked view or the captured bitmap is
  null.

## [2.0.4] - 2024-04-08

### Fixed

- Dependency conflicts from `commons-compress`: its transitive `commons-io`
  and `commons-lang3` are excluded and pinned to 2.14.0 and 3.13.0
  (`commons-codec` 1.16.0).

## [2.0.3] - 2024-04-08

### Changed

- minSdk is back to 21.

## [2.0.2] - 2024-04-05

### Changed

- **Breaking:** minSdk raised to 24.

## [2.0.1] - 2024-03-01

### Added

- `Middleware.setNativeSessionId(String)` for hybrid hosts: once set, that id
  is used as the RUM session id for the rest of the session.

## [2.0.0] - 2024-03-01

### Added

- `Middleware.startNativeRecording(Activity)` starts screenshot recording of
  the given activity, for hybrid hosts (React Native) that own the activity.

## [1.0.10] - 2024-02-29

### Added

- Screenshot-based session recording (v2), on by default. Opt out with
  `MiddlewareBuilder.disableSessionRecording()`.
- `Middleware.addSanitizedElement(View)` / `removeSanitizedElement(View)` to
  black out a view in recordings.

### Changed

- OTLP exporter 1.35.0 and trace propagators 1.34.1. Adds `commons-compress`
  1.26.0 and `commons-codec` 1.16.1; drops `RestrictionBypass`.

## [1.0.9] - 2024-02-02

### Changed

- The `browser.trace` and `browser.mobile` resource attributes are sent as the
  string `"true"` (were booleans).
- App start spans carry `event.type=app_activity`.

## [1.0.8] - 2024-02-01

### Changed

- Crash spans report `event.type=error` (was `crash`), like other errors;
  `component` still tells crashes and errors apart.
- OpenTelemetry Android 0.3.0-alpha release (was a SNAPSHOT), OTLP exporter
  1.34.1, desugar_jdk_libs 2.0.4.

## [1.0.7] - 2024-01-25

### Changed

- Trace, metric and log exports send the account key in an `Authorization`
  header.

## [1.0.6] - 2024-01-25

### Added

- `MiddlewareBuilder.disableActivityLifecycleMonitoring()`.
- `Middleware.getMiddlewareRum()` is public and exposes `getSpanExporter()`,
  `getMetricsExporter()` and `getLogsExporter()`, so apps can send their own
  telemetry through Middleware's exporters.

## [1.0.4] - 2024-01-05

### Added

- `user.status` metric, recorded once at startup with the session id.

### Changed

- Session recording events are sent as OTLP `rum_event` metrics to
  `{target}/v1/metrics` (was a custom JSON payload to `/v1/metrics/rum`).
- Drops the `androidx.work:work-runtime`, `com.google.android.material` and
  `opentelemetry-contrib-trace-propagators` dependencies; OpenTelemetry disk
  buffering is pinned to 1.31.0-alpha.

## [1.0.3] - 2023-12-05

### Added

- `Constants.APP_VERSION` (`app.version`) attribute key, for reporting the
  app version through `setGlobalAttributes()`.

## [1.0.2] - 2023-11-30

### Added

- First session recording API: `Middleware.getRecorder()` (API 24+) returns a
  `MiddlewareRecorder` with `startRecording(Activity)` / `stopRecording()`.

## [1.0.1] - 2023-11-29

### Changed

- Maintenance release; no user-facing changes.

## [1.0.0] - 2023-11-29

### Added

- Initial release of the Middleware Android RUM SDK, built on OpenTelemetry
  Android 0.3.0-alpha and OpenTelemetry Java 1.32.0. minSdk 21 (Java 8+ APIs
  through core library desugaring).
- Set up with `Middleware.builder()`: `setTarget`, `setRumAccessToken`,
  `setProjectName`, `setServiceName`, `setDeploymentEnvironment`,
  `setGlobalAttributes`, `enableDebug`, then `build(Application)`.
- Automatic crash reporting, ANR detection, slow and frozen render detection,
  network change monitoring, and activity/fragment lifecycle and app start
  spans, each with a `disable...()` builder switch (and
  `setSlowRenderingDetectionPollInterval`). Data is exported over OTLP/HTTP to
  `{target}/v1/traces`, `/v1/metrics` and `/v1/logs`.
- `createRumOkHttpCallFactory(OkHttpClient)` traces OkHttp requests, injects
  W3C and B3 trace headers, and links spans to backend traces through the
  `Server-Timing` response header.
- `addEvent`, `startWorkflow`, `addException`, `setGlobalAttribute`,
  `updateLocation`, `integrateWithBrowserRum(WebView)`, and `d` / `i` / `w` /
  `e` / `wtf` logging methods that also send OTLP logs.
