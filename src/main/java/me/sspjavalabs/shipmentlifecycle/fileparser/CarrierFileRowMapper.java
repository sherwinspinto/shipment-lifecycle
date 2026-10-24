package me.sspjavalabs.shipmentlifecycle.fileparser;

import java.time.Instant;
import java.util.Optional;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;

public class CarrierFileRowMapper {
  static Row toRow(String[] values, int lineNumber, RawLineHolder holder) {
    if (CarrierFileParser.FILE_COLUMN_COUNT != values.length) {
      return new Row.BadRow(lineNumber, holder.getLine(), "Short line");
    }

    String carrier = values[CarrierFileParser.CARRIER_COL_INDX].trim();
    if (carrier.isBlank()) {
      return new Row.BadRow(lineNumber, holder.getLine(), "carrier is absent");
    }

    String shipmentId = values[CarrierFileParser.SHIPMENT_ID_COL_INDX].trim();
    if (shipmentId.isBlank()) {
      return new Row.BadRow(lineNumber, holder.getLine(), "shipmentId is absent");
    }

    String eventId = values[CarrierFileParser.EVENT_ID_COL_INDX].trim();
    if (eventId.isBlank()) {
      return new Row.BadRow(lineNumber, holder.getLine(), "eventId is absent");
    }

    EventType eventType =
        EventType.parseFromString(values[CarrierFileParser.EVENT_TYPE_ID_COL_INDX].trim());
    if (eventType == null) {
      return new Row.BadRow(
          lineNumber,
          holder.getLine(),
          "Invalid eventType: " + values[CarrierFileParser.EVENT_TYPE_ID_COL_INDX]);
    }

    String occurredAtString = values[CarrierFileParser.OCCURRED_AT_COL_INDX].trim();
    Instant occurredAt;
    if (occurredAtString.isBlank()) occurredAt = null;
    else {
      Optional<Instant> occurredAtOpt = TrackingEvent.tryParseOccurredAt(occurredAtString);
      occurredAt = occurredAtOpt.orElse(null);
      if (occurredAt == null) {
        return new Row.BadRow(lineNumber, holder.getLine(), "Unparseable occurredAt");
      }
    }

    return new Row.ParsedEvent(
        carrier, TrackingEvent.of(shipmentId, eventId, eventType, occurredAt));
  }

  public sealed interface Row {

    record ParsedEvent(String carrier, TrackingEvent trackingEvent) implements Row {}

    record BadRow(int lineNumber, String rawLine, String reason) implements Row {}
  }

  public sealed interface RawLineHolder {
    String getLine();

    record StringHolder(String rawLine) implements RawLineHolder {

      @Override
      public String getLine() {
        return rawLine;
      }
    }

    final class ByteHolder implements RawLineHolder {
      private byte[] rawLineBytes;
      private int start;
      private int end;

      public ByteHolder(byte[] rawLineBytes, int start, int end) {
        this.rawLineBytes = rawLineBytes;
        this.start = start;
        this.end = end;
      }

      public ByteHolder() {
        rawLineBytes = null;
        start = 0;
        end = 0;
      }

      public ByteHolder setRawLineBytes(byte[] rawLineBytes) {
        this.rawLineBytes = rawLineBytes;
        return this;
      }

      public ByteHolder setStart(int start) {
        this.start = start;
        return this;
      }

      public ByteHolder setEnd(int end) {
        this.end = end;
        return this;
      }

      @Override
      public String getLine() {
        return new String(rawLineBytes, start, end);
      }
    }
  }
}
