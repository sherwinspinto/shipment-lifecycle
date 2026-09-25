package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

import java.util.Map;

public class TransitionTables {
  public static final Map<TransitionEdge, AppliedResult> TRANSITION_TABLE =
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
              new TransitionEdge(
                  ShipmentState.CREATED, EventType.OUT_FOR_DELIVERY),
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
              new TransitionEdge(
                  ShipmentState.IN_TRANSIT, EventType.OUT_FOR_DELIVERY),
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
              new TransitionEdge(
                  ShipmentState.OUT_FOR_DELIVERY, EventType.OUT_FOR_DELIVERY),
              new AppliedResult.Ignored()),
          Map.entry(
              new TransitionEdge(
                  ShipmentState.OUT_FOR_DELIVERY, EventType.IN_TRANSIT),
              new AppliedResult.Ignored()),
          Map.entry(
              new TransitionEdge(
                  ShipmentState.OUT_FOR_DELIVERY, EventType.DELIVERED),
              new AppliedResult.Applied(ShipmentState.DELIVERED)),
          Map.entry(
              new TransitionEdge(
                  ShipmentState.OUT_FOR_DELIVERY, EventType.EXCEPTION),
              new AppliedResult.Applied(ShipmentState.EXCEPTION)),
          Map.entry(
              new TransitionEdge(
                  ShipmentState.OUT_FOR_DELIVERY, EventType.CANCELLED),
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
              new TransitionEdge(
                  ShipmentState.EXCEPTION, EventType.OUT_FOR_DELIVERY),
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
