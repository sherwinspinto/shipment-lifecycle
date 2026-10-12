package me.sspjavalabs.shipmentlifecycle.db.adapter;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import me.sspjavalabs.shipmentlifecycle.db.infra.BasicPgDataSource;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendPort;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendResult;

public class AppendPortAdaptor implements AppendPort {
  private final BasicPgDataSource basicPgDataSource;
  private static final String ADD_EVENT_SQL =
"""
    INSERT INTO shipment_events (
      carrier,
      shipment_id,
      event_id,
      event_type,
      occurred_at
    ) VALUES (?,?,?,?,?)
    ON CONFLICT ON CONSTRAINT shipment_event_id_key
    DO NOTHING
""";

  public AppendPortAdaptor(BasicPgDataSource basicPgDataSource) {
    this.basicPgDataSource = basicPgDataSource;
  }

  @Override
  public AppendResult append(String carrier, TrackingEvent trackingEvent) {
    try {
      long id =
          basicPgDataSource.executeInsertWithGeneratedKey(
              ADD_EVENT_SQL, trackingEventToParamList(carrier, trackingEvent));
      return id == -1
          ? AppendResult.ofDuplicate(trackingEvent.eventId())
          : AppendResult.ofInserted(trackingEvent.eventId());
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  List<Object> trackingEventToParamList(String carrier, TrackingEvent te) {
    return List.of(
        carrier,
        te.shipmentId(),
        te.eventId(),
        te.eventType().name(),
        te.occurredAt() == null ? Optional.empty() : Optional.of(Timestamp.from(te.occurredAt())));
  }
}
