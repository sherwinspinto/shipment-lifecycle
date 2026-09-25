package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

public record TransitionEdge(ShipmentState shipmentState, EventType eventType) {}
