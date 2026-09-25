package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

public enum ShipmentState {
  CREATED,
  IN_TRANSIT,
  OUT_FOR_DELIVERY,
  DELIVERED,
  EXCEPTION,
  CANCELLED
}
