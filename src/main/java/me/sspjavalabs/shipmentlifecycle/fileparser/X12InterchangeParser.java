package me.sspjavalabs.shipmentlifecycle.fileparser;

import static me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser.*;
import static me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser.Parsed.*;

import java.util.List;

public class X12InterchangeParser {
  static final int SUB_ELEMENT_SEPARATOR = 16;
  static final int ISA_LAST_INDEX = 105;
  static final int ISA_LENGTH = 106;

  public record Separators(char element, char sub, char segment) {}

  public record Interchange(Separators separators, List<Parsed> segments) {}

  public Interchange parse(String input) {
    if (!startWithISA(input))
      return new Interchange(
          null,
          List.of(
              new Bad(
                  input.substring(0, Math.min(input.length(), 6)), "Could not find ISA segment")));

    Separators separators = extractSeparators(input);
    if (separators == null)
      return new Interchange(
          null,
          List.of(new Bad(input.substring(0, Math.min(input.length(), 6)), "Bad ISA Segment")));

    X12Parser parser = new X12Parser(separators);
    return new Interchange(separators, parser.parse(input));
  }

  boolean startWithISA(String input) {
    return input.charAt(0) == 'I' && input.charAt(1) == 'S' && input.charAt(2) == 'A';
  }

  Separators extractSeparators(String input) {
    char element = input.charAt(3);
    int separatorCount = 0;
    int indexLastSeparator = 0;
    if (input.length() < ISA_LAST_INDEX) return null;
    while (separatorCount < SUB_ELEMENT_SEPARATOR) {
      if (input.charAt(indexLastSeparator) == element) separatorCount++;
      indexLastSeparator++;
      if (indexLastSeparator > ISA_LENGTH) return null;
    }
    char sub = input.charAt(indexLastSeparator++);
    char segment = input.charAt(indexLastSeparator);
    return new Separators(element, sub, segment);
  }
}
