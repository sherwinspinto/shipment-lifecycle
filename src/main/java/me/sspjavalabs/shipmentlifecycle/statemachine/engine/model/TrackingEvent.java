package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

public record TrackingEvent(String shipmentId, String eventId, EventType eventType) {}
