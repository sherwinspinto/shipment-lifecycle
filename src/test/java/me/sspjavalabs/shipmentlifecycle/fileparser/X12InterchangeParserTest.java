package me.sspjavalabs.shipmentlifecycle.fileparser;

import static me.sspjavalabs.shipmentlifecycle.fileparser.X12InterchangeParser.*;
import static me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser.*;
import static me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser.Parsed.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class X12InterchangeParserTest {
  static final X12InterchangeParser parser = new X12InterchangeParser();

  @Test
  @DisplayName("Testing separator extraction from ISA header")
  public void test_extract_separators() {
    String input =
        "ISA|00|          |00|          |ZZ|SENDER|ZZ|RECEIVER|260824|1800|U|00401|000000001|0|P|>~\n"
            + "IEA|0|000000001~";
    Separators separators = new X12InterchangeParser().extractSeparators(input);
    assertEquals(new Separators('|', '>', '~'), separators);
  }

  @Test
  @DisplayName("separators read from ISA, ISA and IEA in the list")
  void test_1() {
    String input =
        "ISA|00|          |00|          |ZZ|SENDER|ZZ|RECEIVER|260824|1800|U|00401|000000001|0|P|>~\n"
            + "IEA|0|000000001~";
    Interchange expected =
        new Interchange(
            new Separators('|', '>', '~'),
            List.of(
                new Good(
                    new Segment(
                        "ISA",
                        List.of(
                            "00",
                            "          ",
                            "00",
                            "          ",
                            "ZZ",
                            "SENDER",
                            "ZZ",
                            "RECEIVER",
                            "260824",
                            "1800",
                            "U",
                            "00401",
                            "000000001",
                            "0",
                            "P",
                            ">"))),
                new Good(new Segment("IEA", List.of("0", "000000001")))));
    assertEquals(expected, parser.parse(input));
  }

  @DisplayName("Does not start with ISA")
  @Test
  void test_2() {
    String input = "IEA|0|000000001~";
    Interchange interchange = parser.parse(input);
    Interchange expected =
        new Interchange(
            null, List.of(new Bad(input.substring(0, 6), "Could not find ISA segment")));
    assertEquals(expected, interchange);
  }

  @DisplayName("Split on separators extracted from the ISA Segment")
  @Test
  void test_3() {
    String input =
        "ISA|00|          |00|          |ZZ|SENDER|ZZ|RECEIVER|260824|1800|U|00401|000000001|0|P|>~\n"
            + "B10|S1|1Z999|FDXG~\n"
            + "IEA|0|000000001~";

    Interchange interchange = parser.parse(input);
    Separators expectedSeparators = new Separators('|', '>', '~');
    Segment b10Expected = new Segment("B10", List.of("S1", "1Z999", "FDXG"));
    assertEquals(expectedSeparators, interchange.separators());
    assertEquals(3, interchange.segments().size());
    assertInstanceOf(Good.class, interchange.segments().get(1));
    assertEquals(b10Expected, ((Good) interchange.segments().get(1)).segment());
  }

  @DisplayName("Bytes after IEA, captured as Bad")
  @Test
  void test_4() {
    String input =
        "ISA|00|          |00|          |ZZ|SENDER|ZZ|RECEIVER|260824|1800|U|00401|000000001|0|P|>~\n"
            + "B10|S1|1Z999|FDXG~\n"
            + "IEA|0|000000001~\n"
            + "this is trailing junk";

    Interchange interchange = parser.parse(input);
    Parsed expectedLastParsed =
        new Bad("\nthis is trailing junk", "File has data after IEA segment");
    assertEquals(4, interchange.segments().size());
    assertEquals(expectedLastParsed, interchange.segments().getLast());
  }

  @DisplayName("All separators not present")
  @Test
  void test_5() {
    String input = "ISA|00|          |00|          |ZZ|SENDER";
    Interchange expected = new Interchange(null, List.of(new Bad("ISA|00", "Bad ISA Segment")));
    assertEquals(expected, parser.parse(input));
  }

  @DisplayName("Empty element inside ISA")
  @Test
  void test_6() {
    String input =
        "ISA|00||00|          |ZZ|SENDER         |ZZ|RECEIVER       |260824|1800|U|00401|000000001|0|P|>~\n"
            + "IEA|0|000000001~";
    String secondElementExpected = "";
    assertEquals(
        secondElementExpected,
        ((Good) parser.parse(input).segments().getFirst()).segment().elements().get(1));
  }

  @DisplayName("ISA after IEA")
  @Test
  void test_7() {
    String input =
        "ISA|00||00|          |ZZ|SENDER         |ZZ|RECEIVER       |260824|1800|U|00401|000000001|0|P|>~\n"
            + "IEA|0|000000001~\n"
            + "ISA|00|          |00|          |ZZ|OTHER|ZZ|OTHER|260824|1800|U|00401|000000002|0|P|>~";

    String reasonExpected = "File has data after IEA segment";
    Interchange interchange = parser.parse(input);
    assertInstanceOf(Bad.class, interchange.segments().getLast());
    assertEquals(reasonExpected, ((Bad) interchange.segments().getLast()).reason());
  }

  @DisplayName("No split on sub element separator")
  @Test
  void test_8() {
    String input =
        "ISA|00||00|          |ZZ|SENDER         |ZZ|RECEIVER       |260824|1800|U|00401|000000001|0|P|>~\n"
            + "B10|S1|1Z9>99|FDXG~\n"
            + "IEA|0|000000001~";

    String expectedValue = "1Z9>99";
    Interchange interchange = parser.parse(input);
    assertInstanceOf(Good.class, interchange.segments().getLast());
    assertEquals(expectedValue, ((Good) interchange.segments().get(1)).segment().elements().get(1));
  }

  @DisplayName("same element. The release survives the ISA path")
  @Test
  void test_9() {
    String input =
        "ISA|00|          |00|          |ZZ|SENDER|ZZ|RECEIVER|260824|1800|U|00401|000000001|0|P|>~\n"
            + "B10|S1|1Z9?|99|FDXG~\n"
            + "IEA|0|000000001~";
    Good segment = new Good(new Segment("B10", List.of("S1", "1Z9|99", "FDXG")));
    Interchange interchange = parser.parse(input);
    assertInstanceOf(Good.class, interchange.segments().get(1));
    assertEquals(segment, interchange.segments().get(1));
  }
}
