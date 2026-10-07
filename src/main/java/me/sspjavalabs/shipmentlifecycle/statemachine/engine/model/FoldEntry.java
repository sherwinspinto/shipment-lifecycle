package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

public record FoldEntry(AppliedResult appliedResult, TrackingEvent trackingEvent) {
  public static FoldEntry of(AppliedResult appliedResult, TrackingEvent trackingEvent) {
    return new FoldEntry(appliedResult, trackingEvent);
  }
}
