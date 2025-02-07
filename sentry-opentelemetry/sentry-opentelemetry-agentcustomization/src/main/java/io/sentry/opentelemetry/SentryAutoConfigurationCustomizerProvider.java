package io.sentry.opentelemetry;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.sentry.Instrumenter;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.protocol.SdkVersion;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryAutoConfigurationCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    final @Nullable String sentryPropertiesFile = System.getenv("SENTRY_PROPERTIES_FILE");
    final @Nullable String sentryDsn = System.getenv("SENTRY_DSN");

    if (sentryPropertiesFile != null || sentryDsn != null) {
      Sentry.init(
          options -> {
            options.setEnableExternalConfiguration(true);
            options.setInstrumenter(Instrumenter.OTEL);
            final @Nullable SdkVersion sdkVersion = createSdkVersion(options);
            if (sdkVersion != null) {
              options.setSdkVersion(sdkVersion);
            }
          });
    }

    autoConfiguration
        .addTracerProviderCustomizer(this::configureSdkTracerProvider)
        .addPropertiesSupplier(this::getDefaultProperties);
  }

  private @Nullable SdkVersion createSdkVersion(final @NotNull SentryOptions sentryOptions) {
    SdkVersion sdkVersion = sentryOptions.getSdkVersion();

    try {
      final @NotNull Enumeration<URL> resources =
          ClassLoader.getSystemClassLoader().getResources("META-INF/MANIFEST.MF");
      while (resources.hasMoreElements()) {
        try {
          final @NotNull Manifest manifest = new Manifest(resources.nextElement().openStream());
          final @Nullable Attributes mainAttributes = manifest.getMainAttributes();
          if (mainAttributes != null) {
            final @Nullable String name = mainAttributes.getValue("Sentry-Opentelemetry-SDK-Name");
            final @Nullable String version = mainAttributes.getValue("Sentry-Version-Name");

            if (name != null && version != null) {
              sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);
              sdkVersion.addPackage("maven:io.sentry:sentry-opentelemetry-agent", version);
              final @Nullable String otelVersion =
                  mainAttributes.getValue("Sentry-Opentelemetry-Version-Name");
              if (otelVersion != null) {
                sdkVersion.addPackage("maven:io.opentelemetry:opentelemetry-sdk", otelVersion);
              }
              final @Nullable String otelJavaagentVersion =
                  mainAttributes.getValue("Sentry-Opentelemetry-Javaagent-Version-Name");
              if (otelJavaagentVersion != null) {
                sdkVersion.addPackage(
                    "maven:io.opentelemetry.javaagent:opentelemetry-javaagent",
                    otelJavaagentVersion);
              }
            }
          }
        } catch (Exception e) {
          // ignore
        }
      }
    } catch (IOException e) {
      // ignore
    }

    return sdkVersion;
  }

  private SdkTracerProviderBuilder configureSdkTracerProvider(
      SdkTracerProviderBuilder tracerProvider, ConfigProperties config) {
    return tracerProvider.addSpanProcessor(new SentrySpanProcessor());
  }

  private Map<String, String> getDefaultProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.propagators", "sentry");
    return properties;
  }
}
