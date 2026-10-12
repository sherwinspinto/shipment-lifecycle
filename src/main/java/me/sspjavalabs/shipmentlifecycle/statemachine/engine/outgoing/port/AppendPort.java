package me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port;

import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;

public interface AppendPort {
  AppendResult append(String carrier, TrackingEvent trackingEvent);
}
