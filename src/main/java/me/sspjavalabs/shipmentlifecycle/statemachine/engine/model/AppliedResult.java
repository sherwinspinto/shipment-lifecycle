package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

public sealed interface AppliedResult {
  record Ignored() implements AppliedResult {}

  record Rejected(String reason) implements AppliedResult {}

  record Applied(ShipmentState newShipmentState) implements AppliedResult {}
}
