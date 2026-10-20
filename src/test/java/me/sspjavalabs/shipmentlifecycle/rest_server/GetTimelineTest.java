package me.sspjavalabs.shipmentlifecycle.rest_server;

import static me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.ResponseDto.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.sspjavalabs.shipmentlifecycle.authentication.BearerTokenResolver;
import me.sspjavalabs.shipmentlifecycle.db.adapter.AppendPortAdaptor;
import me.sspjavalabs.shipmentlifecycle.db.adapter.LoadPortAdapter;
import me.sspjavalabs.shipmentlifecycle.db.infra.BasicPgDataSource;
import me.sspjavalabs.shipmentlifecycle.helper.EventGenerator;
import me.sspjavalabs.shipmentlifecycle.helper.GlobalPostgresExtension;
import me.sspjavalabs.shipmentlifecycle.helper.RestClient;
import me.sspjavalabs.shipmentlifecycle.infra.GsonSerDer;
import me.sspjavalabs.shipmentlifecycle.infra.JsonSerDer;
import me.sspjavalabs.shipmentlifecycle.rest_server.authentication.BearerTokenAuthenticator;
import me.sspjavalabs.shipmentlifecycle.rest_server.rate_limiter.FixedWindowLimiter;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.BaseRequestHandler;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.ShipmentsRequestHandler;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.EventLog;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.ShipmentLifecycle;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.ShipmentState;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendPort;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.AggregateWith;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(GlobalPostgresExtension.class)
class GetTimelineTest {
  private static final String END_POINT =
      "http://localhost:8080/shipments/{carrier}/{shipmentId}/events";
  private static RestServer restServer;
  private static RestClient restClient;
  private static AppendPort appendPort;
  private static JsonSerDer jsonSerDer;

  @BeforeAll
  static void setup() throws IOException {
    System.setProperty("SHIPMENT_LIFECYCLE_USER_TOKEN", "12345");

    BasicPgDataSource dataSource =
        BasicPgDataSource.of(
            System.getProperty("DB_URL") + "shipment_lifecycle",
            System.getProperty("DB_USER"),
            System.getProperty("DB_PASS"));
    Gson gson =
        new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    jsonSerDer = new GsonSerDer(gson);
    appendPort = new AppendPortAdaptor(dataSource);
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
                        new EventLog(new LoadPortAdapter(dataSource), new ShipmentLifecycle()),
                        appendPort,
                        jsonSerDer,
                        new FixedWindowLimiter(new ConcurrentHashMap<>()),
                        Clock.systemUTC()))));
    restServer.start();
  }

  @Test
  @DisplayName("Test Non existent shipment")
  void test_1() throws IOException, InterruptedException {
    String carrier = "fedex";
    HttpResponse<String> response = getStringHttpResponse("fedex", "s1");
    Events expected = new Events(ShipmentState.CREATED.name(), null, List.of(), null);
    assertEquals(expected, jsonSerDer.toObject(response.body(), Events.class));
    assertEquals(200, response.statusCode());
  }

  @ParameterizedTest
  @DisplayName("Rebuild: Deliver")
  @CsvSource({
    "'eventId=srhte1,shipmentId=srhts1,eventType=DELIVERED,occurredAt=BASE_INSTANT + @T(0)'"
  })
  void test_2(
      @AggregateWith(EventGenerator.TrackingEventListAggregator.class) List<TrackingEvent> events)
      throws IOException, InterruptedException {
    String carrier = "fedex";
    String shipmentId = "srhts1";
    AppendResult appendResult = appendPort.append(carrier, events.getFirst());
    HttpResponse<String> response = getStringHttpResponse(carrier, shipmentId);
    Events expected =
        new Events(ShipmentState.DELIVERED.name(), "2026-08-06T18:00:00Z", List.of(), null);
    assertEquals(200, response.statusCode());
    assertEquals(expected, jsonSerDer.toObject(response.body(), Events.class));
  }

  @ParameterizedTest
  @DisplayName(
"""
Rebuild for shipment
1. DELIVERED@T(0)
2. IN_TRANSIT@T(1)
Result: finalState -> Delivered, afterDelivered ->[IN_TRANSIT@T(1)], firstDeliveredAt -> @T(0), rejectedOn ->
""")
  @CsvSource({
"""
'
eventId=srhte1,shipmentId=srhts2,eventType=DELIVERED,occurredAt=BASE_INSTANT + @T(0)
eventId=srhte2,shipmentId=srhts2,eventType=IN_TRANSIT,occurredAt=BASE_INSTANT + @T(1)
'
"""
  })
  void test_3(
      @AggregateWith(EventGenerator.TrackingEventListAggregator.class) List<TrackingEvent> events)
      throws IOException, InterruptedException {
    String carrier = "fedex";
    String shipmentId = "srhts2";
    events.forEach(trackingEvent -> appendPort.append(carrier, trackingEvent));
    HttpResponse<String> response = getStringHttpResponse(carrier, shipmentId);
    List<TrackingEventView> afterDelivered =
        List.of(new TrackingEventView("srhte2", "IN_TRANSIT", "2026-08-06T19:00:00Z"));
    RejectedOnView rejectedOnView =
        new RejectedOnView("srhte2", "IN_TRANSIT", "2026-08-06T19:00:00Z", "after_delivered");
    Events expected =
        new Events(
            ShipmentState.DELIVERED.name(), "2026-08-06T18:00:00Z", afterDelivered, rejectedOnView);
    assertEquals(200, response.statusCode());
    assertEquals(expected, jsonSerDer.toObject(response.body(), Events.class));
  }

  @ParameterizedTest
  @DisplayName(
      """
    We append a shipment with carrier fedex. We then try and GET with carrier ups. This should return a response body with final state CREATED, since
    the record is not found. Then we get using fedex, the response body should be the view of what was appended
    """)
  @CsvSource({
    """
    '
    eventId=srhte1,shipmentId=srhts3,eventType=DELIVERED,occurredAt=BASE_INSTANT + @T(0)
    '
    """
  })
  void test_4(
      @AggregateWith(EventGenerator.TrackingEventListAggregator.class) List<TrackingEvent> events)
      throws IOException, InterruptedException {
    String carrier = "fedex";
    String shipmentId = "srhts3";
    String badCarrier = "ups";
    events.forEach(trackingEvent -> appendPort.append(carrier, trackingEvent));

    HttpResponse<String> responseWithBadCarrier = getStringHttpResponse(badCarrier, shipmentId);
    Events expectedForBadCarrier = new Events(ShipmentState.CREATED.name(), null, List.of(), null);
    assertEquals(200, responseWithBadCarrier.statusCode());
    assertEquals(
        expectedForBadCarrier, jsonSerDer.toObject(responseWithBadCarrier.body(), Events.class));

    HttpResponse<String> response = getStringHttpResponse(carrier, shipmentId);

    Events expected =
        new Events(ShipmentState.DELIVERED.name(), "2026-08-06T18:00:00Z", List.of(), null);
    assertEquals(200, response.statusCode());
    assertEquals(expected, jsonSerDer.toObject(response.body(), Events.class));
  }

  private static HttpResponse<String> getStringHttpResponse(String carrier, String shipmentId)
      throws IOException, InterruptedException {
    return restClient.get(
        Map.of("Authorization", "Bearer 12345"),
        END_POINT.replace("{carrier}", carrier).replace("{shipmentId}", shipmentId));
  }

  @AfterAll
  static void close() throws Exception {
    restClient.close();
    restServer.close();
  }
}
