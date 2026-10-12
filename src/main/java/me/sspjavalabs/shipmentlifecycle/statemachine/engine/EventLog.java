package me.sspjavalabs.shipmentlifecycle.statemachine.engine;

import java.util.List;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.Timeline;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.LoadPort;

public class EventLog {
  private final LoadPort loadPort;
  private final ShipmentLifecycle shipmentLifecycle;

  public EventLog(LoadPort loadPort, ShipmentLifecycle shipmentLifecycle) {
    this.loadPort = loadPort;
    this.shipmentLifecycle = shipmentLifecycle;
  }

  public Timeline rebuild(String carrier, String shipmentId) {
    List<TrackingEvent> trackingEvents = loadPort.load(carrier, shipmentId);
    return shipmentLifecycle.summarize(trackingEvents);
  }
}
