package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record Timeline(
    ShipmentState finalState,
    Optional<Instant> firstDeliveredAt,
    List<TrackingEvent> afterDelivered,
    Optional<FoldEntry> rejectedOn) {}
