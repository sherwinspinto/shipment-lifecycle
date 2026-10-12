package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

public sealed interface AppliedResult {
  record Ignored() implements AppliedResult {}

  record Rejected(String reason) implements AppliedResult {
    public static String REASON_MISSING_TIME = "missing_time";
    public static String REASON_CANCELLED = "cancelled";
    public static String REASON_NO_TRANSITION = "no transition";
    public static String REASON_AFTER_DELIVERED = "after_delivered";
  }

  record Applied(ShipmentState newShipmentState) implements AppliedResult {}
}
