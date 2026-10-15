package me.sspjavalabs.shipmentlifecycle.rest_server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Map;
import me.sspjavalabs.shipmentlifecycle.authentication.BearerTokenResolver;
import me.sspjavalabs.shipmentlifecycle.helper.RestClient;
import me.sspjavalabs.shipmentlifecycle.rest_server.authentication.BearerTokenAuthenticator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RestServerTest {
  static final String SHIPMENTS_ENDPOINT = "http://localhost:8080/shipments";
  private static RestServer restServer;
  private static RestClient restClient;

  @BeforeAll
  static void setUp() throws IOException {
    System.setProperty("SHIPMENT_LIFECYCLE_USER_TOKEN", "12345");
    restClient = RestClient.of();
    restServer =
        RestServer.initialise(
            8080,
            new BearerTokenAuthenticator(
                new BearerTokenResolver.SystemPropertiesBearerTokenResolver()),
            Map.of("/", exchange -> {}));
    restServer.start();
  }

  @AfterAll
  static void close() throws Exception {
    restClient.close();
    restServer.close();
  }

  @Test
  void test_no_header() throws IOException, InterruptedException {
    HttpResponse<String> response = restClient.get(Map.of(), SHIPMENTS_ENDPOINT);
    assertEquals(401, response.statusCode());
  }

  @Test
  void test_incorrect_bearer_token() throws IOException, InterruptedException {
    HttpResponse<String> response =
        restClient.get(
            Map.of("Authorization", "Bearer incorrect_bearer_token"), SHIPMENTS_ENDPOINT);
    assertEquals(401, response.statusCode());
  }

  @Test
  void test_incorrect_bearer_token_with_no_space() throws IOException, InterruptedException {
    HttpResponse<String> response =
        restClient.get(Map.of("Authorization", "Bearer12345"), SHIPMENTS_ENDPOINT);
    assertEquals(401, response.statusCode());
  }

  @Test
  void test_basic_scheme() throws IOException, InterruptedException {
    HttpResponse<String> response =
        restClient.get(Map.of("Authorization", "Basic incorrect_bearer_token"), SHIPMENTS_ENDPOINT);
    assertEquals(401, response.statusCode());
  }
}
