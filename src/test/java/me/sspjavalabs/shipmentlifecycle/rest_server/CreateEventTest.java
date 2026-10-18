package me.sspjavalabs.shipmentlifecycle.rest_server;

import static me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.RequestDto.*;
import static me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.ResponseDto.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
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
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.BaseRequestHandler;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.ShipmentsRequestHandler;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.EventLog;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.ShipmentLifecycle;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendPort;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.LoadPort;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.AggregateWith;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(GlobalPostgresExtension.class)
public class CreateEventTest {
  private static final String END_POINT =
      "http://localhost:8080/shipments/{carrier}/{shipmentId}/events";
  private static RestServer restServer;
  private static RestClient restClient;
  private static AppendPort appendPort;
  private static JsonSerDer jsonSerDer;
  private static LoadPort loadPort;

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
    loadPort = new LoadPortAdapter(dataSource);
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
                        new EventLog(loadPort, new ShipmentLifecycle()), appendPort, jsonSerDer))));
    restServer.start();
  }

  @Test
  @DisplayName("No auth header")
  void test_1() throws IOException, InterruptedException {
    CreateEvent createEvent = new CreateEvent("cete1", "DELIVERED", "2026-08-06T18:00:00Z");

    HttpResponse<String> response =
        post(Map.of(), "fedex", "cets1", jsonSerDer.toJsonString(createEvent));
    assertEquals(401, response.statusCode());
  }

  @ParameterizedTest
  @DisplayName("Create -> 201 -> Get -> Delivered")
  @CsvSource({
    """
    '
    eventId=cete1,shipmentId=cets2,eventType=DELIVERED,occurredAt=BASE_INSTANT + @T(0)
    '
    """
  })
  void test_2(
      @AggregateWith(EventGenerator.TrackingEventListAggregator.class) List<TrackingEvent> events)
      throws IOException, InterruptedException {
    String carrier = "fedex";
    String shipmentId = "cets2";

    HttpResponse<String> response =
        post(
            Map.of("Authorization", "Bearer 12345"),
            carrier,
            shipmentId,
            jsonSerDer.toJsonString(getFromTrackingEvent(events.getFirst())));
    assertEquals(201, response.statusCode());
    assertEquals(
        new CreateEventView("inserted", "cete1"),
        jsonSerDer.toObject(response.body(), CreateEventView.class));

    HttpResponse<String> getResponse =
        restClient.get(
            Map.of("Authorization", "Bearer 12345"),
            "http://localhost:8080/shipments/fedex/cets2/events");
    assertEquals("DELIVERED", jsonSerDer.toObject(getResponse.body(), Events.class).finalState());
  }

  @ParameterizedTest
  @DisplayName("Create -> 201 -> Create again with duplicate -> 200")
  @CsvSource({
    """
    '
    eventId=cete1,shipmentId=cets3,eventType=DELIVERED,occurredAt=BASE_INSTANT + @T(0)
    '
    """
  })
  void test_3(
      @AggregateWith(EventGenerator.TrackingEventListAggregator.class) List<TrackingEvent> events)
      throws IOException, InterruptedException {
    String carrier = "fedex";
    String shipmentId = "cets3";

    HttpResponse<String> response =
        post(
            Map.of("Authorization", "Bearer 12345"),
            carrier,
            shipmentId,
            jsonSerDer.toJsonString(getFromTrackingEvent(events.getFirst())));
    assertEquals(201, response.statusCode());
    assertEquals(
        new CreateEventView("inserted", "cete1"),
        jsonSerDer.toObject(response.body(), CreateEventView.class));
    HttpResponse<String> getResponse =
        restClient.get(
            Map.of("Authorization", "Bearer 12345"),
            "http://localhost:8080/shipments/fedex/cets3/events");
    assertEquals("DELIVERED", jsonSerDer.toObject(getResponse.body(), Events.class).finalState());

    HttpResponse<String> responseDuplicate =
        post(
            Map.of("Authorization", "Bearer 12345"),
            carrier,
            shipmentId,
            jsonSerDer.toJsonString(getFromTrackingEvent(events.getFirst())));
    assertEquals(200, responseDuplicate.statusCode());
    assertEquals(
        new CreateEventView("duplicate", "cete1"),
        jsonSerDer.toObject(responseDuplicate.body(), CreateEventView.class));
  }

  @Test
  @DisplayName("Bad request, bad event type")
  void test_4() throws IOException, InterruptedException {
    String carrier = "fedex";
    String shipmentId = "cets4";
    String eventId = "cete1";
    String eventType = "BAD_TYPE";
    String occurredAt = EventGenerator.baseInstant.toString();
    CreateEvent createEventWithBadEventType = new CreateEvent(eventId, eventType, occurredAt);

    HttpResponse<String> response =
        post(
            Map.of("Authorization", "Bearer 12345"),
            carrier,
            shipmentId,
            jsonSerDer.toJsonString(createEventWithBadEventType));
    assertEquals(400, response.statusCode());
    assertEquals(
        new ValidationError("Incorrect event_type"),
        jsonSerDer.toObject(response.body(), ValidationError.class));
  }

  @Test
  @DisplayName("null clock")
  void test_5() throws IOException, InterruptedException {
    String carrier = "fedex";
    String shipmentId = "cets5";
    String eventId = "cete1";
    String eventType = "OUT_FOR_DELIVERY";
    String occurredAt = null;
    CreateEvent createEventWithBadEventType = new CreateEvent(eventId, eventType, occurredAt);

    HttpResponse<String> response =
        post(
            Map.of("Authorization", "Bearer 12345"),
            carrier,
            shipmentId,
            jsonSerDer.toJsonString(createEventWithBadEventType));
    assertEquals(201, response.statusCode());
    assertEquals(
        new CreateEventView("inserted", "cete1"),
        jsonSerDer.toObject(response.body(), CreateEventView.class));
    List<TrackingEvent> eventsFromDb = loadPort.load(carrier, shipmentId);
    assertEquals(1, eventsFromDb.size());
    assertEquals(EventType.OUT_FOR_DELIVERY, eventsFromDb.getFirst().eventType());
    assertNull(eventsFromDb.getFirst().occurredAt());
  }

  @Test
  @DisplayName("Carrier from path stored correctly")
  void test_6() throws IOException, InterruptedException {
    String carrier = "ups";
    String shipmentId = "cets6";
    String eventId = "ceteups1";
    String eventType = "OUT_FOR_DELIVERY";
    String occurredAt = EventGenerator.baseInstant.toString();
    CreateEvent createEventWithBadEventType = new CreateEvent(eventId, eventType, occurredAt);

    HttpResponse<String> response =
        post(
            Map.of("Authorization", "Bearer 12345"),
            carrier,
            shipmentId,
            jsonSerDer.toJsonString(createEventWithBadEventType));
    assertEquals(201, response.statusCode());
    assertEquals(
        new CreateEventView("inserted", "ceteups1"),
        jsonSerDer.toObject(response.body(), CreateEventView.class));

    HttpResponse<String> getResponse =
        restClient.get(
            Map.of("Authorization", "Bearer 12345"),
            "http://localhost:8080/shipments/ups/cets6/events");
    assertEquals(
        "OUT_FOR_DELIVERY", jsonSerDer.toObject(getResponse.body(), Events.class).finalState());
  }

  @Test
  @DisplayName("unparseable occurred_at")
  void test_7() throws IOException, InterruptedException {
    String carrier = "fedex";
    String shipmentId = "cets7";
    String eventId = "cete1";
    String eventType = "OUT_FOR_DELIVERY";
    String occurredAt = "2003";
    CreateEvent createEventWithBadEventType = new CreateEvent(eventId, eventType, occurredAt);

    HttpResponse<String> response =
        post(
            Map.of("Authorization", "Bearer 12345"),
            carrier,
            shipmentId,
            jsonSerDer.toJsonString(createEventWithBadEventType));
    assertEquals(400, response.statusCode());
    assertEquals(
        new ValidationError("Unparseable occurred_at"),
        jsonSerDer.toObject(response.body(), ValidationError.class));
    List<TrackingEvent> eventsFromDb = loadPort.load(carrier, shipmentId);
    assertEquals(0, eventsFromDb.size());
  }

  CreateEvent getFromTrackingEvent(TrackingEvent trackingEvent) {
    String eventId = trackingEvent.eventId();
    String shipmentId = trackingEvent.shipmentId();
    String eventType = trackingEvent.eventType() == null ? null : trackingEvent.eventType().name();
    String occurredAt =
        trackingEvent.occurredAt() == null ? null : trackingEvent.occurredAt().toString();
    return new CreateEvent(eventId, eventType, occurredAt);
  }

  HttpResponse<String> post(
      Map<String, String> headers, String carrier, String shipmentId, String requestBody)
      throws IOException, InterruptedException {
    return restClient.post(
        headers,
        requestBody,
        END_POINT.replace("{carrier}", carrier).replace("{shipmentId}", shipmentId));
  }

  @AfterAll
  static void close() throws Exception {
    restClient.close();
    restServer.close();
  }
}
