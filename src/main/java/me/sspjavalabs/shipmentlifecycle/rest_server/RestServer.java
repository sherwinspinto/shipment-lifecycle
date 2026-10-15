package me.sspjavalabs.shipmentlifecycle.rest_server;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class RestServer implements AutoCloseable {
  private final HttpServer httpServer;
  private static final String SHIPMENTS_BASE_URL = "/shipments";

  private RestServer(HttpServer httpServer) {
    this.httpServer = httpServer;
  }

  public static RestServer initialise(
      int port, Authenticator authenticator, Map<String, HttpHandler> requestHandlerMap)
      throws IOException {
    HttpServer createdServer = HttpServer.create(new InetSocketAddress(port), 0);
    createdServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    setAuthenticators(addContexts(createdServer, requestHandlerMap), authenticator);
    return new RestServer(createdServer);
  }

  public void start() {
    httpServer.start();
  }

  @Override
  public void close() throws Exception {
    httpServer.stop(1);
  }

  static List<HttpContext> addContexts(
      HttpServer server, Map<String, HttpHandler> requestHandlerMap) {
    return requestHandlerMap.entrySet().stream()
        .map(entry -> server.createContext(entry.getKey(), entry.getValue()))
        .toList();
  }

  static void setAuthenticators(List<HttpContext> httpContexts, Authenticator authenticator) {
    httpContexts.forEach(httpContext -> httpContext.setAuthenticator(authenticator));
  }
}
