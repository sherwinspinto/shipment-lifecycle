package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

import java.time.Instant;

public record TrackingEvent(
    String shipmentId, String eventId, EventType eventType, Instant occurredAt) {}
