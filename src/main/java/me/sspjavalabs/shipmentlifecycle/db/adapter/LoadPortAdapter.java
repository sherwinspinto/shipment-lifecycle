package me.sspjavalabs.shipmentlifecycle.db.adapter;

import static me.sspjavalabs.shipmentlifecycle.db.adapter.ValueCaster.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import me.sspjavalabs.shipmentlifecycle.db.infra.BasicPgDataSource;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.LoadPort;

public class LoadPortAdapter implements LoadPort {

  private static final String LOAD_EVENTS =
"""
  SELECT event_id, event_type, occurred_at from shipment_events WHERE carrier = ? AND shipment_id = ? order by received_at, id asc
""";

  private final BasicPgDataSource basicPgDataSource;

  public LoadPortAdapter(BasicPgDataSource basicPgDataSource) {
    this.basicPgDataSource = basicPgDataSource;
  }

  @Override
  public List<TrackingEvent> load(String carrier, String shipmentId) {
    try {
      List<Map<String, Object>> events =
          basicPgDataSource.executeSelect(LOAD_EVENTS, List.of(carrier, shipmentId));
      return events.stream().map(row -> dbRowToEvent(shipmentId, row)).toList();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  static TrackingEvent dbRowToEvent(String shipmentId, Map<String, Object> row) {
    var eventId = row.get("event_id");
    var eventType = row.get("event_type");
    var occurredAt = row.get("occurred_at");
    return TrackingEvent.of(
        shipmentId,
        getAsString(eventId),
        getAsEnum(eventType, EventType.class),
        getAsInstant(occurredAt));
  }
}
