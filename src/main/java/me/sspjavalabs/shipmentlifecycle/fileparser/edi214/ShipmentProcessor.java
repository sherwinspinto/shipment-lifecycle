package me.sspjavalabs.shipmentlifecycle.fileparser.edi214;

import static me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser.*;
import static me.sspjavalabs.shipmentlifecycle.fileparser.edi214.EdiShipment.ShipmentResult.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;

public class ShipmentProcessor {
  static final int TN_INDEX = 1;
  static final int AT7_01 = 0;
  static final int AT7_02 = 1;

  static Map<String, EventType> EXT_CODE_TO_INT_EVENT =
      Map.of(
          "D", EventType.DELIVERED,
          "NS", EventType.IN_TRANSIT,
          "P", EventType.PICKED_UP);

  Result process(Shipment shipment) {
    var trackingNumberOpt = getTrackingNumberFromB10(shipment.b10());
    if (trackingNumberOpt.isEmpty()) return new Result(List.of(), "No tracking number");
    List<EdiShipment.StatusLoop> statusLoops = shipment.loops();
    List<TrackingEvent> trackingEvents = new ArrayList<>();
    for (EdiShipment.StatusLoop statusLoop : statusLoops) {
      for (Segment segment : statusLoop.loop()) {
        if (segment.tag().equals("AT7")) {
          var statusCodeOpt = getStatusCode(segment);
          if (statusCodeOpt.isPresent()) {
            EventType eventType = EXT_CODE_TO_INT_EVENT.get(statusCodeOpt.get());
            if (eventType != null) {
              trackingEvents.add(TrackingEvent.of(trackingNumberOpt.get(), "1", eventType, null));
            }
          }
        }
      }
    }
    return new Result(trackingEvents, null);
  }

  Optional<String> getStatusCode(Segment at7) {
    return getElementFromSegment(at7, AT7_01);
  }

  Optional<String> getTrackingNumberFromB10(Segment b10) {
    return getElementFromSegment(b10, TN_INDEX);
  }

  Optional<String> getElementFromSegment(Segment segment, int index) {
    if (segment.elements().size() > index) return Optional.of(segment.elements().get(index));
    return Optional.empty();
  }

  record Result(List<TrackingEvent> trackingEvents, String failure) {}
}
