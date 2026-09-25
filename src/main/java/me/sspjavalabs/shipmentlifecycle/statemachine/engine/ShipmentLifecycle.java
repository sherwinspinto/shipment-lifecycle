package me.sspjavalabs.shipmentlifecycle.statemachine.engine;

import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.*;

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

}
