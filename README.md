# Middleware Instrumentation Android SDK

## Features
- Android Activity & Fragment lifecycle events
- Crash Reporting
- ANR Dectection
- Network Change Detection
- Slow / Freeze render detection
- OkHttp3 instrumention for monitoring http events
- Middleware APIs for sending custom events & record exceptions
- Access to OpenTelemetry APIs
- Custom logging

## Setup


### Install Middleware Android SDK

```groovy
implementation 'io.middleware.android:sdk:+'
```

### Configure of Middleware Android Instrumentation

```java

class MyApplication extends Application {
   private final String targetUrl = "<target-url>";
   private final String rumAccessToken = "<your-access-token>";

   @Override
   public void onCreate() {
      super.onCreate();

    Middleware.builder()
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


Global attributes are key-value pairs that are used for attaching the global information for the reported data. These values can be useful for custom or user specific tags that can be attached while sending data to Middleware.

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

You can also send custom events and workflows using <code>addEvent</code> and <code>startWorkflow</code> APIs respectively

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

You can report exceptions, errors and any messages using `addException(Throwable)` We will show this on our Middleware Dashboard.

```java
Middleware.getInstance().addException(new RuntimeException("Something went wrong!"), Attributes.empty())
```

#### Custom Logs

You can add custom logs such as debug, error, warn, info these logs will be shown on Middleware Logs Dashboard

```java
Middleware logInstance = Middleware.getInstance();
logInstance.d("TAG", "I am debug");
logInstance.e("TAG", "I am error");
logInstance.i("TAG", "I am info");
logInstance.w("TAG", "I am warn");
```

