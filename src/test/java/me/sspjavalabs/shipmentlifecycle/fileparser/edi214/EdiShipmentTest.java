package me.sspjavalabs.shipmentlifecycle.fileparser.edi214;

import static me.sspjavalabs.shipmentlifecycle.fileparser.edi214.EdiShipment.ShipmentResult.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser.Segment;
import me.sspjavalabs.shipmentlifecycle.fileparser.edi214.EdiShipment.ShipmentResult;
import me.sspjavalabs.shipmentlifecycle.fileparser.edi214.EdiShipment.StatusLoop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EdiShipmentTest {
  @Test
  @DisplayName("one shipment, one loop, the `AT7` is in the loop ")
  void test_1() {
    EdiShipment ediShipment = new EdiShipment();
    List<Segment> shipmentSegments =
        List.of(
            new Segment("B10", List.of()),
            new Segment("LX", List.of()),
            new Segment("AT7", List.of()));
    ShipmentResult shipmentResult = ediShipment.getShipment(shipmentSegments);
    ShipmentResult shipmentExpected =
        new Shipment(
            new Segment("B10", List.of()),
            List.of(
                new StatusLoop(
                    List.of(new Segment("LX", List.of()), new Segment("AT7", List.of())))),
            List.of());
    assertEquals(shipmentExpected, shipmentResult);
  }

  @Test
  @DisplayName("two loops. First has two `AT7`s, second has one")
  void test_2() {
    EdiShipment ediShipment = new EdiShipment();
    List<Segment> shipmentSegments =
        List.of(
            new Segment("B10", List.of()),
            new Segment("LX", List.of()),
            new Segment("AT7", List.of()),
            new Segment("AT7", List.of()),
            new Segment("LX", List.of()),
            new Segment("AT7", List.of()));
    ShipmentResult shipmentResult = ediShipment.getShipment(shipmentSegments);
    ShipmentResult shipmentExpected =
        new Shipment(
            new Segment("B10", List.of()),
            List.of(
                new StatusLoop(
                    List.of(
                        new Segment("LX", List.of()),
                        new Segment("AT7", List.of()),
                        new Segment("AT7", List.of()))),
                new StatusLoop(
                    List.of(new Segment("LX", List.of()), new Segment("AT7", List.of())))),
            List.of());
    assertEquals(shipmentExpected, shipmentResult);
  }

  @Test
  @DisplayName("the `AT7` is not in a loop. It lands in `other`. No invented `LX`")
  void test_3() {
    EdiShipment ediShipment = new EdiShipment();
    List<Segment> shipmentSegments =
      List.of(
        new Segment("B10", List.of()),
        new Segment("AT7", List.of()));
    ShipmentResult shipmentResult = ediShipment.getShipment(shipmentSegments);
    ShipmentResult shipmentExpected =
      new Shipment(
        new Segment("B10", List.of()),
        List.of(),
        List.of(new Segment("AT7", List.of())));
    assertEquals(shipmentExpected, shipmentResult);
  }

  @Test
  @DisplayName("no `B10`. A walk failure, not a shipment with an empty `B10` ")
  void test_4() {
    EdiShipment ediShipment = new EdiShipment();
    List<Segment> shipmentSegments =
      List.of(
        new Segment("LX", List.of()),
        new Segment("AT7", List.of()));
    ShipmentResult shipmentResult = ediShipment.getShipment(shipmentSegments);
    ShipmentResult shipmentExpected = new Bad("Missing B10");
    assertEquals(shipmentExpected, shipmentResult);
  }

  @Test
  @DisplayName("`N1` and `SE` in `other`. The loop still holds the `AT7`")
  void test_5() {
    EdiShipment ediShipment = new EdiShipment();
    List<Segment> shipmentSegments =
      List.of(
        new Segment("B10", List.of()),
        new Segment("N1", List.of()),
        new Segment("LX", List.of()),
        new Segment("AT7", List.of()),
        new Segment("SE", List.of())
      );
    ShipmentResult shipmentResult = ediShipment.getShipment(shipmentSegments);
    ShipmentResult shipmentExpected =
        new Shipment(
            new Segment("B10", List.of()),
            List.of(
                new StatusLoop(
                    List.of(new Segment("LX", List.of()), new Segment("AT7", List.of())))),
            List.of(new Segment("N1", List.of()), new Segment("SE", List.of())));
    assertEquals(shipmentExpected, shipmentResult);
  }

  @Test
  @DisplayName("one shipment, no loops, nothing in `other`")
  void test_6() {
    EdiShipment ediShipment = new EdiShipment();
    List<Segment> shipmentSegments =
      List.of(
        new Segment("B10", List.of())
      );
    ShipmentResult shipmentResult = ediShipment.getShipment(shipmentSegments);
    ShipmentResult shipmentExpected =
      new Shipment(
        new Segment("B10", List.of()),
        List.of(),
        List.of());
    assertEquals(shipmentExpected, shipmentResult);
  }

  @Test
  @DisplayName("the second `B10` is in `other`. You do not start a second shipment")
  void test_7() {
    EdiShipment ediShipment = new EdiShipment();
    List<Segment> shipmentSegments =
      List.of(
        new Segment("B10", List.of()),
        new Segment("LX", List.of()),
        new Segment("AT7", List.of()),
        new Segment("B10", List.of())
      );
    ShipmentResult shipmentResult = ediShipment.getShipment(shipmentSegments);
    ShipmentResult shipmentExpected =
        new Shipment(
            new Segment("B10", List.of()),
            List.of(
                new StatusLoop(
                    List.of(new Segment("LX", List.of()), new Segment("AT7", List.of())))),
            List.of(new Segment("B10", List.of())));
    assertEquals(shipmentExpected, shipmentResult);
  }

  @Test
  @DisplayName("LX with no AT7")
  void test_8() {
    EdiShipment ediShipment = new EdiShipment();
    List<Segment> shipmentSegments =
      List.of(
        new Segment("B10", List.of()),
        new Segment("LX", List.of()),
        new Segment("N1", List.of())
      );
    ShipmentResult shipmentResult = ediShipment.getShipment(shipmentSegments);
    ShipmentResult shipmentExpected =
      new Shipment(
        new Segment("B10", List.of()),
        List.of(
          new StatusLoop(
            List.of(new Segment("LX", List.of())))),
        List.of(new Segment("N1", List.of())));
    assertEquals(shipmentExpected, shipmentResult);
  }
}
