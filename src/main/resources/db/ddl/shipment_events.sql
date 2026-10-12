CREATE TABLE IF NOT EXISTS shipment_events
(
  id          BIGSERIAL primary key,
  carrier     TEXT        NOT NULL,
  event_id    TEXT        NOT NULL,
  shipment_id TEXT        NOT NULL,
  event_type  TEXT        NOT NULL,
  occurred_at TIMESTAMPTZ,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT shipment_event_id_key UNIQUE (carrier, shipment_id, event_id)
);
