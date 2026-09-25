package me.sspjavalabs.shipmentlifecycle.statemachine.engine;

import static me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShipmentLifecycleTest {
  static String SHIPMENT_ID = "S1";
  static String EVENT_ID = "E1";
  private ShipmentLifecycle shipmentLifecycle;

  @BeforeEach
  void setUp() {
    shipmentLifecycle = new ShipmentLifecycle();
  }

  @Test
  void test_created_to_delivered() {
    AppliedResult expected =
        new AppliedResult.Applied(ShipmentState.DELIVERED);
    TrackingEvent trackingEvent =
        new TrackingEvent(
            SHIPMENT_ID, EVENT_ID, DELIVERED);
    assertEquals(
        expected, shipmentLifecycle.apply(ShipmentState.CREATED, trackingEvent));
  }

  @Test
  void test_created_to_ofd() {
    AppliedResult expected =
        new AppliedResult.Applied(
            ShipmentState.OUT_FOR_DELIVERY);
    TrackingEvent trackingEvent =
        new TrackingEvent(
            SHIPMENT_ID, EVENT_ID, OUT_FOR_DELIVERY);
    assertEquals(
        expected, shipmentLifecycle.apply(ShipmentState.CREATED, trackingEvent));
  }

  @Test
  void test_ofd_to_in_transit() {
    AppliedResult expected = new AppliedResult.Ignored();
    TrackingEvent trackingEvent =
        new TrackingEvent(
            SHIPMENT_ID, EVENT_ID, EventType.IN_TRANSIT);
    assertEquals(
        expected,
        shipmentLifecycle.apply(ShipmentState.OUT_FOR_DELIVERY, trackingEvent));
  }

  @Test
  void test_delivered_to_delivered() {
    AppliedResult expected = new AppliedResult.Ignored();
    TrackingEvent trackingEvent =
        new TrackingEvent(
            SHIPMENT_ID, EVENT_ID, DELIVERED);
    assertEquals(
        expected,
        shipmentLifecycle.apply(ShipmentState.DELIVERED, trackingEvent));
  }

  @Test
  void test_delivered_to_in_transit() {
    AppliedResult expected =
        new AppliedResult.Rejected("after_delivered");
    TrackingEvent trackingEvent =
        new TrackingEvent(
            SHIPMENT_ID, EVENT_ID, EventType.IN_TRANSIT);
    assertEquals(
        expected,
        shipmentLifecycle.apply(ShipmentState.DELIVERED, trackingEvent));
  }

  @Test
  void test_cancelled_to_delivered() {
    AppliedResult expected =
        new AppliedResult.Rejected("cancelled");
    TrackingEvent trackingEvent =
        new TrackingEvent(
            SHIPMENT_ID, EVENT_ID, DELIVERED);
    assertEquals(
        expected,
        shipmentLifecycle.apply(ShipmentState.CANCELLED, trackingEvent));
  }

  @Test
  @DisplayName("Test []")
  void test_fold_1 () {
    List<EventType> eventTypes = Collections.emptyList();
    FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentState.CREATED, foldResult.finalState());
    assertTrue(foldResult.rejectedOn().isEmpty());
  }

  @Test
  @DisplayName("Test [PICKED_UP, OUT_FOR_DELIVERY, DELIVERED]")
  void test_fold_2 () {
    List<EventType> eventTypes = List.of(PICKED_UP, OUT_FOR_DELIVERY, DELIVERED);
    FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentState.DELIVERED, foldResult.finalState());
    assertTrue(foldResult.rejectedOn().isEmpty());
  }

  @Test
  @DisplayName("Test [DELIVERED]")
  void test_fold_3 () {
    List<EventType> eventTypes = List.of(DELIVERED);
    FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentState.DELIVERED, foldResult.finalState());
    assertTrue(foldResult.rejectedOn().isEmpty());
  }

  @Test
  @DisplayName("Test [DELIVERED, DELIVERED]")
  void test_fold_4 () {
    List<EventType> eventTypes = List.of(DELIVERED, DELIVERED);
    FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentState.DELIVERED, foldResult.finalState());
    assertEquals(AppliedResult.Ignored.class, foldResult.appliedResults().get(1).getClass());
    assertTrue(foldResult.rejectedOn().isEmpty());
  }

  @Test
  @DisplayName("Test [DELIVERED, IN_TRANSIT]")
  void test_fold_5 () {
    List<EventType> eventTypes = List.of(DELIVERED, IN_TRANSIT);
    FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentState.DELIVERED, foldResult.finalState());
    assertEquals(AppliedResult.Rejected.class, foldResult.rejectedOn().get().getClass());
    assertEquals("after_delivered", foldResult.rejectedOn().get().reason());
  }

  @Test
  @DisplayName("Test [EXCEPTION, IN_TRANSIT]")
  void test_fold_6 () {
    List<EventType> eventTypes = List.of(EXCEPTION, IN_TRANSIT);
    FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentState.IN_TRANSIT, foldResult.finalState());
    assertTrue(foldResult.rejectedOn().isEmpty());
  }

  @Test
  @DisplayName("Test [CANCELLED, DELIVERED]")
  void test_fold_7 () {
    List<EventType> eventTypes = List.of(CANCELLED, DELIVERED);
    FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentState.CANCELLED, foldResult.finalState());
    assertEquals(AppliedResult.Rejected.class, foldResult.rejectedOn().get().getClass());
    assertEquals("cancelled", foldResult.rejectedOn().get().reason());
  }

  @Test
  @DisplayName("Test [OUT_FOR_DELIVERY, IN_TRANSIT]")
  void test_fold_8 () {
    List<EventType> eventTypes = List.of(OUT_FOR_DELIVERY, IN_TRANSIT);
    FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentState.OUT_FOR_DELIVERY, foldResult.finalState());
    assertEquals(AppliedResult.Ignored.class, foldResult.appliedResults().get(1).getClass());
    assertTrue(foldResult.rejectedOn().isEmpty());
  }

  @Test
  @DisplayName("Test [IN_TRANSIT, LABEL_CREATED]")
   void test_fold_9 () {
    List<EventType> eventTypes = List.of(IN_TRANSIT, LABEL_CREATED);
    FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentState.IN_TRANSIT, foldResult.finalState());
    assertEquals(AppliedResult.Rejected.class, foldResult.rejectedOn().get().getClass());
    assertEquals("no transition", foldResult.rejectedOn().get().reason());
  }

  @Test
  @DisplayName("Test [PICKED_UP, CANCELLED, DELIVERED]")
  void test_fold_10 () {
    List<EventType> eventTypes = List.of(PICKED_UP, CANCELLED, DELIVERED);
    FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentState.CANCELLED, foldResult.finalState());
    assertEquals(AppliedResult.Rejected.class, foldResult.rejectedOn().get().getClass());
    assertEquals(2, foldResult.appliedResults().size());
    assertEquals("cancelled", foldResult.rejectedOn().get().reason());
  }



  FoldResult executeFoldTest (List<EventType> eventTypes) {
    List<TrackingEvent> trackingEvents = createListOfTrackingEventsFromEventTypes(eventTypes, SHIPMENT_ID);
    return shipmentLifecycle.fold(trackingEvents);
  }

  static List<TrackingEvent> createListOfTrackingEventsFromEventTypes(
    List<EventType> eventTypes, String shipmentId) {
    return eventTypes.stream()
        .map(
            eventType ->
                createTrackingEventFromShipmentState(
                    eventType, shipmentId, UUID.randomUUID().toString()))
        .toList();
  }

  static TrackingEvent createTrackingEventFromShipmentState(
    EventType eventType, String shipmentId, String eventId) {
    return new TrackingEvent(shipmentId, eventId, eventType);
  }
}
