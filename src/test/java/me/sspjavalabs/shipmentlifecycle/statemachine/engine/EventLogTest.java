package me.sspjavalabs.shipmentlifecycle.statemachine.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import me.sspjavalabs.shipmentlifecycle.db.adapter.AppendPortAdaptor;
import me.sspjavalabs.shipmentlifecycle.db.adapter.LoadPortAdapter;
import me.sspjavalabs.shipmentlifecycle.db.infra.BasicPgDataSource;
import me.sspjavalabs.shipmentlifecycle.helper.EventGenerator;
import me.sspjavalabs.shipmentlifecycle.helper.GlobalPostgresExtension;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.*;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendPort;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendResult;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.LoadPort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.AggregateWith;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(GlobalPostgresExtension.class)
public class EventLogTest {
  private static AppendPort appendPort;
  private static LoadPort loadPort;
  private static EventLog eventLog;

  @BeforeAll
  public static void initialise() {
    BasicPgDataSource dataSource =
        BasicPgDataSource.of(
            System.getProperty("DB_URL") + "shipment_lifecycle",
            System.getProperty("DB_USER"),
            System.getProperty("DB_PASS"));
    appendPort = new AppendPortAdaptor(dataSource);
    loadPort = new LoadPortAdapter(dataSource);
    ShipmentLifecycle shipmentLifecycle = new ShipmentLifecycle();
    eventLog = new EventLog(loadPort, shipmentLifecycle);
  }

  @ParameterizedTest
  @DisplayName("Rebuild: Deliver")
  @CsvSource({"'eventId=e1,shipmentId=s1,eventType=DELIVERED,occurredAt=BASE_INSTANT + @T(0)'"})
  void test_rebuild_1(
      @AggregateWith(EventGenerator.TrackingEventListAggregator.class) List<TrackingEvent> events) {
    String carrier = "fedex";
    TrackingEvent inputTrackingEvent = events.getFirst();
    AppendResult appendResult = appendPort.append(carrier, inputTrackingEvent);
    switch (appendResult) {
      case AppendResult.Inserted(String eventIdFromDb) ->
          assertEquals(inputTrackingEvent.eventId(), eventIdFromDb);
      case AppendResult.Duplicate(String eventIdFromDb) -> fail("Expected Inserted got Duplicate");
    }

    Timeline timeline = eventLog.rebuild(carrier, inputTrackingEvent.shipmentId());
    assertEquals(ShipmentState.DELIVERED, timeline.finalState());
    assertEquals(List.of(), timeline.afterDelivered());
    assertEquals(
        Optional.of(Instant.from(inputTrackingEvent.occurredAt())), timeline.firstDeliveredAt());
    assertEquals(Optional.empty(), timeline.rejectedOn());
  }

  @ParameterizedTest
  @DisplayName("Rebuild: duplicate events")
  @CsvSource({
    """
    '
    eventId=e1,shipmentId=s2,eventType=DELIVERED,occurredAt=BASE_INSTANT + @T(0)
    eventId=e1,shipmentId=s2,eventType=DELIVERED,occurredAt=BASE_INSTANT + @T(1)
    '
    """
  })
  void test_rebuild_2(
      @AggregateWith(EventGenerator.TrackingEventListAggregator.class) List<TrackingEvent> events) {
    String carrier = "fedex";
    String shipmentId = "s2";

    TrackingEvent newEvent = events.getFirst();
    TrackingEvent duplicateEvent = events.get(1);

    AppendResult newEventAppend = appendPort.append(carrier, events.getFirst());
    AppendResult duplicateEventAppend = appendPort.append(carrier, events.get(1));

    switch (newEventAppend) {
      case AppendResult.Inserted(String eventId) -> assertEquals(newEvent.eventId(), eventId);
      case AppendResult.Duplicate _ -> fail("Expected Inserted got Duplicate");
    }

    switch (duplicateEventAppend) {
      case AppendResult.Inserted _ -> fail("Expected Duplicate got Inserted");
      case AppendResult.Duplicate(String eventId) -> {
        assertEquals(newEvent.eventId(), eventId);
        assertEquals(duplicateEvent.eventId(), eventId);
      }
    }

    List<TrackingEvent> eventsAfterDuplicateAppend = loadPort.load(carrier, shipmentId);
    assertEquals(1, eventsAfterDuplicateAppend.size());
    assertEquals(newEvent.occurredAt(), eventsAfterDuplicateAppend.getFirst().occurredAt());
  }

  @ParameterizedTest
  @DisplayName(
      """
  1. e2 -> IN-TRANSIT @T(3)
  2. e3 -> DELIVERED @T(1)
  Result: finalState = DELIVERED, firstDeliveredAt=BASE + @T(1), afterDelivered->IN_TRANSIT, RejectedOn=Empty
  """)
  @CsvSource({
    """
  '
  eventId=e2,shipmentId=s3,eventType=IN_TRANSIT,occurredAt=BASE_INSTANT + @T(3)
  eventId=e3,shipmentId=s3,eventType=DELIVERED,occurredAt=BASE_INSTANT + @T(1)
  '
  """
  })
  void test_rebuild_3(
      @AggregateWith(EventGenerator.TrackingEventListAggregator.class) List<TrackingEvent> events) {
    String carrier = "fedex";
    String shipmentId = "s3";
    List<AppendResult> appendResults =
        events.stream().map(trackingEvent -> appendPort.append(carrier, trackingEvent)).toList();

    assertEquals(2, appendResults.size());

    for (int i = 0; i < events.size(); i++) {
      switch (appendResults.get(i)) {
        case AppendResult.Inserted(String eventId) ->
            assertEquals(events.get(i).eventId(), eventId);
        case AppendResult.Duplicate _ ->
            fail("Expected Inserted with eventId = " + events.get(i).eventId());
      }
    }

    Timeline timeline = eventLog.rebuild(carrier, shipmentId);
    assertEquals(ShipmentState.DELIVERED, timeline.finalState());
    assertEquals(1, timeline.afterDelivered().size());
    assertEquals(events.getFirst(), timeline.afterDelivered().getFirst());
    if (timeline.firstDeliveredAt().isPresent())
      assertEquals(events.get(1).occurredAt(), timeline.firstDeliveredAt().get());
    else fail("Expected first delivered, but got empty optional");
    if (timeline.rejectedOn().isPresent()) fail("Not expecting rejectedOn");
    else assertEquals(Optional.empty(), timeline.rejectedOn());
  }

  @ParameterizedTest
  @DisplayName(
      """
  1. e4 -> DELIVERED@T(0)
  2. e5 -> IN_TRANSIT@T(1)
  Result: finalState = DELIVERED, firstDeliveredAt=BASE + @T(0), afterDelivered->IN_TRANSIT, RejectedOn=IN_TRANSIT
  """)
  @CsvSource({
    """
  '
  eventId=e4,shipmentId=s4,eventType=DELIVERED,occurredAt=BASE_INSTANT + @T(0)
  eventId=e5,shipmentId=s4,eventType=IN_TRANSIT,occurredAt=BASE_INSTANT + @T(1)
  '
  """
  })
  void test_rebuild_4(
      @AggregateWith(EventGenerator.TrackingEventListAggregator.class) List<TrackingEvent> events) {
    String carrier = "fedex";
    String shipmentId = "s4";
    List<AppendResult> appendResults =
        events.stream().map(trackingEvent -> appendPort.append(carrier, trackingEvent)).toList();

    assertEquals(2, appendResults.size());

    for (int i = 0; i < events.size(); i++) {
      switch (appendResults.get(i)) {
        case AppendResult.Inserted(String eventId) ->
            assertEquals(events.get(i).eventId(), eventId);
        case AppendResult.Duplicate _ ->
            fail("Expected Inserted with eventId = " + events.get(i).eventId());
      }
    }

    Timeline timeline = eventLog.rebuild(carrier, shipmentId);
    assertEquals(ShipmentState.DELIVERED, timeline.finalState());
    assertEquals(1, timeline.afterDelivered().size());
    assertEquals(events.getLast(), timeline.afterDelivered().getFirst());
    if (timeline.firstDeliveredAt().isPresent())
      assertEquals(events.getFirst().occurredAt(), timeline.firstDeliveredAt().get());
    else fail("Expected first delivered, but got empty optional");

    if (timeline.rejectedOn().isPresent())
      assertEquals(events.getLast(), timeline.rejectedOn().get().trackingEvent());
    else fail("Expected rejectedOn for e5, IN_TRANSIT event");
  }

  @ParameterizedTest
  @DisplayName(
      """
  1. e6 -> PICKED_UP
  2. e7 -> CANCELLED
  3. e8 -> DELIVERED@T(5)
  Result: finalState -> CANCELLED, firstDeliveredAt -> Empty, afterDelivered -> Empty, rejectedOn -> DELIVERED
  """)
  @CsvSource({
    """
  '
  eventId=e6,shipmentId=s5,eventType=PICKED_UP,occurredAt=BASE_INSTANT + @T(0)
  eventId=e7,shipmentId=s5,eventType=CANCELLED,occurredAt=BASE_INSTANT + @T(0)
  eventId=e8,shipmentId=s5,eventType=DELIVERED,occurredAt=BASE_INSTANT + @T(5)
  '
  """
  })
  public void test_rebuild_5(
      @AggregateWith(EventGenerator.TrackingEventListAggregator.class) List<TrackingEvent> events) {
    String carrier = "fedex";
    String shipmentId = "s5";
    List<AppendResult> appendResults =
        events.stream().map(event -> appendPort.append(carrier, event)).toList();
    for (int i = 0; i < events.size(); i++) {
      switch (appendResults.get(i)) {
        case AppendResult.Inserted(String eventId) ->
            assertEquals(events.get(i).eventId(), eventId);
        case AppendResult.Duplicate _ ->
            fail("Expected Inserted with eventId = " + events.get(i).eventId());
      }
    }

    Timeline timeline = eventLog.rebuild(carrier, shipmentId);

    assertEquals(ShipmentState.CANCELLED, timeline.finalState());
    assertEquals(Optional.empty(), timeline.firstDeliveredAt());
    assertEquals(List.of(), timeline.afterDelivered());
    if (timeline.rejectedOn().isPresent()) {
      assertEquals(events.get(2), timeline.rejectedOn().get().trackingEvent());
      switch (timeline.rejectedOn().get().appliedResult()) {
        case AppliedResult.Rejected(String reason) -> {
          assertEquals(AppliedResult.Rejected.REASON_CANCELLED, reason);
          assertEquals(
              EventType.DELIVERED, timeline.rejectedOn().get().trackingEvent().eventType());
        }
        case AppliedResult.Applied _, AppliedResult.Ignored _ -> fail("Expected RejectedOn");
      }
    } else fail("Expected rejectedOn Delivered event");
  }

  @ParameterizedTest
  @DisplayName(
      """
  1.NULL occurredAt
  Result: finalState -> CREATED,afterDelivered -> Empty, firstDeliveredAt -> empty, rejectedOn -> missing_time
  """)
  @CsvSource({
    """
    '
    eventId=e9,shipmentId=s6,eventType=DELIVERED,occurredAt=BASE_INSTANT + @T(NULL)
    '
    """
  })
  void test_rebuild_6(
      @AggregateWith(EventGenerator.TrackingEventListAggregator.class) List<TrackingEvent> events) {
    String carrier = "fedex";
    String shipmentId = "s6";

    List<AppendResult> appendResults =
        events.stream().map(event -> appendPort.append(carrier, event)).toList();
    for (int i = 0; i < events.size(); i++) {
      switch (appendResults.get(i)) {
        case AppendResult.Inserted(String eventId) ->
            assertEquals(events.get(i).eventId(), eventId);
        case AppendResult.Duplicate _ ->
            fail("Expected Inserted with eventId = " + events.get(i).eventId());
      }
    }

    Timeline timeline = eventLog.rebuild(carrier, shipmentId);

    assertEquals(ShipmentState.CREATED, timeline.finalState());
    assertEquals(List.of(), timeline.afterDelivered());
    assertEquals(Optional.empty(), timeline.firstDeliveredAt());
    if (timeline.rejectedOn().isPresent()) {
      switch (timeline.rejectedOn().get().appliedResult()) {
        case AppliedResult.Rejected(String reason) -> {
          assertEquals(AppliedResult.Rejected.REASON_MISSING_TIME, reason);
          assertNull(timeline.rejectedOn().get().trackingEvent().occurredAt());
        }
        case AppliedResult.Applied _, AppliedResult.Ignored _ ->
            fail("Expected appliedResult to be of type RejectedOn");
      }
    } else {
      fail("Expected RejectedOn");
    }
  }

  @ParameterizedTest
  @DisplayName(
      """
  1. IN_TRANSIT@T(1)
  2. OUT_FOR_DELIVERY -> null time
  3. DELIVERED@T(3)
  Result: finalState -> OUT_FOR_DELIVERY, afterDelivered -> empty, firstDeliveredAt -> empty, rejectedOn -> null time
  """)
  @CsvSource({
    """
    '
    eventId=e1,shipmentId=s7,eventType=IN_TRANSIT,occurredAt=BASE_INSTANT + @T(1)
    eventId=e2,shipmentId=s7,eventType=OUT_FOR_DELIVERY,occurredAt=BASE_INSTANT + @T(NULL)
    eventId=e3,shipmentId=s7,eventType=DELIVERED,occurredAt=BASE_INSTANT + @T(3)
    '
    """
  })
  void test_rebuild_7(
      @AggregateWith(EventGenerator.TrackingEventListAggregator.class) List<TrackingEvent> events) {
    String carrier = "fedex";
    String shipmentId = "s7";

    List<AppendResult> appendResults =
        events.stream().map(event -> appendPort.append(carrier, event)).toList();
    for (int i = 0; i < events.size(); i++) {
      switch (appendResults.get(i)) {
        case AppendResult.Inserted(String eventId) ->
            assertEquals(events.get(i).eventId(), eventId);
        case AppendResult.Duplicate _ ->
            fail("Expected Inserted with eventId = " + events.get(i).eventId());
      }
    }

    Timeline timeline = eventLog.rebuild(carrier, shipmentId);
    assertEquals(ShipmentState.IN_TRANSIT, timeline.finalState());
    assertEquals(List.of(), timeline.afterDelivered());
    assertEquals(Optional.empty(), timeline.firstDeliveredAt());
    if (timeline.rejectedOn().isPresent()) {
      switch (timeline.rejectedOn().get().appliedResult()) {
        case AppliedResult.Rejected(String reason) -> {
          assertEquals(AppliedResult.Rejected.REASON_MISSING_TIME, reason);
          assertNull(timeline.rejectedOn().get().trackingEvent().occurredAt());
          assertEquals(events.get(1), timeline.rejectedOn().get().trackingEvent());
        }
        case AppliedResult.Applied _, AppliedResult.Ignored _ ->
            fail("Expected appliedResult to be of type RejectedOn");
      }
    } else {
      fail("Expected RejectedOn");
    }
  }

  @ParameterizedTest
  @DisplayName(
      """
    Test sort order for 2 events with ids e2,e10. When inserted e1 first e10 second, loaded data
    must be in the same order. We use received_at, event_id as the sort key to make sure the data is
    loaded in order
    """)
  @CsvSource({
    """
  '
  eventId=e2,shipmentId=s8,eventType=IN_TRANSIT,occurredAt=BASE_INSTANT + @T(1)
  eventId=e10,shipmentId=s8,eventType=OUT_FOR_DELIVERY,occurredAt=BASE_INSTANT + @T(2)
  '
  """
  })
  void test_rebuild_8(
      @AggregateWith(EventGenerator.TrackingEventListAggregator.class) List<TrackingEvent> events) {
    String carrier = "fedex";
    String shipmentId = "s8";

    List<AppendResult> appendResults =
        events.stream().map(event -> appendPort.append(carrier, event)).toList();
    for (int i = 0; i < events.size(); i++) {
      switch (appendResults.get(i)) {
        case AppendResult.Inserted(String eventId) ->
            assertEquals(events.get(i).eventId(), eventId);
        case AppendResult.Duplicate _ ->
            fail("Expected Inserted with eventId = " + events.get(i).eventId());
      }
    }

    List<TrackingEvent> eventsFromDb = loadPort.load(carrier, shipmentId);
    assertEquals(2, eventsFromDb.size());
    assertEquals(events.getFirst(), eventsFromDb.getFirst());
    assertEquals(events.getLast(), eventsFromDb.getLast());
  }
}
