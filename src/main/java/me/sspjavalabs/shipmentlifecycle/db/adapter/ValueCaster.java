package me.sspjavalabs.shipmentlifecycle.db.adapter;

import java.sql.Timestamp;
import java.time.Instant;

public class ValueCaster {
  static String getAsString(Object stringVal) {
    return stringVal == null ? null : (String) stringVal;
  }

  static Instant getAsInstant(Object sqlTimestamp) {
    return sqlTimestamp == null ? null : ((Timestamp) sqlTimestamp).toInstant();
  }

  public static <E extends Enum<E>> E getAsEnum(Object stringEnumVal, Class<E> enumClass) {
    if (stringEnumVal == null) return null;
    return Enum.valueOf(enumClass, (String) stringEnumVal);
  }
}
