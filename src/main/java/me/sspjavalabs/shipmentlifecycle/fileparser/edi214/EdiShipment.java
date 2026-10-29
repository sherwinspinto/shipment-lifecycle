package me.sspjavalabs.shipmentlifecycle.fileparser.edi214;

import static me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser.*;
import static me.sspjavalabs.shipmentlifecycle.fileparser.edi214.EdiShipment.ShipmentResult.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EdiShipment {
  public ShipmentResult getShipment(List<Segment> shipmentSegments) {
    Segment b10 = null;
    boolean inLX = false;
    List<StatusLoop> statusLoops = new ArrayList<>();
    List<Segment> others = new ArrayList<>();
    List<Segment> statusLoop = null;
    for (Segment segment : shipmentSegments) {
      switch (SegmentType.getSegmentType(segment.tag())) {
        case B10 -> {
          if (b10 == null)
            b10 = segment;
          else others.add(segment);
        }
        case LX -> {
          inLX = true;
          statusLoop = new ArrayList<>();
          statusLoops.add(new StatusLoop(statusLoop));
          statusLoop.add(segment);
        }
        case AT7 -> {
          if (inLX) {
            statusLoop.add(segment);
          } else {
            others.add(segment);
          }
        }
        case OTHER -> {
          others.add(segment);
        }
      }
    }

    if (b10 == null) return new Bad("Missing B10");
    return new Shipment(b10, statusLoops, others);
  }

  Optional<Segment> getB10(List<Segment> segments) {
    for (Segment segment : segments) {
      if (segment.tag().equals("B10")) return Optional.of(segment);
    }
    return Optional.empty();
  }

  static class StatusLoopsAccumulator {
  }

  enum SegmentType {
    B10,
    LX,
    AT7,
    OTHER;

    static final Map<String, SegmentType> TAG_TO_SEG_TYPE =
        Map.of(
            "B10", B10,
            "LX", LX,
            "AT7", AT7);

    static SegmentType getSegmentType(String tag) {
      return TAG_TO_SEG_TYPE.getOrDefault(tag, OTHER);
    }
  }

  record StatusLoop(List<Segment> loop) {}

  public sealed interface ShipmentResult {

    record Shipment(Segment b10, List<StatusLoop> loops, List<Segment> unknown)
        implements ShipmentResult {}

    record Bad(String reason) implements ShipmentResult {}
  }
}
