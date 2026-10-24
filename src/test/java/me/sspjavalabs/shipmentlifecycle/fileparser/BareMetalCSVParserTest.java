package me.sspjavalabs.shipmentlifecycle.fileparser;

import static me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileParser.*;
import static me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileRowMapper.Row.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileRowMapper.Row.ParsedEvent;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BareMetalCSVParserTest {
  @Test
  @DisplayName("Simple parse success")
  void test_1() throws IOException {
    String csvLine =
        """
          carrier,shipment_id,event_id,event_type,occurred_at
          fedex,1Z999,e1,DELIVERED,2026-08-06T18:00:00Z
         """;
    try (ReadableByteChannel readableByteChannel =
        Channels.newChannel(new ByteArrayInputStream(csvLine.getBytes(StandardCharsets.UTF_8)))) {
      BareMetalCSVParser parser = new BareMetalCSVParser();
      ParsedResults parsedResults = parser.parse(readableByteChannel);
      TrackingEvent expectedTrackingEvent =
          TrackingEvent.of(
              "1Z999", "e1", EventType.DELIVERED, Instant.parse("2026-08-06T18:00:00Z"));
      ParsedResults parsedResultsExpected =
          new ParsedResults(List.of(new ParsedEvent("fedex", expectedTrackingEvent)), List.of());
      assertEquals(parsedResultsExpected, parsedResults);
    }
  }

  @Test
  @DisplayName("CSV with comma in quotes")
  void test_2() throws IOException {
    String csvLine =
        """
          carrier,shipment_id,event_id,event_type,occurred_at
          fedex,1Z999,"e1,extra",DELIVERED,2026-08-06T18:00:00Z
         """;
    try (ReadableByteChannel readableByteChannel =
        Channels.newChannel(new ByteArrayInputStream(csvLine.getBytes(StandardCharsets.UTF_8)))) {
      BareMetalCSVParser parser = new BareMetalCSVParser();
      ParsedResults parsedResults = parser.parse(readableByteChannel);
      TrackingEvent expectedTrackingEvent =
          TrackingEvent.of(
              "1Z999", "e1,extra", EventType.DELIVERED, Instant.parse("2026-08-06T18:00:00Z"));
      ParsedResults parsedResultsExpected =
          new ParsedResults(List.of(new ParsedEvent("fedex", expectedTrackingEvent)), List.of());
      assertEquals(parsedResultsExpected, parsedResults);
    }
  }

  @Test
  @DisplayName("newline in quoted field event_id")
  void test_3() throws IOException {
    String csvLine =
        "carrier,shipment_id,event_id,event_type,occurred_at\n"
            + "fedex,1Z999,\"e1\nextra\",DELIVERED,2026-08-06T18:00:00Z";
    try (ReadableByteChannel readableByteChannel =
        Channels.newChannel(new ByteArrayInputStream(csvLine.getBytes(StandardCharsets.UTF_8)))) {
      BareMetalCSVParser parser = new BareMetalCSVParser();
      ParsedResults parsedResults = parser.parse(readableByteChannel);
      TrackingEvent expectedTrackingEvent =
          TrackingEvent.of(
              "1Z999", "e1\nextra", EventType.DELIVERED, Instant.parse("2026-08-06T18:00:00Z"));
      ParsedResults parsedResultsExpected =
          new ParsedResults(List.of(new ParsedEvent("fedex", expectedTrackingEvent)), List.of());
      assertEquals(parsedResultsExpected, parsedResults);
    }
  }

  @Test
  @DisplayName("field containing escaped quotes")
  void test_4() throws IOException {
    String csvLine =
        "carrier,shipment_id,event_id,event_type,occurred_at\n"
            + "fedex,1Z999,\"e1\"\"extra\",DELIVERED,2026-08-06T18:00:00Z";
    try (ReadableByteChannel readableByteChannel =
        Channels.newChannel(new ByteArrayInputStream(csvLine.getBytes(StandardCharsets.UTF_8)))) {
      BareMetalCSVParser parser = new BareMetalCSVParser();
      ParsedResults parsedResults = parser.parse(readableByteChannel);
      TrackingEvent expectedTrackingEvent =
          TrackingEvent.of(
              "1Z999", "e1\"extra", EventType.DELIVERED, Instant.parse("2026-08-06T18:00:00Z"));
      ParsedResults parsedResultsExpected =
          new ParsedResults(List.of(new ParsedEvent("fedex", expectedTrackingEvent)), List.of());
      assertEquals(parsedResultsExpected, parsedResults);
    }
  }

  @Test
  @DisplayName("Refill buffer")
  void test_5() throws IOException {
    String csvLine =
        "carrier,shipment_id,event_id,event_type,occurred_at\n"
            + "fedex,1Z999,\"e1\"\"extra\",DELIVERED,2026-08-06T18:00:00Z";
    try (ReadableByteChannel readableByteChannel =
        Channels.newChannel(new ByteArrayInputStream(csvLine.getBytes(StandardCharsets.UTF_8)))) {
      BareMetalCSVParser parser = new BareMetalCSVParser(4);
      ParsedResults parsedResults = parser.parse(readableByteChannel);
      TrackingEvent expectedTrackingEvent =
          TrackingEvent.of(
              "1Z999", "e1\"extra", EventType.DELIVERED, Instant.parse("2026-08-06T18:00:00Z"));
      ParsedResults parsedResultsExpected =
          new ParsedResults(List.of(new ParsedEvent("fedex", expectedTrackingEvent)), List.of());
      assertEquals(parsedResultsExpected, parsedResults);
    }
  }

  @Test
  @DisplayName("Bad type, followed by good row")
  void test_6() throws IOException {
    String csvLine =
        "carrier,shipment_id,event_id,event_type,occurred_at\n"
            + "fedex,1Z999,e1,BAD_TYPE,2026-08-06T18:00:00Z\n"
            + "fedex,1Z999,e2,DELIVERED,2026-08-06T18:00:00Z";
    try (ReadableByteChannel readableByteChannel =
        Channels.newChannel(new ByteArrayInputStream(csvLine.getBytes(StandardCharsets.UTF_8)))) {
      BareMetalCSVParser parser = new BareMetalCSVParser(4);
      ParsedResults parsedResults = parser.parse(readableByteChannel);
      TrackingEvent expectedTrackingEvent =
          TrackingEvent.of(
              "1Z999", "e2", EventType.DELIVERED, Instant.parse("2026-08-06T18:00:00Z"));
      BadRow badRow =
          new BadRow(
              2, "fedex,1Z999,e1,BAD_TYPE,2026-08-06T18:00:00Z", "Invalid eventType: BAD_TYPE");
      ParsedResults parsedResultsExpected =
          new ParsedResults(
              List.of(new ParsedEvent("fedex", expectedTrackingEvent)), List.of(badRow));
      assertEquals(parsedResultsExpected, parsedResults);
    }
  }

  @Test
  @DisplayName("Empty occurredAt, results in occurredAt=null")
  void test_7() throws IOException {
    String csvLine =
        "carrier,shipment_id,event_id,event_type,occurred_at\n" + "fedex,1Z999,e1,DELIVERED,";
    try (ReadableByteChannel readableByteChannel =
        Channels.newChannel(new ByteArrayInputStream(csvLine.getBytes(StandardCharsets.UTF_8)))) {
      BareMetalCSVParser parser = new BareMetalCSVParser(4);
      ParsedResults parsedResults = parser.parse(readableByteChannel);
      TrackingEvent expectedTrackingEvent =
          TrackingEvent.of("1Z999", "e1", EventType.DELIVERED, null);
      ParsedResults parsedResultsExpected =
          new ParsedResults(List.of(new ParsedEvent("fedex", expectedTrackingEvent)), List.of());
      assertEquals(parsedResultsExpected, parsedResults);
    }
  }

  @Test
  @DisplayName("Untrimmed fields")
  void test_8() throws IOException {
    String csvLine =
        "carrier,shipment_id,event_id,event_type,occurred_at\n" + " fedex , 1Z999, e1 ,DELIVERED,";
    try (ReadableByteChannel readableByteChannel =
        Channels.newChannel(new ByteArrayInputStream(csvLine.getBytes(StandardCharsets.UTF_8)))) {
      BareMetalCSVParser parser = new BareMetalCSVParser(4);
      ParsedResults parsedResults = parser.parse(readableByteChannel);
      TrackingEvent expectedTrackingEvent =
          TrackingEvent.of("1Z999", "e1", EventType.DELIVERED, null);
      ParsedResults parsedResultsExpected =
          new ParsedResults(List.of(new ParsedEvent("fedex", expectedTrackingEvent)), List.of());
      assertEquals(parsedResultsExpected, parsedResults);
    }
  }
}
