package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public record TrackingEvent(
    String shipmentId, String eventId, EventType eventType, Instant occurredAt) {
  public static TrackingEvent of(
      String shipmentId, String eventId, EventType eventType, Instant occurredAt) {
    return new TrackingEvent(shipmentId, eventId, eventType, occurredAt);
  }

  public static Optional<Instant> tryParseOccurredAt(String occurredAtString) {
    try {
      return occurredAtString == null
          ? Optional.empty()
          : Optional.of(Instant.parse(occurredAtString));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }
}
