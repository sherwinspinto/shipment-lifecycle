package me.sspjavalabs.shipmentlifecycle.fileparser;

import static me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileRowMapper.*;
import static me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileRowMapper.RawLineHolder.*;
import static me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileRowMapper.Row.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import me.sspjavalabs.shipmentlifecycle.fileparser.BareMetalCSVParser.RowAccumulator.RowAccumulatorResult;

public class BareMetalCSVParser {
  private static final byte COMMA_BYTE = 0x2C;
  private static final byte NEWLINE_BYTE = 0x0A;
  private static final byte QUOTES_BYTE = 0x22;
  private final int bufferCapacity;

  public BareMetalCSVParser() {
    this.bufferCapacity = 8 * 1024;
  }

  public BareMetalCSVParser(int bufferCapacity) {
    this.bufferCapacity = bufferCapacity;
  }

  public CarrierFileParser.ParsedResults parse(ReadableByteChannel in) throws IOException {
    List<ParsedEvent> parsedEvents = new ArrayList<>();
    List<BadRow> badRows = new ArrayList<>();
    ByteBuffer byteBuffer = ByteBuffer.allocate(this.bufferCapacity);
    byteBuffer.limit(0);
    byte curr;
    int check;
    boolean inQuotes = false;

    FieldByteAccumulator fieldByteAccumulator = new FieldByteAccumulator(128);
    RowAccumulator rowAccumulator = new RowAccumulator(1024);
    FieldsAccumulator fieldsAccumulator = new FieldsAccumulator(5);
    State currentState = State.FIELD;
    ByteHolder lineHolder = new ByteHolder();

    while ((check = nextByte(byteBuffer, in)) != -1) {
      if ((byte) check == NEWLINE_BYTE) break;
    }

    while ((check = nextByte(byteBuffer, in)) != -1) {
      curr = (byte) check;
      Token currToken = Token.getType(curr);
      switch (currToken) {
        case QUOTE -> {
          if (isEscaped(currToken, in, byteBuffer, currentState)) {
            rowAccumulator.accumulate((byte) nextByte(byteBuffer, in));
            fieldByteAccumulator.accumulate(curr);
          } else {
            inQuotes = !inQuotes;
            currentState = inQuotes ? State.QUOTED_FIELD : State.FIELD;
            rowAccumulator.accumulate(curr);
          }
        }
        case COMMA -> {
          switch (currentState) {
            case FIELD -> fieldsAccumulator.add(fieldByteAccumulator.flush());
            case QUOTED_FIELD -> fieldByteAccumulator.accumulate(curr);
          }
          rowAccumulator.accumulate(curr);
        }
        case NEWLINE -> {
          switch (currentState) {
            case FIELD -> {
              fieldsAccumulator.add(fieldByteAccumulator.flush());
              flushRow(
                  fieldsAccumulator.flush(),
                  rowAccumulator.flush(),
                  parsedEvents,
                  badRows,
                  lineHolder);
              fieldsAccumulator.clear();
            }
            case QUOTED_FIELD -> {
              fieldByteAccumulator.accumulate(curr);
              rowAccumulator.accumulate(curr);
            }
          }
        }
        case CHAR -> {
          fieldByteAccumulator.accumulate(curr);
          rowAccumulator.accumulate(curr);
        }
      }
    }
    if (!fieldsAccumulator.flushed) {
      fieldsAccumulator.add(fieldByteAccumulator.flush());
      flushRow(
          fieldsAccumulator.flush(), rowAccumulator.flush(), parsedEvents, badRows, lineHolder);
    }

    return new CarrierFileParser.ParsedResults(parsedEvents, badRows);
  }

  boolean isEscaped(Token token, ReadableByteChannel in, ByteBuffer byteBuffer, State state)
      throws IOException {
    return state == State.QUOTED_FIELD
        && token == Token.QUOTE
        && Token.getType((byte) peek(byteBuffer, in)) == Token.QUOTE;
  }

  void flushRow(
      String[] trackingEventArray,
      RowAccumulatorResult rar,
      List<ParsedEvent> parsedEvents,
      List<BadRow> badRows,
      ByteHolder lineHolder) {
    Row row =
        toRow(
            trackingEventArray,
            rar.rowCount,
            lineHolder.setRawLineBytes(rar.rowBuffer).setStart(rar.start).setEnd(rar.end));
    switch (row) {
      case ParsedEvent parsedEvent -> parsedEvents.add(parsedEvent);
      case BadRow badRow -> badRows.add(badRow);
    }
  }

  int peek(ByteBuffer byteBuffer, ReadableByteChannel in) throws IOException {
    if (byteBuffer.position() < byteBuffer.limit()) {
      return byteBuffer.get(byteBuffer.position());
    } else if (bufferRefill(byteBuffer, in)) {
      if (byteBuffer.position() < byteBuffer.limit()) {
        return byteBuffer.get(byteBuffer.position());
      }
    }
    return -1;
  }

  int nextByte(ByteBuffer byteBuffer, ReadableByteChannel in) throws IOException {
    return bufferRefill(byteBuffer, in) ? byteBuffer.get() & 0xFF : -1;
  }

  boolean bufferRefill(ByteBuffer byteBuffer, ReadableByteChannel in) throws IOException {
    if (!byteBuffer.hasRemaining()) {
      byteBuffer.clear();
      int bytesRead = in.read(byteBuffer);
      if (bytesRead == -1) return false;
      byteBuffer.flip();
    }
    return true;
  }

  enum Token {
    COMMA,
    NEWLINE,
    QUOTE,
    CHAR;
    private static Map<Byte, Token> tokenMap =
        Map.of(
            COMMA_BYTE, COMMA,
            NEWLINE_BYTE, NEWLINE,
            QUOTES_BYTE, QUOTE);

    static Token getType(byte input) {
      return tokenMap.getOrDefault(input, CHAR);
    }
  }

  enum State {
    FIELD,
    QUOTED_FIELD
  }

  class FieldsAccumulator {
    private final String[] fields;
    private int fieldCount = 0;
    private boolean flushed = false;

    public FieldsAccumulator(int numberOfFields) {
      this.fields = new String[numberOfFields];
    }

    void add(String field) {
      fields[fieldCount++] = field;
      flushed = false;
    }

    // TODO: if fields size less that field count then log a warning ??
    String[] flush() {
      fieldCount = 0;
      flushed = true;
      return fields;
    }

    void clear() {
      Arrays.fill(fields, null);
    }
  }

  class FieldByteAccumulator {
    private final byte[] fieldBuffer;
    private int fieldLength = 0;

    FieldByteAccumulator(int bufferSize) {
      this.fieldBuffer = new byte[bufferSize];
    }

    void accumulate(byte aByte) {
      fieldBuffer[fieldLength++] = aByte;
    }

    String flush() {
      String fieldValue = new String(fieldBuffer, 0, fieldLength);
      fieldLength = 0;
      return fieldValue;
    }
  }

  class RowAccumulator {
    private final byte[] rowBuffer;
    private int rowBufferCount = 0;
    private final RowAccumulatorResult result = new RowAccumulatorResult();

    public RowAccumulator(int rowBufferLength) {
      this.rowBuffer = new byte[rowBufferLength];
    }

    void accumulate(byte aByte) {
      rowBuffer[rowBufferCount++] = aByte;
    }

    RowAccumulatorResult flush() {
      result.setRowBuffer(rowBuffer).setStart(0).setEnd(rowBufferCount).increaseRowCount();
      rowBufferCount = 0;
      return result;
    }

    class RowAccumulatorResult {
      private byte[] rowBuffer;
      private int start;
      private int end;
      private int rowCount = 1;

      RowAccumulatorResult setRowBuffer(final byte[] rowBuffer) {
        this.rowBuffer = rowBuffer;
        return this;
      }

      RowAccumulatorResult setStart(int start) {
        this.start = start;
        return this;
      }

      RowAccumulatorResult setEnd(int end) {
        this.end = end;
        return this;
      }

      RowAccumulatorResult increaseRowCount() {
        rowCount++;
        return this;
      }
    }
  }
}
