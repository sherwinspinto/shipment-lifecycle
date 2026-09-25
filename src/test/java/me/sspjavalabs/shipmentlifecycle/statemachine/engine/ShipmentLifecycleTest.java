package me.sspjavalabs.shipmentlifecycle.statemachine.engine;

import static me.sspjavalabs.shipmentlifecycle.statemachine.engine.ShipmentLifecycle.EventType.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
    ShipmentLifecycle.AppliedResult expected =
        new ShipmentLifecycle.AppliedResult.Applied(ShipmentLifecycle.ShipmentState.DELIVERED);
    ShipmentLifecycle.TrackingEvent trackingEvent =
        new ShipmentLifecycle.TrackingEvent(
            SHIPMENT_ID, EVENT_ID, DELIVERED);
    assertEquals(
        expected, shipmentLifecycle.apply(ShipmentLifecycle.ShipmentState.CREATED, trackingEvent));
  }

  @Test
  void test_created_to_ofd() {
    ShipmentLifecycle.AppliedResult expected =
        new ShipmentLifecycle.AppliedResult.Applied(
            ShipmentLifecycle.ShipmentState.OUT_FOR_DELIVERY);
    ShipmentLifecycle.TrackingEvent trackingEvent =
        new ShipmentLifecycle.TrackingEvent(
            SHIPMENT_ID, EVENT_ID, OUT_FOR_DELIVERY);
    assertEquals(
        expected, shipmentLifecycle.apply(ShipmentLifecycle.ShipmentState.CREATED, trackingEvent));
  }

  @Test
  void test_ofd_to_in_transit() {
    ShipmentLifecycle.AppliedResult expected = new ShipmentLifecycle.AppliedResult.Ignored();
    ShipmentLifecycle.TrackingEvent trackingEvent =
        new ShipmentLifecycle.TrackingEvent(
            SHIPMENT_ID, EVENT_ID, ShipmentLifecycle.EventType.IN_TRANSIT);
    assertEquals(
        expected,
        shipmentLifecycle.apply(ShipmentLifecycle.ShipmentState.OUT_FOR_DELIVERY, trackingEvent));
  }

  @Test
  void test_delivered_to_delivered() {
    ShipmentLifecycle.AppliedResult expected = new ShipmentLifecycle.AppliedResult.Ignored();
    ShipmentLifecycle.TrackingEvent trackingEvent =
        new ShipmentLifecycle.TrackingEvent(
            SHIPMENT_ID, EVENT_ID, DELIVERED);
    assertEquals(
        expected,
        shipmentLifecycle.apply(ShipmentLifecycle.ShipmentState.DELIVERED, trackingEvent));
  }

  @Test
  void test_delivered_to_in_transit() {
    ShipmentLifecycle.AppliedResult expected =
        new ShipmentLifecycle.AppliedResult.Rejected("after_delivered");
    ShipmentLifecycle.TrackingEvent trackingEvent =
        new ShipmentLifecycle.TrackingEvent(
            SHIPMENT_ID, EVENT_ID, ShipmentLifecycle.EventType.IN_TRANSIT);
    assertEquals(
        expected,
        shipmentLifecycle.apply(ShipmentLifecycle.ShipmentState.DELIVERED, trackingEvent));
  }

  @Test
  void test_cancelled_to_delivered() {
    ShipmentLifecycle.AppliedResult expected =
        new ShipmentLifecycle.AppliedResult.Rejected("cancelled");
    ShipmentLifecycle.TrackingEvent trackingEvent =
        new ShipmentLifecycle.TrackingEvent(
            SHIPMENT_ID, EVENT_ID, DELIVERED);
    assertEquals(
        expected,
        shipmentLifecycle.apply(ShipmentLifecycle.ShipmentState.CANCELLED, trackingEvent));
  }

  @Test
  @DisplayName("Test []")
  void test_fold_1 () {
    List<ShipmentLifecycle.EventType> eventTypes = Collections.emptyList();
    ShipmentLifecycle.FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentLifecycle.ShipmentState.CREATED, foldResult.finalState());
    assertTrue(foldResult.rejectedOn().isEmpty());
  }

  @Test
  @DisplayName("Test [PICKED_UP, OUT_FOR_DELIVERY, DELIVERED]")
  void test_fold_2 () {
    List<ShipmentLifecycle.EventType> eventTypes = List.of(PICKED_UP, OUT_FOR_DELIVERY, DELIVERED);
    ShipmentLifecycle.FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentLifecycle.ShipmentState.DELIVERED, foldResult.finalState());
    assertTrue(foldResult.rejectedOn().isEmpty());
  }

  @Test
  @DisplayName("Test [DELIVERED]")
  void test_fold_3 () {
    List<ShipmentLifecycle.EventType> eventTypes = List.of(DELIVERED);
    ShipmentLifecycle.FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentLifecycle.ShipmentState.DELIVERED, foldResult.finalState());
    assertTrue(foldResult.rejectedOn().isEmpty());
  }

  @Test
  @DisplayName("Test [DELIVERED, DELIVERED]")
  void test_fold_4 () {
    List<ShipmentLifecycle.EventType> eventTypes = List.of(DELIVERED, DELIVERED);
    ShipmentLifecycle.FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentLifecycle.ShipmentState.DELIVERED, foldResult.finalState());
    assertEquals(ShipmentLifecycle.AppliedResult.Ignored.class, foldResult.appliedResults().get(1).getClass());
    assertTrue(foldResult.rejectedOn().isEmpty());
  }

  @Test
  @DisplayName("Test [DELIVERED, IN_TRANSIT]")
  void test_fold_5 () {
    List<ShipmentLifecycle.EventType> eventTypes = List.of(DELIVERED, IN_TRANSIT);
    ShipmentLifecycle.FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentLifecycle.ShipmentState.DELIVERED, foldResult.finalState());
    assertEquals(ShipmentLifecycle.AppliedResult.Rejected.class, foldResult.rejectedOn().get().getClass());
    assertEquals("after_delivered", foldResult.rejectedOn().get().reason());
  }

  @Test
  @DisplayName("Test [EXCEPTION, IN_TRANSIT]")
  void test_fold_6 () {
    List<ShipmentLifecycle.EventType> eventTypes = List.of(EXCEPTION, IN_TRANSIT);
    ShipmentLifecycle.FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentLifecycle.ShipmentState.IN_TRANSIT, foldResult.finalState());
    assertTrue(foldResult.rejectedOn().isEmpty());
  }

  @Test
  @DisplayName("Test [CANCELLED, DELIVERED]")
  void test_fold_7 () {
    List<ShipmentLifecycle.EventType> eventTypes = List.of(CANCELLED, DELIVERED);
    ShipmentLifecycle.FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentLifecycle.ShipmentState.CANCELLED, foldResult.finalState());
    assertEquals(ShipmentLifecycle.AppliedResult.Rejected.class, foldResult.rejectedOn().get().getClass());
    assertEquals("cancelled", foldResult.rejectedOn().get().reason());
  }

  @Test
  @DisplayName("Test [OUT_FOR_DELIVERY, IN_TRANSIT]")
  void test_fold_8 () {
    List<ShipmentLifecycle.EventType> eventTypes = List.of(OUT_FOR_DELIVERY, IN_TRANSIT);
    ShipmentLifecycle.FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentLifecycle.ShipmentState.OUT_FOR_DELIVERY, foldResult.finalState());
    assertEquals(ShipmentLifecycle.AppliedResult.Ignored.class, foldResult.appliedResults().get(1).getClass());
    assertTrue(foldResult.rejectedOn().isEmpty());
  }

  @Test
  @DisplayName("Test [IN_TRANSIT, LABEL_CREATED]")
   void test_fold_9 () {
    List<ShipmentLifecycle.EventType> eventTypes = List.of(IN_TRANSIT, LABEL_CREATED);
    ShipmentLifecycle.FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentLifecycle.ShipmentState.IN_TRANSIT, foldResult.finalState());
    assertEquals(ShipmentLifecycle.AppliedResult.Rejected.class, foldResult.rejectedOn().get().getClass());
    assertEquals("no transition", foldResult.rejectedOn().get().reason());
  }

  @Test
  @DisplayName("Test [PICKED_UP, CANCELLED, DELIVERED]")
  void test_fold_10 () {
    List<ShipmentLifecycle.EventType> eventTypes = List.of(PICKED_UP, CANCELLED, DELIVERED);
    ShipmentLifecycle.FoldResult foldResult = executeFoldTest(eventTypes);
    assertEquals(ShipmentLifecycle.ShipmentState.CANCELLED, foldResult.finalState());
    assertEquals(ShipmentLifecycle.AppliedResult.Rejected.class, foldResult.rejectedOn().get().getClass());
    assertEquals(2, foldResult.appliedResults().size());
    assertEquals("cancelled", foldResult.rejectedOn().get().reason());
  }



  ShipmentLifecycle.FoldResult executeFoldTest (List<ShipmentLifecycle.EventType> eventTypes) {
    List<ShipmentLifecycle.TrackingEvent> trackingEvents = createListOfTrackingEventsFromEventTypes(eventTypes, SHIPMENT_ID);
    return shipmentLifecycle.fold(trackingEvents);
  }

  static List<ShipmentLifecycle.TrackingEvent> createListOfTrackingEventsFromEventTypes(
      List<ShipmentLifecycle.EventType> eventTypes, String shipmentId) {
    return eventTypes.stream()
        .map(
            eventType ->
                createTrackingEventFromShipmentState(
                    eventType, shipmentId, UUID.randomUUID().toString()))
        .toList();
  }

  static ShipmentLifecycle.TrackingEvent createTrackingEventFromShipmentState(
      ShipmentLifecycle.EventType eventType, String shipmentId, String eventId) {
    return new ShipmentLifecycle.TrackingEvent(shipmentId, eventId, eventType);
  }
}
