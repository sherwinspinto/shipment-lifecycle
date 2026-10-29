package me.sspjavalabs.shipmentlifecycle.fileparser.edi214;

import me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser;
import me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser.Segment;
import me.sspjavalabs.shipmentlifecycle.fileparser.edi214.EdiShipment.ShipmentResult.Shipment;
import me.sspjavalabs.shipmentlifecycle.fileparser.edi214.EdiShipment.StatusLoop;
import me.sspjavalabs.shipmentlifecycle.fileparser.edi214.ShipmentProcessor.Result;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipmentProcessorTest {
  static final ShipmentProcessor shipmentProcessor = new ShipmentProcessor();

  @Test
  @DisplayName("one AT7 D → DELIVERED")
  void test_1() {
    String carrier = "ups";
    String trackingNumber = "tracking_number";
    String eventId = "1";
    Shipment shipment = new Shipment(
      new Segment("B10", List.of("Something", trackingNumber)),
      List.of(new StatusLoop(List.of(new Segment("LX", List.of()), new Segment("AT7", List.of("D"))))),
      List.of());
    Result expected = new Result(List.of(TrackingEvent.of(trackingNumber, eventId, EventType.DELIVERED, null)), null);
    Result actual = shipmentProcessor.process(shipment);
    assertEquals(expected, actual);
  }

  @Test
  @DisplayName("2 loop order kept")
  void test_2() {
    String trackingNumber = "tracking_number";
    String eventId = "1";
    Shipment shipment = new Shipment(
      new Segment("B10", List.of("Something", trackingNumber)),
      List.of(new StatusLoop(List.of(new Segment("LX", List.of()), new Segment("AT7", List.of("NS")))),
        new StatusLoop(List.of(new Segment("LX", List.of()), new Segment("AT7", List.of("D"))))),
      List.of());
    Result expected = new Result(
      List.of(TrackingEvent.of(trackingNumber, eventId, EventType.IN_TRANSIT, null),
        TrackingEvent.of(trackingNumber, eventId, EventType.DELIVERED, null)), null);
    Result actual = shipmentProcessor.process(shipment);
    assertEquals(expected, actual);
  }

  @Test
  @DisplayName("unknown code skipped")
  void test_3() {
    String trackingNumber = "tracking_number";
    String eventId = "1";
    Shipment shipment = new Shipment(
      new Segment("B10", List.of("Something", trackingNumber)),
      List.of(new StatusLoop(List.of(new Segment("LX", List.of()), new Segment("AT7", List.of("X3")))),
        new StatusLoop(List.of(new Segment("LX", List.of()), new Segment("AT7", List.of("D"))))),
      List.of());
    Result expected = new Result(
      List.of(
        TrackingEvent.of(trackingNumber, eventId, EventType.DELIVERED, null)), null);
    Result actual = shipmentProcessor.process(shipment);
    assertEquals(expected, actual);
  }

  @Test
  @DisplayName("no loop, empty list")
  void test_4() {
    String trackingNumber = "tracking_number";
    String eventId = "1";
    Shipment shipment = new Shipment(
      new Segment("B10", List.of("Something", trackingNumber)),
      List.of(),
      List.of());
    Result expected = new Result(
      List.of(), null);
    Result actual = shipmentProcessor.process(shipment);
    assertEquals(expected, actual);
  }

  @Test
  @DisplayName("D in other, and D in loop")
  void test_5() {
    String trackingNumber = "tracking_number";
    String eventId = "1";
    Shipment shipment =
        new Shipment(
            new Segment("B10", List.of("Something", trackingNumber)),
            List.of(
                new StatusLoop(
                    List.of(new Segment("LX", List.of()), new Segment("AT7", List.of("D"))))),
            List.of(new Segment("AT7", List.of("D"))));
    Result expected = new Result(
      List.of(TrackingEvent.of(trackingNumber, eventId, EventType.DELIVERED, null)), null);
    Result actual = shipmentProcessor.process(shipment);
    assertEquals(expected, actual);
  }

  @Test
  @DisplayName("LX with AT7 and no status code, followed by LX with AT7 and status code")
  void test_6() {
    String trackingNumber = "tracking_number";
    String eventId = "1";
    Shipment shipment =
      new Shipment(
        new Segment("B10", List.of("Something", trackingNumber)),
        List.of(new StatusLoop(List.of(new Segment("LX", List.of()), new Segment("AT7", List.of()))),
          new StatusLoop(List.of(new Segment("LX", List.of()), new Segment("AT7", List.of("D"))))),
        List.of());
    Result expected = new Result(
      List.of(TrackingEvent.of(trackingNumber, eventId, EventType.DELIVERED, null)), null);
    Result actual = shipmentProcessor.process(shipment);
    assertEquals(expected, actual);
  }
}
