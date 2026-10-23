package me.sspjavalabs.shipmentlifecycle.fileparser;

import static me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileRowMapper.*;
import static me.sspjavalabs.shipmentlifecycle.fileparser.CarrierFileRowMapper.Row.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
    int fieldLengthCount = 0;
    int fieldNumberCount = 0;
    int currentLineLengthCount = 0;
    int lineNumberCount = 0;
    String[] trackingEventArray = new String[5];
    byte[] tmpFieldValue = new byte[128];
    byte[] line = new byte[1024];
    boolean flushed = false;
    int check;
    boolean inQuotes = false;
    byte peeked;
    while ((check = nextByte(byteBuffer, in)) != -1) {
      curr = (byte) check;
      peeked = curr == QUOTES_BYTE ? (byte) peek(byteBuffer, in) : -1;
      if (curr == QUOTES_BYTE && curr == peeked) nextByte(byteBuffer, in);
      if (curr == QUOTES_BYTE && inQuotes && peeked != QUOTES_BYTE) inQuotes = false;
      else if (curr == QUOTES_BYTE) inQuotes = true;
      if (curr != NEWLINE_BYTE || inQuotes) {
        line[currentLineLengthCount] = curr;
        currentLineLengthCount++;
      }
      if (!inQuotes && (curr == COMMA_BYTE || curr == NEWLINE_BYTE)) {

        trackingEventArray[fieldNumberCount++] =
            new String(tmpFieldValue, 0, fieldLengthCount, StandardCharsets.UTF_8);
        flushed = true;
        fieldLengthCount = 0;
        if (curr == NEWLINE_BYTE) {
          lineNumberCount++;
          if (lineNumberCount != 1) {
            flushRow(
                trackingEventArray,
                lineNumberCount,
                line,
                currentLineLengthCount,
                parsedEvents,
                badRows);
          }
          fieldNumberCount = 0;
          currentLineLengthCount = 0;
        }
      } else {
        flushed = false;
        if (curr != QUOTES_BYTE || peeked == QUOTES_BYTE) tmpFieldValue[fieldLengthCount++] = curr;
      }
    }
    if (!flushed) {
      lineNumberCount++;
      trackingEventArray[fieldNumberCount++] =
          new String(tmpFieldValue, 0, fieldLengthCount, StandardCharsets.UTF_8);
      flushRow(
          trackingEventArray, lineNumberCount, line, currentLineLengthCount, parsedEvents, badRows);
    }

    return new CarrierFileParser.ParsedResults(parsedEvents, badRows);
  }

  void flushRow(
      String[] trackingEventArray,
      int lineNumberCount,
      byte[] line,
      int currentLineLengthCount,
      List<ParsedEvent> parsedEvents,
      List<BadRow> badRows) {
    CsvLineByteArrayInfo csvLineByteArrayInfo =
        new CsvLineByteArrayInfo(line, 0, currentLineLengthCount);
    Row row =
        toRow(
            trackingEventArray,
            lineNumberCount,
            () ->
                new String(
                    csvLineByteArrayInfo.bytes,
                    csvLineByteArrayInfo.start,
                    csvLineByteArrayInfo.end));
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

  record CsvLineByteArrayInfo(byte[] bytes, int start, int end) {}
}
