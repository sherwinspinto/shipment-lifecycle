package me.sspjavalabs.shipmentlifecycle.fileparser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;

public class CarrierFileParser {
  static final String NEW_LINE = "\\R";
  static final int CARRIER_COL_INDX = 0;
  static final int SHIPMENT_ID_COL_INDX = 1;
  static final int EVENT_ID_COL_INDX = 2;
  static final int EVENT_TYPE_ID_COL_INDX = 3;
  static final int OCCURRED_AT_COL_INDX = 4;

  static final int FILE_COLUMN_COUNT = 5;

  public record ParsedEvent(String carrier, TrackingEvent trackingEvent) {}

  public record BadRow(int lineNumber, String rawLine, String reason) {}

  public record ParsedResults(List<ParsedEvent> trackingEvents, List<BadRow> badRows) {}

  public ParsedResults parse(String csvFileString) {
    String[] lines = csvFileString.split(NEW_LINE);
    if (lines.length <= 1) return new ParsedResults(List.of(), List.of());
    List<ParsedEvent> parsedEvents = new ArrayList<>();
    List<BadRow> badRows = new ArrayList<>();
    for (int i = 1; i < lines.length; i++) {
      if (lines[i].isBlank()) continue;

      String[] values = lines[i].split(",", -1);

      if (FILE_COLUMN_COUNT != values.length) {
        badRows.add(new BadRow(i + 1, lines[i], "Short line"));
        continue;
      }

      String carrier = values[CARRIER_COL_INDX].trim();
      if (carrier.isBlank()) {
        badRows.add(new BadRow(i + 1, lines[i], "carrier is absent"));
        continue;
      }

      String shipmentId = values[SHIPMENT_ID_COL_INDX].trim();
      if (shipmentId.isBlank()) {
        badRows.add(new BadRow(i + 1, lines[i], "shipmentId is absent"));
        continue;
      }

      String eventId = values[EVENT_ID_COL_INDX].trim();
      if (eventId.isBlank()) {
        badRows.add(new BadRow(i + 1, lines[i], "eventId is absent"));
        continue;
      }

      EventType eventType = EventType.parseFromString(values[EVENT_TYPE_ID_COL_INDX].trim());
      if (eventType == null) {
        badRows.add(
            new BadRow(i + 1, lines[i], "Invalid eventType: " + values[EVENT_TYPE_ID_COL_INDX]));
        continue;
      }

      String occurredAtString = values[OCCURRED_AT_COL_INDX].trim();
      Instant occurredAt;
      if (occurredAtString.isBlank()) occurredAt = null;
      else {
        Optional<Instant> occurredAtOpt = TrackingEvent.tryParseOccurredAt(occurredAtString);
        occurredAt = occurredAtOpt.orElse(null);
        if (occurredAt == null) {
          badRows.add(new BadRow(i + 1, lines[i], "Unparseable occurredAt"));
          continue;
        }
      }

      parsedEvents.add(
          new ParsedEvent(carrier, TrackingEvent.of(shipmentId, eventId, eventType, occurredAt)));
    }
    return new ParsedResults(parsedEvents, badRows);
  }
}
