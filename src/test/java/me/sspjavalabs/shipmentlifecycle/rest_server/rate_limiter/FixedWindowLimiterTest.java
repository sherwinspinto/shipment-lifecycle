package me.sspjavalabs.shipmentlifecycle.rest_server.rate_limiter;

import static java.util.concurrent.StructuredTaskScope.*;
import static me.sspjavalabs.shipmentlifecycle.helper.EventGenerator.baseInstant;
import static me.sspjavalabs.shipmentlifecycle.rest_server.RestServerFailure.*;
import static me.sspjavalabs.shipmentlifecycle.rest_server.rate_limiter.FixedWindowLimiter.*;
import static me.sspjavalabs.shipmentlifecycle.rest_server.rate_limiter.FixedWindowLimiter.LimiterEntry.*;
import static me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.RequestHandlerResult.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Stream;
import me.sspjavalabs.shipmentlifecycle.authentication.BearerTokenResolver;
import me.sspjavalabs.shipmentlifecycle.db.adapter.AppendPortAdaptor;
import me.sspjavalabs.shipmentlifecycle.db.adapter.LoadPortAdapter;
import me.sspjavalabs.shipmentlifecycle.db.infra.BasicPgDataSource;
import me.sspjavalabs.shipmentlifecycle.helper.GlobalPostgresExtension;
import me.sspjavalabs.shipmentlifecycle.helper.RestClient;
import me.sspjavalabs.shipmentlifecycle.infra.GsonSerDer;
import me.sspjavalabs.shipmentlifecycle.infra.JsonSerDer;
import me.sspjavalabs.shipmentlifecycle.rest_server.RestServer;
import me.sspjavalabs.shipmentlifecycle.rest_server.RestServerFailure;
import me.sspjavalabs.shipmentlifecycle.rest_server.authentication.BearerTokenAuthenticator;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.BaseRequestHandler;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.ShipmentsRequestHandler;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.RequestDto;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.RequestHandlerResult;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.EventLog;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.ShipmentLifecycle;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendPort;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendResult;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.LoadPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(GlobalPostgresExtension.class)
class FixedWindowLimiterTest {
  private static final String END_POINT =
      "http://localhost:8080/shipments/{carrier}/{shipmentId}/events";
  private static RestClient restClient;
  private static final JsonSerDer jsonSerDer = new GsonSerDer();
  private static final Map<String, LimiterEntry> globalLimiterCache = new ConcurrentHashMap<>();
  private static LoadPort loadPort;
  private static RestServer restServer;

  @BeforeAll
  static void setup() throws IOException {
    System.setProperty("SHIPMENT_LIFECYCLE_USER_TOKEN", "12345");

    BasicPgDataSource dataSource =
        BasicPgDataSource.of(
            System.getProperty("DB_URL") + "shipment_lifecycle",
            System.getProperty("DB_USER"),
            System.getProperty("DB_PASS"));

    JsonSerDer jsonSerDer = new GsonSerDer();
    loadPort = new LoadPortAdapter(dataSource);
    AppendPort appendPort = new AppendPortAdaptor(dataSource);
    restClient = RestClient.of();
    restServer =
        RestServer.initialise(
            8080,
            new BearerTokenAuthenticator(
                new BearerTokenResolver.SystemPropertiesBearerTokenResolver()),
            Map.of(
                ShipmentsRequestHandler.ROOT_RESOURCE_URL,
                new BaseRequestHandler(
                    new ShipmentsRequestHandler(
                        new EventLog(loadPort, new ShipmentLifecycle()),
                        appendPort,
                        jsonSerDer,
                        new FixedWindowLimiter(globalLimiterCache),
                        Clock.fixed(baseInstant, ZoneId.of("UTC"))))));
    restServer.start();
  }

  @Test
  @DisplayName("3 posts for fedex within the window")
  void test_1() {
    FixedWindowLimiter FIXED_WINDOW_LIMITER = new FixedWindowLimiter(new ConcurrentHashMap<>());
    Stream.of(
            baseInstant,
            baseInstant.plus(5, ChronoUnit.SECONDS),
            baseInstant.plus(10, ChronoUnit.SECONDS))
        .map(now -> FIXED_WINDOW_LIMITER.allow("fedex", now))
        .forEach(lr -> assertInstanceOf(Allow.class, lr));
  }

  @Test
  @DisplayName("4 posts for fedex within the window, one is rejected")
  void test_2() {
    FixedWindowLimiter FIXED_WINDOW_LIMITER = new FixedWindowLimiter(new ConcurrentHashMap<>());
    List<Instant> expectedAllows =
        List.of(
            baseInstant.plus(100, ChronoUnit.SECONDS),
            baseInstant.plus(105, ChronoUnit.SECONDS),
            baseInstant.plus(107, ChronoUnit.SECONDS));

    List<Instant> expectedBlocks = List.of(baseInstant.plus(110, ChronoUnit.SECONDS));

    List<Instant> allows = new ArrayList<>();
    List<Instant> blocks = new ArrayList<>();

    Stream.of(
            baseInstant.plus(100, ChronoUnit.SECONDS),
            baseInstant.plus(105, ChronoUnit.SECONDS),
            baseInstant.plus(107, ChronoUnit.SECONDS),
            baseInstant.plus(110, ChronoUnit.SECONDS))
        .map(now -> new Tuple<>(FIXED_WINDOW_LIMITER.allow("fedex", now), now))
        .forEach(
            tup -> {
              switch (tup.v1()) {
                case Allow _ -> allows.add(tup.v2());
                case Block _ -> blocks.add(tup.v2());
              }
            });
    assertEquals(expectedAllows, allows);
    assertEquals(expectedBlocks, blocks);
  }

  @Test
  @DisplayName("4th post after window has passed")
  void test_3() {
    FixedWindowLimiter FIXED_WINDOW_LIMITER = new FixedWindowLimiter(new ConcurrentHashMap<>());
    List<Instant> expectedAllows =
        List.of(
            baseInstant.plus(100, ChronoUnit.SECONDS),
            baseInstant.plus(105, ChronoUnit.SECONDS),
            baseInstant.plus(107, ChronoUnit.SECONDS),
            baseInstant.plus(165, ChronoUnit.SECONDS));

    List<Instant> allows = new ArrayList<>();

    Stream.of(
            baseInstant.plus(100, ChronoUnit.SECONDS),
            baseInstant.plus(105, ChronoUnit.SECONDS),
            baseInstant.plus(107, ChronoUnit.SECONDS),
            baseInstant.plus(165, ChronoUnit.SECONDS))
        .map(now -> new Tuple<>(FIXED_WINDOW_LIMITER.allow("fedex", now), now))
        .forEach(
            tup -> {
              switch (tup.v1()) {
                case Allow _ -> allows.add(tup.v2());
                case Block _ -> fail("No blocks expected");
              }
            });
    assertEquals(expectedAllows, allows);
  }

  @Test
  @DisplayName("3 fedex, 1 ups, same window")
  void test_4() {
    FixedWindowLimiter FIXED_WINDOW_LIMITER = new FixedWindowLimiter(new ConcurrentHashMap<>());
    var expectedAllows =
        List.of(
            baseInstant.plus(100, ChronoUnit.SECONDS),
            baseInstant.plus(105, ChronoUnit.SECONDS),
            baseInstant.plus(107, ChronoUnit.SECONDS),
            baseInstant.plus(109, ChronoUnit.SECONDS));

    List<Instant> allows = new ArrayList<>();

    Stream.of(
            new Tuple<>("fedex", baseInstant.plus(100, ChronoUnit.SECONDS)),
            new Tuple<>("fedex", baseInstant.plus(105, ChronoUnit.SECONDS)),
            new Tuple<>("fedex", baseInstant.plus(107, ChronoUnit.SECONDS)),
            new Tuple<>("ups", baseInstant.plus(109, ChronoUnit.SECONDS)))
        .map(tup -> new Tuple<>(FIXED_WINDOW_LIMITER.allow(tup.v1(), tup.v2()), tup.v2()))
        .forEach(
            tup -> {
              switch (tup.v1()) {
                case Allow _ -> allows.add(tup.v2());
                case Block _ -> fail("No blocks expected");
              }
            });
    assertEquals(expectedAllows, allows);
  }

  @Test
  @DisplayName("401, then a valid post")
  void test_5() throws IOException, InterruptedException {
    String carrier = "fedex";
    String shipmentId = "stest5";
    String eventId = "etest5";
    String endPoint = END_POINT.replace("{carrier}", carrier).replace("{shipmentId}", shipmentId);

    List<String> recorder = new ArrayList<>();
    RequestDto.CreateEvent createEvent =
        new RequestDto.CreateEvent("etest5", "DELIVERED", "2026-08-06T18:00:00Z");

    HttpResponse<String> response401 =
        restClient.post(Map.of(), jsonSerDer.toJsonString(createEvent), endPoint);
    assertEquals(401, response401.statusCode());
    assertEquals(0, globalLimiterCache.size());
    assertEquals(0, loadPort.load(carrier, shipmentId).size());
    HttpResponse<String> response201 =
        restClient.post(
            Map.of("Authorization", "Bearer 12345"),
            jsonSerDer.toJsonString(createEvent),
            endPoint);
    assertEquals(201, response201.statusCode());
    assertEquals(1, globalLimiterCache.size());
    assertEquals(new Allow(baseInstant, 1), globalLimiterCache.get(carrier));
    assertEquals(1, loadPort.load(carrier, shipmentId).size());
    assertEquals(eventId, loadPort.load(carrier, shipmentId).getFirst().eventId());
  }

  @Test
  @DisplayName("bad body 400, then posts")
  void test_6() throws IOException {
    String bad401RequestBody =
"""
{
    "event_id" : "BAD",
    "event_type" : "BAD_VALUE",
    "occurred_at" : "2026-08-06T18:00:00Z"
}
""";

    String successRequestBody =
"""
{
    "event_id" : "GOOD",
    "event_type" : "DELIVERED",
    "occurred_at" : "2026-08-06T18:00:00Z"
}
""";
    var limiterCache = new ConcurrentHashMap<String, LimiterEntry>();
    List<String> recorder = new ArrayList<>();
    AppendPort appendPort =
        (carrier, trackingEvent) -> {
          recorder.add(trackingEvent.eventId());
          return new AppendResult.Inserted(trackingEvent.eventId());
        };
    ShipmentsRequestHandler handler =
        new ShipmentsRequestHandler(
            new EventLog(null, new ShipmentLifecycle()),
            appendPort,
            new GsonSerDer(),
            new FixedWindowLimiter(limiterCache),
            Clock.fixed(baseInstant, ZoneId.of("UTC")));

    RequestHandlerResult handler401Result =
        handler.handleRequest("POST", "/shipments/fedex/s1/events", bad401RequestBody);
    switch (handler401Result) {
      case Failure(RestServerFailure failure) -> assertInstanceOf(BadRequestBody.class, failure);
      default -> fail("Expected BadRequestBody.class");
    }
    assertEquals(0, recorder.size());
    assertEquals(0, limiterCache.size());

    RequestHandlerResult handlerCreatedResult =
        handler.handleRequest("POST", "/shipments/fedex/s1/events", successRequestBody);
    switch (handlerCreatedResult) {
      case Created(String responseBody, _) ->
          assertEquals("{\"result\":\"inserted\",\"event_id\":\"GOOD\"}", responseBody);
      default -> fail("Expected Created, got " + handlerCreatedResult.getClass().getName());
    }
    assertEquals(1, recorder.size());
    assertEquals(1, limiterCache.size());
    assertTrue(limiterCache.containsKey("fedex"));
    assertEquals(new Allow(baseInstant, 1), limiterCache.get("fedex"));
  }

  @Test
  @DisplayName("Test 429")
  void test_7() throws IOException, InterruptedException {
    List<HttpResponse<String>> results = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      String endPoint =
          END_POINT.replace("{carrier}", "speedpost").replace("{shipmentId}", "shiptest7" + i);
      RequestDto.CreateEvent createEvent =
          new RequestDto.CreateEvent("etest7", "DELIVERED", "2026-08-06T18:00:00Z");

      HttpResponse<String> response =
          restClient.post(
              Map.of("Authorization", "Bearer 12345"),
              jsonSerDer.toJsonString(createEvent),
              endPoint);
      assertEquals(201, response.statusCode());
    }

    String endPoint =
        END_POINT.replace("{carrier}", "speedpost").replace("{shipmentId}", "shiptest74");
    RequestDto.CreateEvent createEvent =
        new RequestDto.CreateEvent("etest7", "DELIVERED", "2026-08-06T18:00:00Z");

    HttpResponse<String> response =
        restClient.post(
            Map.of("Authorization", "Bearer 12345"),
            jsonSerDer.toJsonString(createEvent),
            endPoint);
    assertEquals(429, response.statusCode());
    assertEquals("60", response.headers().map().get("Retry-After").getFirst());
  }

  record Tuple<L, R>(L v1, R v2) {}

  @AfterAll
  static void close() throws Exception {
    restClient.close();
    restServer.close();
  }
}
