package me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import me.sspjavalabs.shipmentlifecycle.infra.JsonSerDer;
import me.sspjavalabs.shipmentlifecycle.rest_server.RestServerFailure;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.RequestHandlerResult;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.ResponseDto;

public class BaseRequestHandler implements HttpHandler {
  private final RequestHandler requestHandler;

  private final JsonSerDer jsonSerDer;

  public BaseRequestHandler(RequestHandler requestHandler, JsonSerDer jsonSerDer) {
    this.requestHandler = requestHandler;
    this.jsonSerDer = jsonSerDer;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try {
      RequestHandlerResult handlerResult =
          switch (requestHandler) {
            case ShipmentsRequestHandler shipmentsRequestHandler ->
                shipmentsRequestHandler.handleRequest(
                    exchange.getRequestMethod(), exchange.getRequestURI().getRawPath());
          };

      switch (handlerResult) {
        case RequestHandlerResult.Success(ResponseDto responseDto) ->
            sendSuccess(exchange, responseDto);
        case RequestHandlerResult.Failure(
                RestServerFailure.MethodNotSupportedFailure(
                    String method,
                    String resourceUrl,
                    List<String> allowedMethods)) ->
            sendNotSupportedMethod(exchange, allowedMethods);
        case RequestHandlerResult.Failure(
                RestServerFailure.UnmatchedPathFailure(String method, String url)) ->
            sendBadRequest(
                url,
                method,
                "{ \"error\": \"The requested resource path was not found\"}",
                exchange);
      }
    } catch (Exception exception) {
      sendInternalServerError(exchange);
    }
  }

  void sendInternalServerError(HttpExchange exchange) throws IOException {
    exchange.sendResponseHeaders(500, 0);
    writeToOutputStream(exchange, null);
  }

  void sendNotSupportedMethod(HttpExchange exchange, List<String> allowedMethods)
      throws IOException {
    exchange.getResponseHeaders().add("Allow", String.join(",", allowedMethods));
    exchange.sendResponseHeaders(405, -1);
    writeToOutputStream(exchange, null);
  }

  void writeToOutputStream(HttpExchange exchange, String body) throws IOException {
    try (OutputStream outputStream = exchange.getResponseBody()) {
      if (body != null) outputStream.write(body.getBytes(StandardCharsets.UTF_8));
    }
  }

  void sendSuccess(HttpExchange exchange, ResponseDto responseDto) throws IOException {
    String response = jsonSerDer.toJsonString(responseDto);
    exchange.sendResponseHeaders(200, response.length());
    writeToOutputStream(exchange, response);
  }

  void sendBadRequest(String path, String method, String failureMessage, HttpExchange exchange)
      throws IOException {
    exchange.sendResponseHeaders(404, failureMessage.length());
    writeToOutputStream(exchange, failureMessage);
  }
}
