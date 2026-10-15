package me.sspjavalabs.shipmentlifecycle.infra;

import com.google.gson.Gson;

public class GsonSerDer implements JsonSerDer {
  private final Gson gson;

  public GsonSerDer(Gson gson) {
    this.gson = gson;
  }

  @Override
  public String toJsonString(Object input) {
    return gson.toJson(input);
  }

  @Override
  public <O> O toObject(String input, Class<O> type) {
    return gson.fromJson(input, type);
  }
}
