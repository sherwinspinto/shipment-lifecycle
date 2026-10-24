package me.sspjavalabs.shipmentlifecycle.fileparser;

import static me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileRowMapper.*;
import static me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileRowMapper.RawLineHolder.*;
import static me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileRowMapper.Row.*;

import java.util.ArrayList;
import java.util.List;

public class CarrierFileParser {
  static final String NEW_LINE = "\\R";
  static final int CARRIER_COL_INDX = 0;
  static final int SHIPMENT_ID_COL_INDX = 1;
  static final int EVENT_ID_COL_INDX = 2;
  static final int EVENT_TYPE_ID_COL_INDX = 3;
  static final int OCCURRED_AT_COL_INDX = 4;

  static final int FILE_COLUMN_COUNT = 5;

  public record ParsedResults(List<ParsedEvent> trackingEvents, List<BadRow> badRows) {}

  public static ParsedResults parse(String csvFileString) {
    String[] lines = csvFileString.split(NEW_LINE);
    if (lines.length <= 1) return new ParsedResults(List.of(), List.of());
    List<ParsedEvent> parsedEvents = new ArrayList<>();
    List<BadRow> badRows = new ArrayList<>();
    for (int i = 1; i < lines.length; i++) {
      if (lines[i].isBlank()) continue;

      String[] values = lines[i].split(",", -1);
      Row row = toRow(values, i + 1, new StringHolder(lines[i]));

      switch (row) {
        case ParsedEvent parsedEvent -> parsedEvents.add(parsedEvent);
        case BadRow badRow -> badRows.add(badRow);
      }
    }
    return new ParsedResults(parsedEvents, badRows);
  }
}
