package me.sspjavalabs.shipmentlifecycle.launcher;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.util.Map;
import me.sspjavalabs.shipmentlifecycle.authentication.BearerTokenResolver;
import me.sspjavalabs.shipmentlifecycle.db.adapter.LoadPortAdapter;
import me.sspjavalabs.shipmentlifecycle.db.infra.BasicPgDataSource;
import me.sspjavalabs.shipmentlifecycle.infra.GsonSerDer;
import me.sspjavalabs.shipmentlifecycle.infra.JsonSerDer;
import me.sspjavalabs.shipmentlifecycle.rest_server.RestServer;
import me.sspjavalabs.shipmentlifecycle.rest_server.authentication.BearerTokenAuthenticator;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.BaseRequestHandler;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.ShipmentsRequestHandler;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.EventLog;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.ShipmentLifecycle;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.LoadPort;

public class Launcher {
  private static RestServer restServer;
  private static BasicPgDataSource basicPgDataSource;

  static void main() throws IOException {
    initialize();
  }

  static void initialize() throws IOException {

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    IO.println("Shutting down ...");
                    restServer.close();
                    basicPgDataSource.close();
                    IO.println("Successfully closed resources");
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }));

    System.setProperty("SHIPMENT_LIFECYCLE_USER_TOKEN", "12345");

    Gson gson =
        new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    JsonSerDer jsonSerDer = new GsonSerDer(gson);

    basicPgDataSource =
        BasicPgDataSource.of(
            "jdbc:postgresql://localhost:5432/shipment_lifecycle", "admin", "admin");

    ShipmentLifecycle shipmentLifecycle = new ShipmentLifecycle();
    LoadPort loadPort = new LoadPortAdapter(basicPgDataSource);
    EventLog eventLog = new EventLog(loadPort, shipmentLifecycle);
    ShipmentsRequestHandler shipmentsRequestHandler = new ShipmentsRequestHandler(eventLog);
    BaseRequestHandler baseRequestHandler =
        new BaseRequestHandler(shipmentsRequestHandler, jsonSerDer);

    restServer =
        RestServer.initialise(
            8080,
            new BearerTokenAuthenticator(
                new BearerTokenResolver.SystemPropertiesBearerTokenResolver()),
            Map.of(ShipmentsRequestHandler.ROOT_RESOURCE_URL, baseRequestHandler));
    restServer.start();
  }
}
