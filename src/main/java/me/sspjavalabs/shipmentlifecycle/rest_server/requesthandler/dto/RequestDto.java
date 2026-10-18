package me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto;

import static me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent.tryParseOccurredAt;

import java.time.Instant;
import java.util.Optional;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;

public interface RequestDto {
  record CreateEvent(String eventId, String eventType, String occurredAt) implements RequestDto {
    public TrackingEvent toDomain(String shipmentId) {
      EventType eventType = EventType.parseFromString(this.eventType());
      Optional<Instant> occurredAtOpt = tryParseOccurredAt(this.occurredAt());
      Instant occurredAt = occurredAtOpt.orElse(null);
      return TrackingEvent.of(shipmentId, this.eventId(), eventType, occurredAt);
    }
  }
}
