# Middleware Android SDK
---
<p align="center">
  <a href="https://github.com/middleware-labs/middleware-android/releases">
    <img alt="Build Status" src="https://img.shields.io/badge/status-beta-orange">
  </a>
  <a href="https://maven-badges.herokuapp.com/maven-central/io.github.middleware-labs/android-sdk">
    <img alt="Maven Central" src="https://img.shields.io/maven-central/v/io.github.middleware-labs/android-sdk?style=flat">
  </a>  
  <a href="https://github.com/middleware-labs/middleware-android/releases">
    <img alt="GitHub release (latest SemVer)" src="https://img.shields.io/github/v/release/middleware-labs/middleware-android?include_prereleases&style=flat">
  </a>
  <a href="https://github.com/middleware-labs/middleware-android/actions/workflows/build.yml">
    <img alt="Build Status" src="https://img.shields.io/github/actions/workflow/status/middleware-labs/middleware-android/build.yml?branch=main&style=flat">
  </a>
</p>

---

## Features

- Access to OpenTelemetry APIs
- OkHttp3 instrumentation for monitoring HTTP events
- Middleware APIs for sending custom events & recording exceptions
- Slow / Freeze render detection
- Custom logging
- Network Change Detection
- ANR Detection
- Crash Reporting
- Android Activity & Fragment lifecycle events

## Benchmarks

Real-app benchmark: the Coffee Cart sample driven on an emulator with **v3
session recording**, SDK **3.1.4**. A mock collector counts exact wire bytes.

### Production-readiness gate

| Metric | Threshold |
|---|---|
| Upload | ≤ 4 MB/min |
| Idle upload | ≤ 0.5 MB/min |
| Cold start Δ | ≤ 250 ms |

### Real app (Coffee Cart on emulator)

The Coffee Cart sample driven through a scripted journey (browse → product →
add to cart → checkout) and a 60s idle hold, as of **2026-09-04**. Device
`sdk_gphone16k_arm64`, Android 17; a mock collector counts exact wire bytes.
The gate compares `recording_on` against the `sdk_off` baseline.

| Scenario | Baseline | Cold start (ms) | CPU avg (%) | Mem peak (MB) | Jank (%) | Frame p95 (ms) | Upload (B) | MB/min | rrweb events | Ready |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| journey | sdk_off | 302 | 3.8 | 69.4 | 9.0 | 19 | 0 | 0 | 0 | yes |
| idle | sdk_off | 302 | 0.4 | 56.0 | 2.24 | 22 | 0 | 0 | 0 | yes |
| journey | recording_off | 361 | 4.0 | 73.8 | 8.77 | 19 | 156973 | 0.054 | 0 | yes |
| idle | recording_off | 361 | 0.6 | 63.2 | 2.53 | 22 | 16737 | 0.014 | 0 | yes |
| journey | recording_on | 369 | 7.0 | 132.1 | 8.29 | 21 | 1320644 | 0.456 | 208 | yes |
| idle | recording_on | 369 | 0.7 | 84.8 | 3.21 | 23 | 49707 | 0.042 | 6 | yes |

**What recording costs.** Against `recording_off` on the same journey, v3 session
recording adds **3.0 points of average CPU** (4.0% → 7.0%) and **58 MB of peak
PSS** (73.8 → 132.1 MB), and uploads **0.456 MB/min** — roughly a ninth of the
4 MB/min gate. Idle recording costs **0.042 MB/min** against a 0.5 MB/min gate,
and frame p95 moves by 2 ms or less in every pairing.

**Note on APK size.** This suite cannot measure it: `app/build.gradle` links
`project(':sdk')` in every variant and `BENCH_MODE=no_sdk` only skips SDK
initialisation, so all three APKs are byte-identical. Measure a release APK with
and without the dependency instead.

## Requirements

- Android Minimum SDK Version : 21

## Pre-requisites

Before using the Middleware Android SDK, ensure you have:

An account with Middleware to obtain the RUM (Real User Monitoring) access token and target URL.
Visit installation docs section of Real-User-Monitoring from Middleware dashboard.

### Steps

1. Create New Application
2. Obtain the accountKey & target once application is created.

## Getting Started

The Middleware Android SDK provides instrumentation for monitoring various aspects of your Android
application. With this SDK, you can track and analyze the features listed above, viewing the results
in the Middleware RUM section and RUM dashboard.

## Setup

### Install Middleware Android SDK

```groovy
implementation 'io.github.middleware-labs:android-sdk:+'
```

### Configure of Middleware Android Instrumentation

```java
import io.middleware.android.sdk.Middleware;
import io.opentelemetry.api.common.Attributes;

class MiddlewareApplication extends Application {
   private final String targetUrl = "<target-url>";
   private final String rumAccessToken = "<your-access-token>";

   @Override
   public void onCreate() {
      super.onCreate();

    Middleware.builder()
        .setGlobalAttributes(Attributes.of(APP_VERSION, BuildConfig.VERSION_NAME))
        .setTarget(targetUrl)
        .setServiceName("sample-android-app-1")
        .setProjectName("Mobile-SDK-Android")
        .setRumAccessToken(rumAccessToken)
        .setSlowRenderingDetectionPollInterval(Duration.ofMillis(1000))
        .setDeploymentEnvironment("PROD")
        .build(this);
}

```

> `Attributes` lives in `io.opentelemetry.api.common`, which the SDK exposes
> transitively since v3.2.0. On older SDK versions, add it explicitly:
> `implementation 'io.opentelemetry:opentelemetry-api:1.48.0'`

## Documentation

### Configurations

Methods that can be used for setting instrumentation & configure your application.

<table>
<thead>
<tr><td>Option</td><td>Description</td><tr>
</thead>
<tbody>
<tr>
    <td>
        <code lang="java">setRumAccessToken(String)</code>
    </td>
    <td>
        Sets the RUM account access token to authorize client to send telemetry data to Middleware
    </td>
</tr>

<tr>
    <td>
        <code lang="java">setTarget(String)</code>
    </td>
    <td>
        Sets the target URL to which you want to send telemetry data. For example - https://app.middleware.io
    </td>
</tr>

<tr>
    <td>
        <code lang="java">setService(String)</code>
    </td>
    <td>
        Sets the service name for your application. This can be used furthur for filtering by service name.
    </td>
</tr>

<tr>
    <td>
        <code lang="java">setDeploymentEnvironment(String)</code>
    </td>
    <td>
        Sets the environment attribute on the spans that are generated by the instrumentation. For Example  - <code>PROD</code> | <code> DEV </code>
    </td>
</tr>

<tr>
    <td>
        <code lang="java">disableCrashReporting()</code>
    </td>
    <td>
        Disable crash reporting. By default it is enabled.
    </td>
</tr>

<tr>
    <td>
        <code lang="java">disableAnrDetection()</code>
    </td>
    <td>
        Disable Application Not Responding Detection. By default it is enabled.
    </td>
</tr>
<tr>
    <td>
        <code lang="java">disableNetworkMonitor()</code>
    </td>
    <td>
        Disable network change detection. By default it is enabled.
    </td>
</tr>
<tr>
    <td>
        <code lang="java">disableSlowRenderingDetection()</code>
    </td>
    <td>
        Disable slow or frozen frame renders. By default it is enabled.
    </td>
</tr>
<tr>
    <td>
        <code lang="java">setSlowRenderingDetectionPollInterval(Duration)</code>
    </td>
    <td>
        Sets the default polling for slow or frozen render detection. Default value in milliseconds is <code>1000</code>
    </td>
</tr>
<tr>
    <td>
        <code lang="java">setTracePropagationTargets(List&lt;Pattern&gt;)</code>
    </td>
    <td>
        Restricts which outbound request URLs carry <code>traceparent</code> and B3 headers. By default every URL does. See <a href="#distributed-tracing">Distributed Tracing</a>.
    </td>
</tr>
</tbody>
</table>

### HTTP Instrumentation Configuration

#### OkHttp

```java
private Call.Factory buildOkHttpClient(Middleware middleware) {
   return middleware.createRumOkHttpCallFactory(new OkHttpClient());
}
```

> **HTTP instrumentation is not automatic on Android.** Unlike the browser and iOS SDKs, this SDK
> cannot install itself into your network stack. Requests made through a plain `OkHttpClient`
> produce no HTTP spans and carry no trace headers. Every network call must go through the
> `Call.Factory` returned above. If none does, the SDK logs a warning about ten seconds after
> startup.

### Distributed Tracing

End-to-end tracing links a RUM session to the backend traces it caused, so you can open a slow
screen in the session explorer and see the server spans behind it.

It works by trace-context propagation: the SDK creates a client span for each outgoing request and
injects the W3C `traceparent` header (plus B3, for backends that read it). Your instrumented
backend continues that same trace, and Middleware correlates the two by trace ID.

**This only happens for requests made through `createRumOkHttpCallFactory`.** That is the single
requirement, and the most common reason Android sessions show no backend traces.

```java
public class MyApplication extends Application {

    private Call.Factory httpClient;

    @Override
    public void onCreate() {
        super.onCreate();

        Middleware.builder()
                .setTarget("<target>")
                .setProjectName("<project>")
                .setServiceName("<service>")
                .setRumAccessToken("<token>")
                .build(this);

        // Wrap once, then use this everywhere in the app.
        httpClient = Middleware.getInstance()
                .createRumOkHttpCallFactory(new OkHttpClient());
    }
}
```

With Retrofit, pass the wrapped factory rather than an `OkHttpClient`:

```java
Retrofit retrofit = new Retrofit.Builder()
        .baseUrl("https://api.example.com/")
        .callFactory(httpClient)
        .build();
```

#### Supported clients

Only **OkHttp** is instrumented, including anything layered on it (Retrofit, Coil, and similar)
as long as the wrapped factory is what they use. `HttpURLConnection`, Ktor, Volley, Cronet and
raw `java.net` clients are not instrumented and will not correlate.

#### Restricting which hosts receive trace headers

By default every request through the wrapped client carries trace headers. To keep your trace IDs
off third-party APIs, list the hosts that should receive them:

```java
Middleware.builder()
        // ... other configuration
        .setTracePropagationTargets(Arrays.asList(
                Pattern.compile("api\\.example\\.com"),
                Pattern.compile("checkout\\.example\\.com")))
        .build(this);
```

Each pattern is searched for anywhere in the request URL, so `api.example.com` matches
`https://api.example.com/orders`. Requests to other hosts are still timed and still appear in the
session — they just travel without trace headers. Passing an empty list disables propagation
entirely.

#### Verifying it works

The span for each request records its outbound headers as attributes. In the session explorer,
open a network event and look for `http.request.header.traceparent`. If it is present, the SDK
propagated correctly and any missing correlation is on the backend side. If it is absent, the
request did not go through the wrapped client.

### Manually instrumentation for android application

#### Global Attributes

Global attributes are key-value pairs that are used for attaching the global information for the
reported data. These values can be useful for custom or user specific tags that can be attached
while sending data to Middleware.

##### How to set global attributes?

```java
Middleware.builder()
        .setGlobalAttributes(
            Attributes.builder()
                    .put("key", "value")
                    .put(StandardAttributes.APP_VERSION, BuildConfig.VERSION_NAME)
                    .build());
```

#### Custom Events

You can also send custom events and workflows using <code>addEvent</code> and <code>
startWorkflow</code> APIs respectively

##### How to send custom event?

```java
Middleware.getInstance().addEvent("You clicked on Button", BUTTON_ATTRIBUES);
```

##### How to start workflow?

```java
Span loginWorkflow = Middleware.getInstance().startWorkflow("User Login Flow");
```

##### How to end workflow?

```java
loginWorkflow.end();
```

#### Configure error reporting

You can report exceptions, errors and any messages using `addException(Throwable)` We will show this
on our Middleware Dashboard.

```java
Middleware.getInstance().addException(new RuntimeException("Something went wrong!"), Attributes.empty())
```

#### Custom Logs

You can add custom logs such as debug, error, warn, info these logs will be shown on Middleware Logs
Dashboard

```java
Middleware logInstance = Middleware.getInstance();
logInstance.d("TAG", "I am debug");
logInstance.e("TAG", "I am error");
logInstance.i("TAG", "I am info");
logInstance.w("TAG", "I am warn");
```
### Enable Session Recording
By default session recording is enabled capture all activities. To disable session recording you can use `.disableSessionRecording()` 

#### Sanitizing view elements

To blur sensitive information in session recording use the following method : 
```java
    final Middleware instance = Middleware.getInstance();
    final TextView someTextView = findViewById(R.id.some_text_view;
    instance.addSanitizedElement(someTextView);
```

### WebView Instrumentation

Bridges the native RUM session into web content loaded in a `WebView`. Pages instrumented with the Middleware browser RUM SDK detect the injected `MiddlewareNative` interface, adopt the native session id, and report all browser telemetry under the same session as the native app.

```java
WebView webView = findViewById(R.id.webView);
webView.getSettings().setJavaScriptEnabled(true);
Middleware.getInstance().integrateWithBrowserRum(webView);
webView.loadUrl("https://your-pwa.example.com");
```

Requirements:
- Call `integrateWithBrowserRum(webView)` **before** `loadUrl(...)`, with JavaScript enabled.
- The loaded page must include the Middleware browser RUM SDK.

Linking browser + mobile telemetry: the browser SDK inside the WebView and this SDK must report to the **same Middleware project** (same ingest target; each can use its own application/client token within that project). Cross-project linking is not supported — projects are stored separately and cannot be joined. With both in one project, the shared session id from this bridge unifies the session view, replay, and traces automatically.

## Coffee Cart Sample App

The `:app` module is a full **Coffee Cart** ecommerce demo that exercises every Middleware Android RUM feature in a realistic coffee-ordering flow.

### Screens

| Screen | What the user does |
|--------|-------------------|
| **Menu** | Browse coffee products (API + local catalog), open details, add to cart |
| **Product Detail** | Choose quantity, add to cart |
| **Cart** | Update qty, remove items, checkout |
| **Checkout** | Enter delivery + card details, place order |
| **Order Confirmation** | See order id and return to menu |
| **Account** | Save profile (`customerId` global attribute), open Help / RUM Lab |
| **Help (WebView)** | FAQ page with browser RUM integration |
| **RUM Lab** | Crash, ANR, custom event/exception, HTTP, worker, new session |

### Run

1. Put your Middleware credentials in `secrets.properties`:

```properties
TARGET="<your-target-url>"
ACCESS_KEY="<your-rum-access-token>"
```

2. Open the project in Android Studio and run the `app` configuration (or `./gradlew :app:installDebug`).

Service / project name sent to Middleware: `CoffeeCart-Android`.

### RUM feature → screen map

| Feature | Where it is exercised |
|---------|------------------------|
| OkHttp network monitoring (`createRumOkHttpCallFactory`) | Menu product fetch, Checkout order POST, Rum Lab HTTP |
| Crash reporting | Rum Lab (crash + obfuscated crash) |
| Activity / Fragment lifecycle | All screens (automatic) |
| Slow / freeze render detection | Enabled in `CoffeeCartApplication`; product list scroll |
| Custom events (`addEvent`) | Add to cart, product viewed, checkout, profile saved, Rum Lab |
| Workflows (`startWorkflow`) | Browse Menu, Checkout Flow, Rum Lab flows |
| Custom exceptions (`addException`) | Network/payment failures; test card ending in `0002`; Rum Lab |
| Custom logs (`d` / `i` / `e` / `w`) | Throughout the shop flow |
| Session recording + sanitization | Card number & CVV on Checkout (`addSanitizedElement`) |
| Global attributes | App version on init; `customerId` from Account |
| WebView browser RUM | Account → Help |
| ANR detection | Rum Lab Simulate ANR |
| Network change detection | Enabled by default in builder |
| Background worker | Rum Lab |

### Tip

Use any card ending in `0002` on Checkout to trigger a declined-payment exception path.
