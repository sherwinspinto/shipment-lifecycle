package me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port;

import java.util.List;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;

public interface LoadPort {
  List<TrackingEvent> load(String carrier, String shipmentId);
}
