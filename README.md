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

Session-recording compress / tar.gz matrix via `RecordingBench` (Robolectric; frequency × quality). As of **2026-07-15**. Production flush size is **10 frames per tar.gz**.

### Production-readiness gate

| Metric | Threshold |
|---|---|
| Upload | ≤ 4 MB/min |
| Avg capture | ≤ 100 ms |

### Compress / tar.gz matrix

| Scenario | Baseline | Avg capture (ms) | tar.gz (bytes) | Frames/tar | Tars/min | MB/min | 1h bytes | 4h tars | Ready |
|---|---|---|---|---|---|---|---|---|---|
| idle_recording_off_proxy | recording_off | 14.2 | 2502 | 10 | 6 | 0.014 | 880804 | 1440 | yes |
| idle_recording_on_low | recording_on | 4.9 | 2510 | 10 | 6 | 0.014 | 880804 | 1440 | yes |
| scroll_recording_on_standard | recording_on | 4.3 | 2742 | 10 | 18.182 | 0.048 | 3019899 | 4364 | yes |
| stress_recording_on_high | recording_on | 4.1 | 3266 | 10 | 60 | 0.187 | 11765023 | 14400 | yes |

### Measured tar.gz size (recording on)

| Workload | Frames / tar | Avg frame | JPEG bytes in tar | tar.gz (upload) | Frames / min | Tars / min |
|---|---:|---:|---:|---:|---:|---:|
| Default LOW × LOW | 10 | 9191 B (9.0 KB) | 89.8 KB | **2510 B (2.5 KB)** | 60 | **6** |
| STANDARD × MEDIUM | 10 | 10608 B (10.4 KB) | 103.6 KB | **2742 B (2.7 KB)** | 181.8 | **18.182** |
| HIGH × HIGH (stress) | 10 | 12635 B (12.3 KB) | 123.4 KB | **3266 B (3.2 KB)** | 600 | **60** |

### Measured upload rates (recording on)

| Workload | Bytes / min | MB / min |
|---|---:|---:|
| Default LOW × LOW | 14680 B (14.3 KB) | 0.014 |
| STANDARD × MEDIUM | 50332 B (49.2 KB) | 0.048 |
| HIGH × HIGH (stress) | 196084 B (191.5 KB) | 0.187 |

### Projected session upload (recording on)

Each cell is **tar upload count · total size**.

| Workload | tar.gz each | 5 min | 15 min | 30 min | 1 hour | 2 hours | 4 hours |
|---|---:|---:|---:|---:|---:|---:|---:|
| Default LOW × LOW | 2.5 KB | 30 × 2.5 KB = 73.5 KB | 90 × 2.5 KB = 220.6 KB | 180 × 2.5 KB = 441.2 KB | 360 × 2.5 KB = 882.4 KB | 720 × 2.5 KB = 1.72 MB | 1440 × 2.5 KB = 3.45 MB |
| STANDARD × MEDIUM | 2.7 KB | 91 × 2.7 KB = 243.7 KB | 273 × 2.7 KB = 731.0 KB | 545 × 2.7 KB = 1.43 MB | 1091 × 2.7 KB = 2.85 MB | 2182 × 2.7 KB = 5.71 MB | 4364 × 2.7 KB = 11.41 MB |
| HIGH × HIGH (stress) | 3.2 KB | 300 × 3.2 KB = 956.8 KB | 900 × 3.2 KB = 2.80 MB | 1800 × 3.2 KB = 5.61 MB | 3600 × 3.2 KB = 11.21 MB | 7200 × 3.2 KB = 22.43 MB | 14400 × 3.2 KB = 44.85 MB |

**Planning:** default LOW×LOW ≈ 860 KB/hour (360 uploads); 4 hours ≈ 3.4 MB (1440 uploads). Real apps vary with UI density and network conditions.

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
</tbody>
</table>

### HTTP Instrumentation Configuration

#### OkHttp

```java
private Call.Factory buildOkHttpClient(Middleware middleware) {
   return middleware.createRumOkHttpCallFactory(new OkHttpClient());
}
```

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
