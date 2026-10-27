package me.sspjavalabs.shipmentlifecycle.fileparser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.sspjavalabs.shipmentlifecycle.fileparser.X12InterchangeParser.Separators;
import me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser.Parsed.Bad;
import me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser.Parsed.Good;

public class X12Parser {
  public record Segment(String tag, List<String> elements) {}

  private final Separators separators;
  private final char release;

  public X12Parser(Separators separators, char release) {
    this.separators = separators;
    this.release = release;
  }

  public X12Parser(Separators separators) {
    this.release = '?';
    this.separators = separators;
  }

  public List<Parsed> parse(String input) {
    char[] chars = input.toCharArray();
    Map<Character, Token> tokenMap = getTokenMap(separators, release);
    StringBuilder accumulator = new StringBuilder();
    List<Parsed> parsedSegments = new ArrayList<>();
    List<String> fieldAccumulator = new ArrayList<>();
    StringBuilder rawSegment = new StringBuilder();
    boolean foundIEA = false;
    int i = 0;
    boolean stopParsing = false;
    for (; i < chars.length; i++) {
      char aChar = chars[i];
      Token currentToken = tokenMap.getOrDefault(aChar, Token.CHAR);
      if (stopParsing) break;
      rawSegment.append(aChar);
      if (aChar == '\n') continue;
      switch (currentToken) {
        case RELEASE -> {
          Character peeked = peek(chars, i);
          if (peeked != null) {
            accumulator.append(peeked);
            i++;
          } else {
            parsedSegments.add(new Bad(rawSegment.toString(), "release, with nothing to release"));
            accumulator.delete(0, accumulator.length());
          }
        }
        case CHAR, SUB_ELEMENT_SEP -> accumulator.append(aChar);
        case ELEMENT_SEP -> {
          fieldAccumulator.add(accumulator.toString());
          accumulator.delete(0, accumulator.length());
        }
        case SEGMENT_SEP -> {
          fieldAccumulator.add(accumulator.toString());
          accumulator.delete(0, accumulator.length());
          String tag = fieldAccumulator.getFirst();
          parsedSegments.add(
              new Good(
                  new Segment(
                      tag, List.copyOf(fieldAccumulator.subList(1, fieldAccumulator.size())))));
          fieldAccumulator.clear();
          rawSegment.delete(0, rawSegment.length());
          if ("IEA".equals(tag)) {
            stopParsing = true;
            foundIEA = true;
          }
        }
      }
    }

    if (foundIEA && i < input.length() - 1) {
      parsedSegments.add(new Bad(input.substring(i), "File has data after IEA segment"));
    } else if (!accumulator.isEmpty())
      parsedSegments.add(
          new Bad(rawSegment.toString(), "Invalid Segment: Missing Segment Separator '~'"));

    return parsedSegments;
  }

  private Character peek(char[] chars, int currentIndex) {
    return (currentIndex + 1) < chars.length ? chars[currentIndex + 1] : null;
  }

  public sealed interface Parsed {
    record Good(Segment segment) implements Parsed {}

    record Bad(String raw, String reason) implements Parsed {}
  }

  static Map<Character, Token> getTokenMap(Separators separators, char release) {
    return Map.of(
        separators.element(),
        Token.ELEMENT_SEP,
        separators.sub(),
        Token.SUB_ELEMENT_SEP,
        separators.segment(),
        Token.SEGMENT_SEP,
        release,
        Token.RELEASE);
  }

  enum Token {
    CHAR,
    ELEMENT_SEP,
    SUB_ELEMENT_SEP,
    SEGMENT_SEP,
    RELEASE
  }
}
