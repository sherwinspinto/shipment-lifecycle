package me.sspjavalabs.shipmentlifecycle.infra;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GsonSerDer implements JsonSerDer {
  private final Gson gson;

  public GsonSerDer() {
    this.gson =
        new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
  }

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
