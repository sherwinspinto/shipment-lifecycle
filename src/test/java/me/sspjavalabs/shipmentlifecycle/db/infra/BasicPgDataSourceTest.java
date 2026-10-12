package me.sspjavalabs.shipmentlifecycle.db.infra;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import me.sspjavalabs.shipmentlifecycle.helper.GlobalPostgresExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(GlobalPostgresExtension.class)
class BasicPgDataSourceTest {
  private static BasicPgDataSource dataSource;
  private static Clock clock = Clock.systemUTC();

  @BeforeAll
  public static void initialise() {
    dataSource =
        BasicPgDataSource.of(
            System.getProperty("DB_URL") + "shipment_lifecycle",
            System.getProperty("DB_USER"),
            System.getProperty("DB_PASS"));
  }

  @Test
  public void test_insert_with_generated_key() throws Exception {
    List<Object> paramValue =
        List.of("fedex", "de2", "ds1", "DELIVERED", Timestamp.from(Instant.now(clock)));
    long id =
        dataSource.executeInsertWithGeneratedKey(
            "insert into shipment_events (carrier, event_id, shipment_id, event_type, occurred_at) values (?,?,?,?,?)",
            paramValue);

    List<Object> paramValue1 =
        List.of("fedex", "de10", "ds1", "DELIVERED", Timestamp.from(Instant.now(clock)));
    long id1 =
        dataSource.executeInsertWithGeneratedKey(
            "insert into shipment_events (carrier, event_id, shipment_id, event_type, occurred_at) values (?,?,?,?,?)",
            paramValue1);

    String sql =
        "select * from shipment_events where carrier = ? and shipment_id = ? order by received_at, id";
    List<Map<String, Object>> result = dataSource.executeSelect(sql, List.of("fedex", "ds1"));
    Assertions.assertEquals(2, result.size());
    //    Assertions.assertEquals(paramValue, getResultAsList(result));
  }

  @AfterAll
  public static void close() {
    if (dataSource != null) {
      dataSource.close();
    }
  }

  static List<Object> getResultAsList(List<Map<String, Object>> table) {
    Map<String, Object> row = table.getFirst();
    return List.of(
        row.get("carrier"),
        row.get("event_id"),
        row.get("shipment_id"),
        row.get("event_type"),
        row.get("occurred_at"));
  }

  static void printSelectResult(List<Map<String, Object>> table) {
    table.forEach(
        row -> {
          System.out.println("Row ");
          System.out.println(convertToString(row));
        });
  }

  static String convertToString(Map<String, Object> row) {
    StringBuilder sb = new StringBuilder();
    row.forEach((key, value) -> sb.append(key).append(" -> ").append(value).append("\n"));
    return sb.toString();
  }
}
