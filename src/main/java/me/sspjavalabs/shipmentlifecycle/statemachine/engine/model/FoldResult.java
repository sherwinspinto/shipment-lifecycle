package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

import java.util.List;
import java.util.Optional;

public record FoldResult(
    ShipmentState finalState, List<FoldEntry> appliedResults, Optional<FoldEntry> rejectedOn) {}
