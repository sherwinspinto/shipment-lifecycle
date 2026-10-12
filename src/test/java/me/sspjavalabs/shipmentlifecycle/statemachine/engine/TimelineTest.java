package me.sspjavalabs.shipmentlifecycle.statemachine.engine;

import static me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType.CANCELLED;
import static me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType.DELIVERED;
import static me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType.IN_TRANSIT;
import static me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType.PICKED_UP;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class TimelineTest {
  private static final Instant baseInstant = Instant.parse("2026-08-06T18:00:00Z");
  private static ShipmentLifecycle shipmentLifecycle;
  static String SHIPMENT_ID = "S1";

  @BeforeEach
  void initialise() {
    shipmentLifecycle = new ShipmentLifecycle();
  }

  @Test
  @DisplayName("Test `PICKED_UP@T(0)`, `DELIVERED@T(2)`")
  void test_summarize_1() {
    List<ShipmentLifecycleTest.Tuple<EventType, Instant>> eventTypesAndInstants =
        List.of(
            new ShipmentLifecycleTest.Tuple<>(PICKED_UP, baseInstant),
            new ShipmentLifecycleTest.Tuple<>(DELIVERED, baseInstant.plus(2, ChronoUnit.HOURS)));
    Timeline timeline =
        shipmentLifecycle.summarize(executeFoldTestForSummarize(eventTypesAndInstants));

    assertEquals(ShipmentState.DELIVERED, timeline.finalState());

    validateFirstDeliveredAt(timeline, getInstantWithOffsetFromBaseInstant(baseInstant, 2));

    assertEquals(Collections.emptyList(), timeline.afterDelivered());
    assertEquals(Optional.empty(), timeline.rejectedOn());
  }

  @Test
  @DisplayName("Test `DELIVERED@T(5)`, `DELIVERED@T(1)`")
  void test_summarize_2() {
    List<ShipmentLifecycleTest.Tuple<EventType, Instant>> eventTypesAndInstants =
        List.of(
            new ShipmentLifecycleTest.Tuple<>(DELIVERED, baseInstant.plus(5, ChronoUnit.HOURS)),
            new ShipmentLifecycleTest.Tuple<>(DELIVERED, baseInstant.plus(1, ChronoUnit.HOURS)));
    Timeline timeline =
        shipmentLifecycle.summarize(executeFoldTestForSummarize(eventTypesAndInstants));

    assertEquals(ShipmentState.DELIVERED, timeline.finalState());

    Instant firstDeliveredAtResult =
        validateFirstDeliveredAt(timeline, getInstantWithOffsetFromBaseInstant(baseInstant, 1));

    assertEquals(1, timeline.afterDelivered().size());
    assertEquals(DELIVERED, timeline.afterDelivered().getFirst().eventType());

    assert firstDeliveredAtResult != null;
    assertTrue(firstDeliveredAtResult.isBefore(timeline.afterDelivered().getFirst().occurredAt()));

    assertEquals(Optional.empty(), timeline.rejectedOn());
  }

  @Test
  @DisplayName("DELIVERED@T(0), IN_TRANSIT@T(1)")
  void test_summarize_3() {
    List<ShipmentLifecycleTest.Tuple<EventType, Instant>> eventTypesAndInstants =
        List.of(
            new ShipmentLifecycleTest.Tuple<>(DELIVERED, baseInstant.plus(0, ChronoUnit.HOURS)),
            new ShipmentLifecycleTest.Tuple<>(IN_TRANSIT, baseInstant.plus(1, ChronoUnit.HOURS)));
    Timeline timeline =
        shipmentLifecycle.summarize(executeFoldTestForSummarize(eventTypesAndInstants));

    assertEquals(ShipmentState.DELIVERED, timeline.finalState());

    Instant firstDeliveredAtResult =
        validateFirstDeliveredAt(timeline, getInstantWithOffsetFromBaseInstant(baseInstant, 0));

    assertEquals(1, timeline.afterDelivered().size());
    assertEquals(IN_TRANSIT, timeline.afterDelivered().getFirst().eventType());
    assert firstDeliveredAtResult != null;
    assertTrue(firstDeliveredAtResult.isBefore(timeline.afterDelivered().getFirst().occurredAt()));

    if (timeline.rejectedOn().isPresent()) {
      switch (timeline.rejectedOn().get().appliedResult()) {
        case AppliedResult.Rejected(String reason) -> assertEquals("after_delivered", reason);
        case AppliedResult.Applied _, AppliedResult.Ignored _ -> fail("Expected Rejected");
      }
    } else fail("Expected rejectedOn with Rejected");
  }

  @Test
  @DisplayName("IN_TRANSIT@T(3), DELIVERED@T(1)")
  void test_summarize_4() {
    List<ShipmentLifecycleTest.Tuple<EventType, Instant>> eventTypesAndInstants =
        List.of(
            new ShipmentLifecycleTest.Tuple<>(IN_TRANSIT, baseInstant.plus(3, ChronoUnit.HOURS)),
            new ShipmentLifecycleTest.Tuple<>(DELIVERED, baseInstant.plus(1, ChronoUnit.HOURS)));
    Timeline timeline =
        shipmentLifecycle.summarize(executeFoldTestForSummarize(eventTypesAndInstants));

    assertEquals(ShipmentState.DELIVERED, timeline.finalState());

    Instant firstDeliveredAtResult =
        validateFirstDeliveredAt(timeline, getInstantWithOffsetFromBaseInstant(baseInstant, 1));

    assertEquals(1, timeline.afterDelivered().size());
    assertEquals(IN_TRANSIT, timeline.afterDelivered().getFirst().eventType());

    assert firstDeliveredAtResult != null;
    assertTrue(firstDeliveredAtResult.isBefore(timeline.afterDelivered().getFirst().occurredAt()));
  }

  @Test
  @DisplayName("PICKED_UP@T(0), IN_TRANSIT@T(1)")
  void test_summarize_5() {
    List<ShipmentLifecycleTest.Tuple<EventType, Instant>> eventTypesAndInstants =
        List.of(
            new ShipmentLifecycleTest.Tuple<>(PICKED_UP, baseInstant.plus(0, ChronoUnit.HOURS)),
            new ShipmentLifecycleTest.Tuple<>(IN_TRANSIT, baseInstant.plus(1, ChronoUnit.HOURS)));
    Timeline timeline =
        shipmentLifecycle.summarize(executeFoldTestForSummarize(eventTypesAndInstants));

    assertEquals(ShipmentState.IN_TRANSIT, timeline.finalState());

    assertEquals(Optional.empty(), timeline.firstDeliveredAt());

    assertEquals(Collections.emptyList(), timeline.afterDelivered());

    assertEquals(Optional.empty(), timeline.rejectedOn());
  }

  @Test
  @DisplayName("DELIVERED@T(2), DELIVERED@T(2)")
  void test_summarize_6() {
    List<ShipmentLifecycleTest.Tuple<EventType, Instant>> eventTypesAndInstants =
        List.of(
            new ShipmentLifecycleTest.Tuple<>(DELIVERED, baseInstant.plus(2, ChronoUnit.HOURS)),
            new ShipmentLifecycleTest.Tuple<>(DELIVERED, baseInstant.plus(2, ChronoUnit.HOURS)));
    Timeline timeline =
        shipmentLifecycle.summarize(executeFoldTestForSummarize(eventTypesAndInstants));

    assertEquals(ShipmentState.DELIVERED, timeline.finalState());

    validateFirstDeliveredAt(timeline, getInstantWithOffsetFromBaseInstant(baseInstant, 2));

    assertEquals(Collections.emptyList(), timeline.afterDelivered());

    assertEquals(Optional.empty(), timeline.rejectedOn());
  }

  @Test
  @DisplayName("empty")
  void test_summarize_7() {
    List<ShipmentLifecycleTest.Tuple<EventType, Instant>> eventTypesAndInstants = List.of();
    Timeline timeline =
        shipmentLifecycle.summarize(executeFoldTestForSummarize(eventTypesAndInstants));

    assertEquals(ShipmentState.CREATED, timeline.finalState());

    assertEquals(Optional.empty(), timeline.firstDeliveredAt());

    assertEquals(Collections.emptyList(), timeline.afterDelivered());

    assertEquals(Optional.empty(), timeline.rejectedOn());
  }

  @Test
  @DisplayName("PICKED_UP@T(0), DELIVERED NULL time")
  void test_summarize_8() {
    List<ShipmentLifecycleTest.Tuple<EventType, Instant>> eventTypesAndInstants =
        List.of(
            new ShipmentLifecycleTest.Tuple<>(PICKED_UP, baseInstant.plus(0, ChronoUnit.HOURS)),
            new ShipmentLifecycleTest.Tuple<>(DELIVERED, null));
    Timeline timeline =
        shipmentLifecycle.summarize(executeFoldTestForSummarize(eventTypesAndInstants));

    assertEquals(ShipmentState.IN_TRANSIT, timeline.finalState());

    assertEquals(Optional.empty(), timeline.firstDeliveredAt());

    assertEquals(Collections.emptyList(), timeline.afterDelivered());

    if (timeline.rejectedOn().isPresent()) {
      switch (timeline.rejectedOn().get().appliedResult()) {
        case AppliedResult.Rejected(String reason) -> {
          assertEquals("missing_time", reason);
          assertTrue(Objects.isNull(timeline.rejectedOn().get().trackingEvent().occurredAt()));
        }
        case AppliedResult.Applied _, AppliedResult.Ignored _ -> fail("Expected Rejected");
      }
    } else fail("Expected halted on");
  }

  @Test
  @DisplayName("PICKED_UP@T(0), CANCELLED@T(1), DELIVERED@T(5)")
  void test_summarize_9() {
    List<ShipmentLifecycleTest.Tuple<EventType, Instant>> eventTypesAndInstants =
        List.of(
            new ShipmentLifecycleTest.Tuple<>(PICKED_UP, baseInstant.plus(0, ChronoUnit.HOURS)),
            new ShipmentLifecycleTest.Tuple<>(CANCELLED, baseInstant.plus(1, ChronoUnit.HOURS)),
            new ShipmentLifecycleTest.Tuple<>(DELIVERED, baseInstant.plus(5, ChronoUnit.HOURS)));

    Timeline timeline =
        shipmentLifecycle.summarize(executeFoldTestForSummarize(eventTypesAndInstants));

    assertEquals(ShipmentState.CANCELLED, timeline.finalState());

    assertEquals(Optional.empty(), timeline.firstDeliveredAt());

    assertEquals(Collections.emptyList(), timeline.afterDelivered());

    if (timeline.rejectedOn().isPresent()) {
      switch (timeline.rejectedOn().get().appliedResult()) {
        case AppliedResult.Rejected(String reason) -> {
          assertEquals("cancelled", reason);
          assertEquals(DELIVERED, timeline.rejectedOn().get().trackingEvent().eventType());
          assertEquals(
              baseInstant.plus(5, ChronoUnit.HOURS),
              timeline.rejectedOn().get().trackingEvent().occurredAt());
        }
        case AppliedResult.Applied _, AppliedResult.Ignored _ -> fail("Expected Rejected");
      }
    } else fail("Expected halted on");
  }

  FoldResult executeFoldTestForSummarize(
      List<ShipmentLifecycleTest.Tuple<EventType, Instant>> eventTypesAndInstants) {
    List<TrackingEvent> trackingEvents =
        createListOfTrackingEventsFromEventTypesAndInstants(eventTypesAndInstants, SHIPMENT_ID);
    return shipmentLifecycle.fold(trackingEvents);
  }

  static List<TrackingEvent> createListOfTrackingEventsFromEventTypesAndInstants(
      List<ShipmentLifecycleTest.Tuple<EventType, Instant>> eventTypesAndInstants,
      String shipmentId) {
    return eventTypesAndInstants.stream()
        .map(
            tup ->
                createTrackingEventFromShipmentState(
                    tup.v1(), shipmentId, UUID.randomUUID().toString(), tup.v2()))
        .toList();
  }

  static TrackingEvent createTrackingEventFromShipmentState(
      EventType eventType, String shipmentId, String eventId, Instant occurredAt) {
    return new TrackingEvent(shipmentId, eventId, eventType, occurredAt);
  }

  static Instant getInstantWithOffsetFromBaseInstant(Instant baseInstant, int offset) {
    return baseInstant.plus(offset, ChronoUnit.HOURS);
  }

  static Instant validateFirstDeliveredAt(Timeline timeline, Instant expected) {
    if (timeline.firstDeliveredAt().isPresent()) {
      assertEquals(expected, timeline.firstDeliveredAt().get());
      return timeline.firstDeliveredAt().get();
    } else fail("Expected firstDeliveredAt");
    return null;
  }
}
