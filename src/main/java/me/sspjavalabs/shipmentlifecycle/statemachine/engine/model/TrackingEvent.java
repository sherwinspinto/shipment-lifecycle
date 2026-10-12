package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

import java.time.Instant;

public record TrackingEvent(
    String shipmentId, String eventId, EventType eventType, Instant occurredAt) {
  public static TrackingEvent of(
      String shipmentId, String eventId, EventType eventType, Instant occurredAt) {
    return new TrackingEvent(shipmentId, eventId, eventType, occurredAt);
  }
}
