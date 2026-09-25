package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

import java.util.List;
import java.util.Optional;

public record FoldResult(
    ShipmentState finalState,
    List<AppliedResult> appliedResults,
    Optional<AppliedResult.Rejected> rejectedOn) {}
