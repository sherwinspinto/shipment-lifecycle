package me.sspjavalabs.shipmentlifecycle.statemachine.engine;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.*;

/**
 * policy-forward: Declared Applied edges may jump ahead, e.g. Created -> Delivered. No other
 * application of an event can move the state backwards policy-terminal: Delivered -> Duplicate
 * delivered Ignored, Cancelled -> Duplicate Ignored, for both delivered/cancelled all other scans
 * are rejected table-shape: The state transitions are stored as a map, whose value is a specific
 * type, from Applied(state), Ignored(), Rejected(reason)
 */
public class ShipmentLifecycle {

  public AppliedResult apply(ShipmentState currentShipmentState, TrackingEvent trackingEvent) {
    TransitionEdge transitionEdge =
        new TransitionEdge(currentShipmentState, trackingEvent.eventType());
    AppliedResult appliedResult = TransitionTables.TRANSITION_TABLE.get(transitionEdge);
    if (appliedResult != null) return appliedResult;
    if (currentShipmentState == ShipmentState.DELIVERED)
      return new AppliedResult.Rejected("after_delivered");
    if (currentShipmentState == ShipmentState.CANCELLED)
      return new AppliedResult.Rejected("cancelled");

    return new AppliedResult.Rejected("no transition");
  }

  public FoldResult fold(List<TrackingEvent> trackingEvents) {
    if (trackingEvents == null || trackingEvents.isEmpty())
      return new FoldResult(ShipmentState.CREATED, Collections.emptyList(), Optional.empty());

    ShipmentState currentShipmentState = ShipmentState.CREATED;
    List<FoldEntry> appliedResults = new ArrayList<>();

    for (TrackingEvent trackingEvent : trackingEvents) {
      if (trackingEvent.occurredAt() == null)
        return new FoldResult(
            currentShipmentState,
            appliedResults,
            Optional.of(FoldEntry.of(new AppliedResult.Rejected("missing_time"), trackingEvent)));

      AppliedResult appliedResult = apply(currentShipmentState, trackingEvent);
      if (appliedResult instanceof AppliedResult.Applied(ShipmentState newShipmentState)) {
        currentShipmentState = newShipmentState;
        appliedResults.add(FoldEntry.of(appliedResult, trackingEvent));
      } else if (appliedResult instanceof AppliedResult.Ignored)
        appliedResults.add(FoldEntry.of(appliedResult, trackingEvent));
      else if (appliedResult instanceof AppliedResult.Rejected rejected) {
        return new FoldResult(
            currentShipmentState,
            appliedResults,
            Optional.of(FoldEntry.of(rejected, trackingEvent)));
      }
    }

    return new FoldResult(currentShipmentState, appliedResults, Optional.empty());
  }

  public Timeline summarize(List<TrackingEvent> trackingEvents) {
    return summarize(fold(trackingEvents));
  }

  public Timeline summarize(FoldResult foldResult) {
    Optional<Instant> firstDeliveredAt = getFirstDeliveredAt(foldResult.appliedResults());

    if (firstDeliveredAt.isPresent()) {
      List<TrackingEvent> afterDelivered =
          getAfterDeliveredScans(
              foldResult.appliedResults(),
              foldResult.rejectedOn().isEmpty()
                  ? null
                  : foldResult.rejectedOn().get().trackingEvent(),
              firstDeliveredAt.get());

      return new Timeline(
          foldResult.finalState(), firstDeliveredAt, afterDelivered, foldResult.rejectedOn());
    }
    return new Timeline(
        foldResult.finalState(),
        Optional.empty(),
        Collections.emptyList(),
        foldResult.rejectedOn());
  }

  private Optional<Instant> getFirstDeliveredAt(List<FoldEntry> foldEntries) {
    return foldEntries.stream()
        .filter(foldEntry -> isDeliveredScan(foldEntry.trackingEvent()))
        .min(Comparator.comparing(foldEntry -> foldEntry.trackingEvent().occurredAt()))
        .map(foldEntry -> foldEntry.trackingEvent().occurredAt());
  }

  private List<TrackingEvent> getAfterDeliveredScans(
      List<FoldEntry> foldEntries,
      TrackingEvent rejectedOnTrackingEvent,
      Instant firstDeliveredAt) {
    List<TrackingEvent> afterDeliveredFromAppliedResults =
        foldEntries.stream()
            .map(FoldEntry::trackingEvent)
            .filter(trackingEvent -> trackingEvent.occurredAt().isAfter(firstDeliveredAt))
            .toList();

    Instant occurredAt =
        rejectedOnTrackingEvent == null ? null : rejectedOnTrackingEvent.occurredAt();

    if (occurredAt != null && occurredAt.isAfter(firstDeliveredAt))
      return Stream.concat(
              afterDeliveredFromAppliedResults.stream(), Stream.of(rejectedOnTrackingEvent))
          .toList();
    return afterDeliveredFromAppliedResults;
  }

  private boolean isDeliveredScan(TrackingEvent trackingEvent) {
    return trackingEvent.eventType() == EventType.DELIVERED;
  }
}
