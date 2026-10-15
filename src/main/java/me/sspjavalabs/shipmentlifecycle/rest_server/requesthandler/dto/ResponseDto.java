package me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto;

import java.util.List;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.AppliedResult;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.FoldEntry;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.Timeline;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;

public sealed interface ResponseDto {
  record Events(
      String finalState,
      String firstDeliveredAt,
      List<TrackingEventView> afterDelivered,
      RejectedOnView rejectedOn)
      implements ResponseDto {
    public static Events fromDomain(Timeline timeline) {
      String firstDeliveredAt =
          timeline.firstDeliveredAt().isPresent()
              ? timeline.firstDeliveredAt().get().toString()
              : null;
      List<TrackingEventView> afterDelivered =
          timeline.afterDelivered().stream().map(TrackingEventView::fromDomain).toList();
      RejectedOnView rejectedOnView =
          timeline.rejectedOn().isPresent()
              ? RejectedOnView.fromDomain(timeline.rejectedOn().get())
              : null;
      return new Events(
          timeline.finalState().name(), firstDeliveredAt, afterDelivered, rejectedOnView);
    }
  }

  record TrackingEventView(String eventId, String eventType, String occurredAt) {
    static TrackingEventView fromDomain(TrackingEvent trackingEvent) {
      return new TrackingEventView(
          trackingEvent.eventId(),
          trackingEvent.eventType().name(),
          trackingEvent.occurredAt().toString());
    }
  }

  record RejectedOnView(String eventId, String eventType, String occurredAt, String reason) {
    static RejectedOnView fromDomain(FoldEntry foldEntry) {
      TrackingEvent trackingEvent = foldEntry.trackingEvent();
      String occurredAt =
          trackingEvent.occurredAt() == null ? null : trackingEvent.occurredAt().toString();
      return switch (foldEntry.appliedResult()) {
        case AppliedResult.Rejected(String reason) ->
            new RejectedOnView(
                trackingEvent.eventId(), trackingEvent.eventType().name(), occurredAt, reason);
        // TODO: Add better exception message. Discuss best practice
        case AppliedResult.Applied _, AppliedResult.Ignored _ ->
            throw new RuntimeException("SEVERE: Invalid state)");
      };
    }
  }
}
