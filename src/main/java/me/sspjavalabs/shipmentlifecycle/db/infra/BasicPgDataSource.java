package me.sspjavalabs.shipmentlifecycle.db.infra;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.*;

public class BasicPgDataSource implements AutoCloseable {
  private final HikariDataSource hikariDataSource;

  public BasicPgDataSource(String jdbcUrl, String username, String password) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    config.setUsername(username);
    config.setPassword(password);

    this.hikariDataSource = new HikariDataSource(config);
  }

  @Override
  public void close() {
    if (this.hikariDataSource != null && !this.hikariDataSource.isClosed()) {
      this.hikariDataSource.close();
    }
  }

  public List<Map<String, Object>> executeSelect(String sql, List<Object> paramVals)
      throws SQLException {
    try (Connection connection = hikariDataSource.getConnection();
        PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

      for (int i = 0; i < paramVals.size(); i++) {
        preparedStatement.setObject(i + 1, paramVals.get(i));
      }

      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        return convertToTable(resultSet);
      }
    }
  }

  public long executeInsertWithGeneratedKey(String sql, List<Object> paramVals)
      throws SQLException {
    try (Connection connection = hikariDataSource.getConnection();
        PreparedStatement preparedStatement =
            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

      for (int i = 0; i < paramVals.size(); i++) {
        if (paramVals.get(i) instanceof Optional<?> o) {
          if (o.isPresent()) preparedStatement.setObject(i + 1, o.get());
          else preparedStatement.setObject(i + 1, null);
        } else preparedStatement.setObject(i + 1, paramVals.get(i));
      }

      preparedStatement.executeUpdate();
      try (ResultSet generatedKey = preparedStatement.getGeneratedKeys()) {
        if (generatedKey.next()) return generatedKey.getLong(1);
        else return -1;
      }
    }
  }

  public static BasicPgDataSource of(String dbURL, String username, String password) {
    return new BasicPgDataSource(dbURL, username, password);
  }

  List<Map<String, Object>> convertToTable(ResultSet resultSet) {
    try {
      ResultSetMetaData metaData = resultSet.getMetaData();
      int columnCount = metaData.getColumnCount();
      List<Map<String, Object>> resultTable = new ArrayList<>();
      while (resultSet.next()) {
        Map<String, Object> rowMap = new HashMap<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
          rowMap.put(metaData.getColumnName(i), resultSet.getObject(i));
        }
        resultTable.add(rowMap);
      }
      return resultTable;
    } catch (SQLException sqlException) {
      System.out.println("Exception => " + sqlException.getMessage());
    }
    return Collections.emptyList();
  }
}
