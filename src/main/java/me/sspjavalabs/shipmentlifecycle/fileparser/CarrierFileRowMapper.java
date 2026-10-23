package me.sspjavalabs.shipmentlifecycle.fileparser;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;

public class CarrierFileRowMapper {
  static Row toRow(String[] values, int lineNumber, Supplier<String> rawLineSupplier) {
    if (CarrierFileParser.FILE_COLUMN_COUNT != values.length) {
      return new Row.BadRow(lineNumber, rawLineSupplier.get(), "Short line");
    }

    String carrier = values[CarrierFileParser.CARRIER_COL_INDX].trim();
    if (carrier.isBlank()) {
      return new Row.BadRow(lineNumber, rawLineSupplier.get(), "carrier is absent");
    }

    String shipmentId = values[CarrierFileParser.SHIPMENT_ID_COL_INDX].trim();
    if (shipmentId.isBlank()) {
      return new Row.BadRow(lineNumber, rawLineSupplier.get(), "shipmentId is absent");
    }

    String eventId = values[CarrierFileParser.EVENT_ID_COL_INDX].trim();
    if (eventId.isBlank()) {
      return new Row.BadRow(lineNumber, rawLineSupplier.get(), "eventId is absent");
    }

    EventType eventType =
        EventType.parseFromString(values[CarrierFileParser.EVENT_TYPE_ID_COL_INDX].trim());
    if (eventType == null) {
      return new Row.BadRow(
          lineNumber,
          rawLineSupplier.get(),
          "Invalid eventType: " + values[CarrierFileParser.EVENT_TYPE_ID_COL_INDX]);
    }

    String occurredAtString = values[CarrierFileParser.OCCURRED_AT_COL_INDX].trim();
    Instant occurredAt;
    if (occurredAtString.isBlank()) occurredAt = null;
    else {
      Optional<Instant> occurredAtOpt = TrackingEvent.tryParseOccurredAt(occurredAtString);
      occurredAt = occurredAtOpt.orElse(null);
      if (occurredAt == null) {
        return new Row.BadRow(lineNumber, rawLineSupplier.get(), "Unparseable occurredAt");
      }
    }

    return new Row.ParsedEvent(
        carrier, TrackingEvent.of(shipmentId, eventId, eventType, occurredAt));
  }

  public sealed interface Row {

    record ParsedEvent(String carrier, TrackingEvent trackingEvent) implements Row {}

    record BadRow(int lineNumber, String rawLine, String reason) implements Row {}
  }
}
