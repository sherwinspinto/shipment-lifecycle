package me.sspjavalabs.shipmentlifecycle.helper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

public class RestClient implements AutoCloseable {
  private final HttpClient httpClient;

  private RestClient() {
    httpClient = buildHttpClient();
  }

  private HttpClient buildHttpClient() {

    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
  }

  public static RestClient of() {
    return new RestClient();
  }

  public HttpResponse<String> get(Map<String, String> headers, String endpoint)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(endpoint)).GET();
    if (!headers.isEmpty()) builder.headers(getHeadersArrayFromMap(headers));
    HttpRequest request = builder.build();

    HttpResponse.BodyHandler<String> bodyHandler =
        responseInfo -> HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
    return httpClient.send(request, bodyHandler);
  }

  public HttpResponse<String> post(Map<String, String> headers, String requestBody, String endPoint)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(endPoint))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody));
    if (!headers.isEmpty()) builder.headers(getHeadersArrayFromMap(headers));
    HttpRequest request = builder.build();

    HttpResponse.BodyHandler<String> bodyHandler =
        responseInfo -> HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
    return httpClient.send(request, bodyHandler);
  }

  @Override
  public void close() throws Exception {
    httpClient.close();
  }

  private String[] getHeadersArrayFromMap(Map<String, String> headers) {
    String[] headersArray = new String[headers.size() * 2];
    Iterator<Map.Entry<String, String>> iterator = headers.entrySet().iterator();
    int i = 0;
    while (iterator.hasNext()) {
      Map.Entry<String, String> entry = iterator.next();
      headersArray[i] = entry.getKey();
      i++;
      headersArray[i] = entry.getValue();
      i++;
    }
    return headersArray;
  }
}
