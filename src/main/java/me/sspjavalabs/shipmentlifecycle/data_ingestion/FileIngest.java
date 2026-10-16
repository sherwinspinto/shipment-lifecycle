package me.sspjavalabs.shipmentlifecycle.data_ingestion;

import static me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileParser.*;
import static me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendResult.*;

import java.util.ArrayList;
import java.util.List;
import me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileParser;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendPort;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendResult;

public class FileIngest {
  private final CarrierFileParser parser;
  private final AppendPort appendPort;

  public FileIngest(CarrierFileParser parser, AppendPort appendPort) {
    this.parser = parser;
    this.appendPort = appendPort;
  }

  IngestReport ingest(String fileLines) {
    ParsedResults parsedResults = parser.parse(fileLines);
    List<Appended> appendedList = new ArrayList<>();
    for (ParsedEvent parsedEvent : parsedResults.trackingEvents()) {
      AppendResult appendResult =
          appendPort.append(parsedEvent.carrier(), parsedEvent.trackingEvent());
      String eventId =
          switch (appendResult) {
            case Inserted(String idFromInserted) -> idFromInserted;
            case Duplicate(String idFromDuplicate) -> idFromDuplicate;
          };
      appendedList.add(
          new Appended(
              parsedEvent.carrier(),
              parsedEvent.trackingEvent().shipmentId(),
              eventId,
              appendResult));
    }
    return new IngestReport(appendedList, parsedResults.badRows());
  }

  record IngestReport(List<Appended> appendedList, List<BadRow> badRows) {}

  record Appended(String carrier, String shipmentId, String eventId, AppendResult appendResult) {}
}
