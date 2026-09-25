package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

public enum EventType {
  LABEL_CREATED,
  PICKED_UP,
  IN_TRANSIT,
  OUT_FOR_DELIVERY,
  DELIVERED,
  EXCEPTION,
  CANCELLED
}
