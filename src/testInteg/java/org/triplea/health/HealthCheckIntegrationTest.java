package org.triplea.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// Guards the SmallRye Health endpoints the post-deploy smoke check relies on. Beyond confirming
/// the probes exist, the readiness assertion pins the intent that "healthy" is dependency-aware:
/// the Agroal datasource check must be part of `/q/health/ready`, so a booted-but-DB-less server
/// reads as DOWN rather than falsely green.
@QuarkusTest
class HealthCheckIntegrationTest {

  @ConfigProperty(name = "quarkus.http.test-port", defaultValue = "8081")
  int testPort;

  private HttpClient httpClient;
  private String baseUrl;

  @BeforeEach
  void setUp() {
    httpClient = HttpClient.newHttpClient();
    baseUrl = "http://localhost:" + testPort;
  }

  @Test
  void livenessIsUp() throws Exception {
    var response = get("/q/health/live");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\": \"UP\"");
  }

  @Test
  void readinessIsUpAndCoversTheDatabase() throws Exception {
    var response = get("/q/health/ready");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\": \"UP\"");
    // The name Quarkus/SmallRye gives the Agroal datasource readiness check; its presence is what
    // makes readiness fail when Postgres is unreachable.
    assertThat(response.body()).contains("Database connections health check");
  }

  private HttpResponse<String> get(String path) throws Exception {
    var request = HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
