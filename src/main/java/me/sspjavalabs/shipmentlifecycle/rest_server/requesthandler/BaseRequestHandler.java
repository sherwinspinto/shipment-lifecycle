package me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler;

import static me.sspjavalabs.shipmentlifecycle.rest_server.RestServerFailure.*;
import static me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.RequestHandlerResult.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import me.sspjavalabs.shipmentlifecycle.rest_server.RestServerFailure;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.RequestHandlerResult;

public class BaseRequestHandler implements HttpHandler {
  private final RequestHandler requestHandler;

  public BaseRequestHandler(RequestHandler requestHandler) {
    this.requestHandler = requestHandler;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try {
      RequestHandlerResult handlerResult =
          switch (requestHandler) {
            case ShipmentsRequestHandler shipmentsRequestHandler ->
                shipmentsRequestHandler.handleRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath(),
                    handleRequestBodyStream(exchange.getRequestBody()));
          };

      switch (handlerResult) {
        case Ok(String responseBody) -> sendOk(exchange, responseBody);
        case Created(String responseBody, String location) ->
            sendCreated(exchange, responseBody, location);
        case Failure(RestServerFailure restServerFailure) ->
            sendFailure(exchange, restServerFailure);
      }
    } catch (Exception exception) {
      sendInternalServerError(exchange);
    }
  }

  void sendFailure(HttpExchange exchange, RestServerFailure restServerFailure) throws IOException {
    switch (restServerFailure) {
      case MethodNotSupportedFailure(List<String> allowedMethods) ->
          sendMethodNotSupported(exchange, allowedMethods);
      case UnmatchedPathFailure unmatchedPathFailure ->
          sendResourceNotFound(exchange, unmatchedPathFailure.responseBody());
      case BadRequestBody badRequestBody -> sendBadRequest(exchange, badRequestBody.responseBody());
      case InternalServerError() -> sendInternalServerError(exchange);
    }
  }

  void sendOk(HttpExchange exchange, String responseBody) throws IOException {
    exchange.sendResponseHeaders(200, responseBody.length());
    writeToOutputStream(exchange, responseBody);
  }

  void sendCreated(HttpExchange exchange, String responseBody, String createdResourceUrl)
      throws IOException {
    exchange.getResponseHeaders().add("Location", createdResourceUrl);
    exchange.sendResponseHeaders(201, responseBody.length());
    writeToOutputStream(exchange, responseBody);
  }

  void sendInternalServerError(HttpExchange exchange) throws IOException {
    exchange.sendResponseHeaders(500, 0);
    writeToOutputStream(exchange, null);
  }

  void sendMethodNotSupported(HttpExchange exchange, List<String> allowedMethods)
      throws IOException {
    exchange.getResponseHeaders().add("Allow", String.join(",", allowedMethods));
    exchange.sendResponseHeaders(405, -1);
    writeToOutputStream(exchange, null);
  }

  void sendResourceNotFound(HttpExchange exchange, String failureMessage) throws IOException {
    exchange.sendResponseHeaders(404, failureMessage.length());
    writeToOutputStream(exchange, failureMessage);
  }

  void sendBadRequest(HttpExchange exchange, String failureMessage) throws IOException {
    exchange.sendResponseHeaders(400, failureMessage.length());
    writeToOutputStream(exchange, failureMessage);
  }

  void writeToOutputStream(HttpExchange exchange, String body) throws IOException {
    try (OutputStream outputStream = exchange.getResponseBody()) {
      if (body != null) outputStream.write(body.getBytes(StandardCharsets.UTF_8));
    }
  }

  String handleRequestBodyStream(InputStream requestBodyInputStream) throws IOException {
    if (requestBodyInputStream == null) return "";
    return new String(requestBodyInputStream.readAllBytes());
  }
}
