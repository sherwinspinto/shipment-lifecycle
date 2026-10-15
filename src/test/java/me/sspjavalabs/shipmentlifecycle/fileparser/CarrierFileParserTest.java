package me.sspjavalabs.shipmentlifecycle.fileparser;

import static me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileParser.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CarrierFileParserTest {
  @ParameterizedTest
  @DisplayName("Header line only")
  @ValueSource(
      strings =
          """
      carrier,shipment_id,event_id,event_type,occurred_at
      """)
  void test_1(String csvString) {
    CarrierFileParser carrierFileParser = new CarrierFileParser();
    ParsedResults parsedResults = carrierFileParser.parse(csvString);
    assertEquals(new ParsedResults(List.of(), List.of()), parsedResults);
  }

  @ParameterizedTest
  @DisplayName("One event")
  @ValueSource(
      strings =
          """
      carrier,shipment_id,event_id,event_type,occurred_at
      fedex,1Z999,e1,DELIVERED,2026-08-06T18:00:00Z
      """)
  void test_2(String csvString) {
    CarrierFileParser carrierFileParser = new CarrierFileParser();
    ParsedResults parsedResults = carrierFileParser.parse(csvString);
    TrackingEvent expectedEvent =
        TrackingEvent.of("1Z999", "e1", EventType.DELIVERED, Instant.parse("2026-08-06T18:00:00Z"));
    ParsedEvent parsedEvent = new ParsedEvent("fedex", expectedEvent);
    List<ParsedEvent> expectedParsedEvents = List.of(parsedEvent);
    assertEquals(new ParsedResults(expectedParsedEvents, List.of()), parsedResults);
  }

  @ParameterizedTest
  @DisplayName("No occurredAt value in file")
  @ValueSource(
      strings =
          """
  carrier,shipment_id,event_id,event_type,occurred_at
  fedex,1Z999,e1,DELIVERED,
  """)
  void test_3(String csvString) {
    CarrierFileParser carrierFileParser = new CarrierFileParser();
    ParsedResults parsedResults = carrierFileParser.parse(csvString);
    TrackingEvent expectedEvent = TrackingEvent.of("1Z999", "e1", EventType.DELIVERED, null);
    ParsedEvent parsedEvent = new ParsedEvent("fedex", expectedEvent);
    List<ParsedEvent> expectedParsedEvents = List.of(parsedEvent);
    assertEquals(new ParsedResults(expectedParsedEvents, List.of()), parsedResults);
  }

  @ParameterizedTest
  @DisplayName("Invalid event type")
  @ValueSource(
      strings =
          """
  carrier,shipment_id,event_id,event_type,occurred_at
  fedex,1Z999,e1,DELIVERED,2026-08-06T18:00:00Z
  fedex,1Z999,e2,INTRANSIT,2026-08-06T17:00:00Z
  """)
  void test_4(String csvString) {
    CarrierFileParser carrierFileParser = new CarrierFileParser();
    ParsedResults parsedResults = carrierFileParser.parse(csvString);
    TrackingEvent expectedEvent =
        TrackingEvent.of("1Z999", "e1", EventType.DELIVERED, Instant.parse("2026-08-06T18:00:00Z"));
    ParsedEvent parsedEvent = new ParsedEvent("fedex", expectedEvent);
    List<ParsedEvent> expectedParsedEvents = List.of(parsedEvent);
    BadRow badRow =
        new BadRow(
            3, "fedex,1Z999,e2,INTRANSIT,2026-08-06T17:00:00Z", "Invalid eventType: INTRANSIT");
    assertEquals(new ParsedResults(expectedParsedEvents, List.of(badRow)), parsedResults);
  }

  @ParameterizedTest
  @DisplayName("Short line")
  @ValueSource(
      strings =
"""
carrier,shipment_id,event_id,event_type,occurred_at
fedex,1Z999,e1,DELIVERED,2026-08-06T18:00:00Z
fedex,1Z999,e2,
""")
  void test_5(String csvString) {
    CarrierFileParser carrierFileParser = new CarrierFileParser();
    ParsedResults parsedResults = carrierFileParser.parse(csvString);
    TrackingEvent expectedEvent =
        TrackingEvent.of("1Z999", "e1", EventType.DELIVERED, Instant.parse("2026-08-06T18:00:00Z"));
    ParsedEvent parsedEvent = new ParsedEvent("fedex", expectedEvent);
    List<ParsedEvent> expectedParsedEvents = List.of(parsedEvent);
    BadRow badRow = new BadRow(3, "fedex,1Z999,e2,", "Short line");
    assertEquals(new ParsedResults(expectedParsedEvents, List.of(badRow)), parsedResults);
  }

  @ParameterizedTest
  @DisplayName("Good, bad, good")
  @ValueSource(
      strings =
"""
carrier,shipment_id,event_id,event_type,occurred_at
fedex,1Z999,e1,DELIVERED,2026-08-06T18:00:00Z
fedex,1Z999,e2,IN_TRANSIT,2026-08-06 18:00:00
fedex,1Z998,e2,DELIVERED,
""")
  void test_6(String csvString) {
    CarrierFileParser carrierFileParser = new CarrierFileParser();
    ParsedResults parsedResults = carrierFileParser.parse(csvString);

    TrackingEvent expectedEvent1 =
        TrackingEvent.of("1Z999", "e1", EventType.DELIVERED, Instant.parse("2026-08-06T18:00:00Z"));
    ParsedEvent parsedEvent1 = new ParsedEvent("fedex", expectedEvent1);

    TrackingEvent expectedEvent2 = TrackingEvent.of("1Z998", "e2", EventType.DELIVERED, null);
    ParsedEvent parsedEvent2 = new ParsedEvent("fedex", expectedEvent2);

    List<ParsedEvent> expectedParsedEvents = List.of(parsedEvent1, parsedEvent2);
    BadRow badRow =
        new BadRow(3, "fedex,1Z999,e2,IN_TRANSIT,2026-08-06 18:00:00", "Unparseable occurredAt");
    assertEquals(new ParsedResults(expectedParsedEvents, List.of(badRow)), parsedResults);
  }

  @ParameterizedTest
  @DisplayName("Good, bad, good")
  @ValueSource(
      strings =
"""
carrier,shipment_id,event_id,event_type,occurred_at
fedex,1Z999,e1,DELIVERED,2026-08-06T18:00:00Z

fedex,1Z998,e2,DELIVERED,
""")
  void test_7(String csvString) {
    CarrierFileParser carrierFileParser = new CarrierFileParser();
    ParsedResults parsedResults = carrierFileParser.parse(csvString);

    TrackingEvent expectedEvent1 =
        TrackingEvent.of("1Z999", "e1", EventType.DELIVERED, Instant.parse("2026-08-06T18:00:00Z"));
    ParsedEvent parsedEvent1 = new ParsedEvent("fedex", expectedEvent1);

    TrackingEvent expectedEvent2 = TrackingEvent.of("1Z998", "e2", EventType.DELIVERED, null);
    ParsedEvent parsedEvent2 = new ParsedEvent("fedex", expectedEvent2);

    List<ParsedEvent> expectedParsedEvents = List.of(parsedEvent1, parsedEvent2);
    assertEquals(new ParsedResults(expectedParsedEvents, List.of()), parsedResults);
  }

  @ParameterizedTest
  @DisplayName("EventType value not trimmed")
  @ValueSource(
      strings =
          """
      carrier,shipment_id,event_id,event_type,occurred_at
      fedex,1Z999,e1, DELIVERED ,2026-08-06T18:00:00Z
      """)
  void test_8(String csvString) {
    CarrierFileParser carrierFileParser = new CarrierFileParser();
    ParsedResults parsedResults = carrierFileParser.parse(csvString);

    TrackingEvent expectedEvent =
        TrackingEvent.of("1Z999", "e1", EventType.DELIVERED, Instant.parse("2026-08-06T18:00:00Z"));
    ParsedEvent parsedEvent = new ParsedEvent("fedex", expectedEvent);

    List<ParsedEvent> expectedParsedEvents = List.of(parsedEvent);
    assertEquals(new ParsedResults(expectedParsedEvents, List.of()), parsedResults);
  }
}
