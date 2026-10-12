package me.sspjavalabs.shipmentlifecycle.helper;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.jdbc.JdbcDatabaseDelegate;

public final class GlobalPostgresExtension implements BeforeAllCallback, AutoCloseable {
  private static PostgreSQLContainer<?> postgres;
  private static boolean started = false;

  @Override
  public void beforeAll(@NonNull ExtensionContext context) throws Exception {
    if (!started) {
      started = true;

      postgres = new PostgreSQLContainer<>("postgres:16-alpine");
      postgres.start();

      var databaseDelegate = new JdbcDatabaseDelegate(postgres, "");

      System.out.println("Applying database DDL migrations from production resources...");

      ScriptUtils.runInitScript(databaseDelegate, "db/ddl/shipment_events.sql");
      ScriptUtils.runInitScript(databaseDelegate, "db/ddl/shipment_lifecycle_db.sql");

      // Set system properties so your BasicPgDataSource can read them dynamically
      System.setProperty("DB_URL", postgres.getJdbcUrl());
      System.setProperty("DB_USER", postgres.getUsername());
      System.setProperty("DB_PASS", postgres.getPassword());

      // 💡 Register this class into the root context store.
      // JUnit will automatically call the close() method below when all tests wrap up.
      context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL).put("POSTGRES_CONTAINER", this);
    }
  }

  @Override
  public void close() {
    if (postgres != null && postgres.isRunning()) {
      postgres.stop();
    }
  }
}
