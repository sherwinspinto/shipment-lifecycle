package me.sspjavalabs.shipmentlifecycle.statemachine.engine;

import java.util.*;

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
    List<AppliedResult> appliedResults = new ArrayList<>();

    for (TrackingEvent trackingEvent : trackingEvents) {
      AppliedResult appliedResult = apply(currentShipmentState, trackingEvent);
      if (appliedResult instanceof AppliedResult.Applied(ShipmentState newShipmentState)) {
        currentShipmentState = newShipmentState;
        appliedResults.add(appliedResult);
      } else if (appliedResult instanceof AppliedResult.Ignored) appliedResults.add(appliedResult);
      else if (appliedResult instanceof AppliedResult.Rejected rejected) {
        return new FoldResult(currentShipmentState, appliedResults, Optional.of(rejected));
      }
    }

    return new FoldResult(currentShipmentState, appliedResults, Optional.empty());
  }

  public record FoldResult(
      ShipmentState finalState,
      List<AppliedResult> appliedResults,
      Optional<AppliedResult.Rejected> rejectedOn) {}

  public enum EventType {
    LABEL_CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    EXCEPTION,
    CANCELLED
  }

  public enum ShipmentState {
    CREATED,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    EXCEPTION,
    CANCELLED
  }

  public record TrackingEvent(String shipmentId, String eventId, EventType eventType) {}

  public sealed interface AppliedResult {
    record Ignored() implements AppliedResult {}

    record Rejected(String reason) implements AppliedResult {}

    record Applied(ShipmentState newShipmentState) implements AppliedResult {}
  }

  record TransitionEdge(ShipmentState shipmentState, EventType eventType) {}

  public static class TransitionTables {
    static final Map<TransitionEdge, AppliedResult> TRANSITION_TABLE =
        Map.ofEntries(
            Map.entry(
                new TransitionEdge(ShipmentState.CREATED, EventType.LABEL_CREATED),
                new AppliedResult.Ignored()),
            Map.entry(
                new TransitionEdge(ShipmentState.CREATED, EventType.PICKED_UP),
                new AppliedResult.Applied(ShipmentState.IN_TRANSIT)),
            Map.entry(
                new TransitionEdge(ShipmentState.CREATED, EventType.IN_TRANSIT),
                new AppliedResult.Applied(ShipmentState.IN_TRANSIT)),
            Map.entry(
                new TransitionEdge(ShipmentState.CREATED, EventType.OUT_FOR_DELIVERY),
                new AppliedResult.Applied(ShipmentState.OUT_FOR_DELIVERY)),
            Map.entry(
                new TransitionEdge(ShipmentState.CREATED, EventType.DELIVERED),
                new AppliedResult.Applied(ShipmentState.DELIVERED)),
            Map.entry(
                new TransitionEdge(ShipmentState.CREATED, EventType.EXCEPTION),
                new AppliedResult.Applied(ShipmentState.EXCEPTION)),
            Map.entry(
                new TransitionEdge(ShipmentState.CREATED, EventType.CANCELLED),
                new AppliedResult.Applied(ShipmentState.CANCELLED)),
            Map.entry(
                new TransitionEdge(ShipmentState.IN_TRANSIT, EventType.PICKED_UP),
                new AppliedResult.Ignored()),
            Map.entry(
                new TransitionEdge(ShipmentState.IN_TRANSIT, EventType.IN_TRANSIT),
                new AppliedResult.Ignored()),
            Map.entry(
                new TransitionEdge(ShipmentState.IN_TRANSIT, EventType.OUT_FOR_DELIVERY),
                new AppliedResult.Applied(ShipmentState.OUT_FOR_DELIVERY)),
            Map.entry(
                new TransitionEdge(ShipmentState.IN_TRANSIT, EventType.DELIVERED),
                new AppliedResult.Applied(ShipmentState.DELIVERED)),
            Map.entry(
                new TransitionEdge(ShipmentState.IN_TRANSIT, EventType.EXCEPTION),
                new AppliedResult.Applied(ShipmentState.EXCEPTION)),
            Map.entry(
                new TransitionEdge(ShipmentState.IN_TRANSIT, EventType.CANCELLED),
                new AppliedResult.Applied(ShipmentState.CANCELLED)),
            Map.entry(
                new TransitionEdge(ShipmentState.OUT_FOR_DELIVERY, EventType.OUT_FOR_DELIVERY),
                new AppliedResult.Ignored()),
            Map.entry(
                new TransitionEdge(ShipmentState.OUT_FOR_DELIVERY, EventType.IN_TRANSIT),
                new AppliedResult.Ignored()),
            Map.entry(
                new TransitionEdge(ShipmentState.OUT_FOR_DELIVERY, EventType.DELIVERED),
                new AppliedResult.Applied(ShipmentState.DELIVERED)),
            Map.entry(
                new TransitionEdge(ShipmentState.OUT_FOR_DELIVERY, EventType.EXCEPTION),
                new AppliedResult.Applied(ShipmentState.EXCEPTION)),
            Map.entry(
                new TransitionEdge(ShipmentState.OUT_FOR_DELIVERY, EventType.CANCELLED),
                new AppliedResult.Applied(ShipmentState.CANCELLED)),
            Map.entry(
                new TransitionEdge(ShipmentState.DELIVERED, EventType.DELIVERED),
                new AppliedResult.Ignored()),
            Map.entry(
                new TransitionEdge(ShipmentState.EXCEPTION, EventType.EXCEPTION),
                new AppliedResult.Ignored()),
            Map.entry(
                new TransitionEdge(ShipmentState.EXCEPTION, EventType.PICKED_UP),
                new AppliedResult.Applied(ShipmentState.IN_TRANSIT)),
            Map.entry(
                new TransitionEdge(ShipmentState.EXCEPTION, EventType.IN_TRANSIT),
                new AppliedResult.Applied(ShipmentState.IN_TRANSIT)),
            Map.entry(
                new TransitionEdge(ShipmentState.EXCEPTION, EventType.OUT_FOR_DELIVERY),
                new AppliedResult.Applied(ShipmentState.OUT_FOR_DELIVERY)),
            Map.entry(
                new TransitionEdge(ShipmentState.EXCEPTION, EventType.DELIVERED),
                new AppliedResult.Applied(ShipmentState.DELIVERED)),
            Map.entry(
                new TransitionEdge(ShipmentState.EXCEPTION, EventType.CANCELLED),
                new AppliedResult.Applied(ShipmentState.CANCELLED)),
            Map.entry(
                new TransitionEdge(ShipmentState.CANCELLED, EventType.CANCELLED),
                new AppliedResult.Ignored()));
  }
}
