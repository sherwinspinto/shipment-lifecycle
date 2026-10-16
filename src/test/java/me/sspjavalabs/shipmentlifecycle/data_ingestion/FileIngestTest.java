package me.sspjavalabs.shipmentlifecycle.data_ingestion;

import static me.sspjavalabs.shipmentlifecycle.data_ingestion.FileIngest.*;
import static me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileParser.*;
import static me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendResult.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileParser;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendPort;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.LoadPort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileIngestTest {
  private static FileIngest fileIngest;
  private static final LoadPort loadPort = getLoadPort();
  private static final Map<CarrierShipmentKey, Map<String, TrackingEvent>> db =
      new ConcurrentHashMap<>();

  record CarrierShipmentKey(String carrier, String shipmentId) {}
  ;

  static AppendPort getDuplicateCatchingLoadPort() {
    return (String carrier, TrackingEvent trackingEvent) -> {
      CarrierShipmentKey carrierShipment =
          new CarrierShipmentKey(carrier, trackingEvent.shipmentId());
      String eventId = trackingEvent.eventId();
      if (db.containsKey(carrierShipment) && db.get(carrierShipment).containsKey(eventId))
        return new Duplicate(trackingEvent.eventId());
      if (!dbAppend(db, trackingEvent, carrier)) throw new RuntimeException("Issue with db");
      return new Inserted(trackingEvent.eventId());
    };
  }

  static LoadPort getLoadPort() {
    return (carrier, shipmentId) -> {
      CarrierShipmentKey carrierShipmentKey = new CarrierShipmentKey(carrier, shipmentId);
      if (db.containsKey(carrierShipmentKey))
        return db.get(carrierShipmentKey).values().stream().toList();
      return List.of();
    };
  }

  private static boolean dbAppend(
      Map<CarrierShipmentKey, Map<String, TrackingEvent>> db,
      TrackingEvent trackingEvent,
      String carrier) {
    CarrierShipmentKey carrierShipmentKey =
        new CarrierShipmentKey(carrier, trackingEvent.shipmentId());
    String eventId = trackingEvent.eventId();
    Map<String, TrackingEvent> idToEventMap =
        db.getOrDefault(carrierShipmentKey, new ConcurrentHashMap<>());
    if (idToEventMap.containsKey(eventId)) return false;
    idToEventMap.put(eventId, trackingEvent);
    db.put(carrierShipmentKey, idToEventMap);
    return true;
  }

  @BeforeAll
  static void setUp() {
    AppendPort appendPort = getDuplicateCatchingLoadPort();
    fileIngest = new FileIngest(new CarrierFileParser(), appendPort);
  }

  @ParameterizedTest
  @DisplayName("One event")
  @ValueSource(
      strings =
"""
carrier,shipment_id,event_id,event_type,occurred_at
fedex,fis1,fie1,DELIVERED,2026-08-06T18:00:00Z
""")
  void test_1(String fileLines) {
    String expectedCarrier = "fedex";
    String expectedShipmentId = "fis1";
    String expectedEventId = "fie1";
    IngestReport actualIngestReport = fileIngest.ingest(fileLines);
    Appended expectedAppended =
        new Appended(
            expectedCarrier, expectedShipmentId, expectedEventId, new Inserted(expectedEventId));
    IngestReport expectedIngestReport = new IngestReport(List.of(expectedAppended), List.of());
    assertEquals(expectedIngestReport, actualIngestReport);
  }

  @ParameterizedTest
  @DisplayName("Bad Row, no append")
  @ValueSource(
      strings =
          """
      carrier,shipment_id,event_id,event_type,occurred_at
      fedex,fis2,fie1,DELIVERED,2026-08-06 18:00:00
      """)
  void test_2(String fileLines) {
    IngestReport actualIngestReport = fileIngest.ingest(fileLines);
    BadRow expectedBadRow =
        new BadRow(2, "fedex,fis2,fie1,DELIVERED,2026-08-06 18:00:00", "Unparseable occurredAt");
    IngestReport expectedIngestReport = new IngestReport(List.of(), List.of(expectedBadRow));
    assertEquals(expectedIngestReport, actualIngestReport);
  }

  @ParameterizedTest
  @DisplayName("Second row duplicate")
  @ValueSource(
      strings =
          """
      carrier,shipment_id,event_id,event_type,occurred_at
      fedex,fis3,fie1,DELIVERED,2026-08-06T18:00:00Z
      fedex,fis3,fie1,DELIVERED,2026-08-06T18:00:00Z
      """)
  void test_3(String fileLines) {
    String expectedCarrier = "fedex";
    String expectedShipmentId = "fis3";
    String expectedEventId = "fie1";
    IngestReport actualIngestReport = fileIngest.ingest(fileLines);
    Appended expectedAppendedInserted =
        new Appended(
            expectedCarrier, expectedShipmentId, expectedEventId, new Inserted(expectedEventId));
    Appended expectedAppendedDuplicate =
        new Appended(
            expectedCarrier, expectedShipmentId, expectedEventId, new Duplicate(expectedEventId));
    IngestReport expectedIngestReport =
        new IngestReport(List.of(expectedAppendedInserted, expectedAppendedDuplicate), List.of());
    assertEquals(expectedIngestReport, actualIngestReport);
  }

  @ParameterizedTest
  @DisplayName("Second row duplicate")
  @ValueSource(
      strings =
          """
      carrier,shipment_id,event_id,event_type,occurred_at
      fedex,fis4,fie1,IN_TRANSIT,2026-08-06T18:00:00Z
      fedex,fis4,fie2,BAD,2026-08-06T18:30:00Z
      fedex,fis4,fie3,DELIVERED,2026-08-06T19:00:00Z
      """)
  void test_4(String fileLines) {
    String expectedCarrier = "fedex";
    String expectedShipmentId = "fis4";
    IngestReport actualIngestReport = fileIngest.ingest(fileLines);
    Appended expectedAppendedInserted1 =
        new Appended(expectedCarrier, expectedShipmentId, "fie1", new Inserted("fie1"));
    Appended expectedAppendedInserted2 =
        new Appended(expectedCarrier, expectedShipmentId, "fie3", new Inserted("fie3"));

    BadRow expectedBadRow =
        new BadRow(3, "fedex,fis4,fie2,BAD,2026-08-06T18:30:00Z", "Invalid eventType: BAD");
    IngestReport expectedIngestReport =
        new IngestReport(
            List.of(expectedAppendedInserted1, expectedAppendedInserted2), List.of(expectedBadRow));
    assertEquals(expectedIngestReport, actualIngestReport);
  }

  @ParameterizedTest
  @DisplayName("Empty occurredAt, record appended in db")
  @ValueSource(
      strings = {
"""
carrier,shipment_id,event_id,event_type,occurred_at
fedex,fis5,fie1,IN_TRANSIT,
"""
      })
  void test_5(String fileLines) {
    String expectedCarrier = "fedex";
    String expectedShipmentId = "fis5";
    IngestReport actualIngestReport = fileIngest.ingest(fileLines);

    Appended expectedAppendedInserted =
        new Appended(expectedCarrier, expectedShipmentId, "fie1", new Inserted("fie1"));

    IngestReport expectedIngestReport =
        new IngestReport(List.of(expectedAppendedInserted), List.of());
    assertEquals(expectedIngestReport, actualIngestReport);
    List<TrackingEvent> trackingEvents = loadPort.load(expectedCarrier, expectedShipmentId);
    assertEquals(1, trackingEvents.size());
  }

  @ParameterizedTest
  @DisplayName("Empty occurredAt, record appended in db")
  @ValueSource(
      strings = {
        """
      carrier,shipment_id,event_id,event_type,occurred_at
      fedex,fis6_fedex,fie1,IN_TRANSIT,2026-08-06T18:00:00Z
      ups,fis6_ups,fie2,DELIVERED,2026-08-06T17:00:00Z
      """
      })
  void test_6(String fileLines) {
    String expectedCarrierFedex = "fedex";
    String expectedCarrierUps = "ups";
    String expectedShipmentIdFedex = "fis6_fedex";
    String expectedShipmentIdUps = "fis6_ups";
    IngestReport actualIngestReport = fileIngest.ingest(fileLines);

    Appended expectedAppendedInserted1 =
        new Appended(expectedCarrierFedex, expectedShipmentIdFedex, "fie1", new Inserted("fie1"));

    Appended expectedAppendedInserted2 =
        new Appended(expectedCarrierUps, expectedShipmentIdUps, "fie2", new Inserted("fie2"));

    IngestReport expectedIngestReport =
        new IngestReport(List.of(expectedAppendedInserted1, expectedAppendedInserted2), List.of());
    assertEquals(expectedIngestReport, actualIngestReport);

    List<TrackingEvent> trackingEventsFedex =
        loadPort.load(expectedCarrierFedex, expectedShipmentIdFedex);
    assertEquals(1, trackingEventsFedex.size());
    assertEquals(expectedShipmentIdFedex, trackingEventsFedex.getFirst().shipmentId());

    List<TrackingEvent> trackingEventsUps =
        loadPort.load(expectedCarrierUps, expectedShipmentIdUps);
    assertEquals(1, trackingEventsUps.size());
    assertEquals(expectedShipmentIdUps, trackingEventsUps.getFirst().shipmentId());
  }

  @ParameterizedTest
  @DisplayName("One event")
  @ValueSource(
      strings =
          """
      carrier,shipment_id,event_id,event_type,occurred_at
      fedex, fis7 , fie1 , DELIVERED , 2026-08-06T18:00:00Z
      """)
  void test_7(String fileLines) {
    String expectedCarrier = "fedex";
    String expectedShipmentId = "fis7";
    String expectedEventId = "fie1";
    IngestReport actualIngestReport = fileIngest.ingest(fileLines);
    Appended expectedAppended =
        new Appended(
            expectedCarrier, expectedShipmentId, expectedEventId, new Inserted(expectedEventId));
    IngestReport expectedIngestReport = new IngestReport(List.of(expectedAppended), List.of());
    assertEquals(expectedIngestReport, actualIngestReport);
  }
}
