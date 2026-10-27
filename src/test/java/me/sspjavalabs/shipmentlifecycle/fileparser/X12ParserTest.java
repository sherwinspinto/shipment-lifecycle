package me.sspjavalabs.shipmentlifecycle.fileparser;

import static me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser.*;
import static me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser.Parsed.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import me.sspjavalabs.shipmentlifecycle.fileparser.X12InterchangeParser.Separators;
import me.sspjavalabs.shipmentlifecycle.fileparser.X12Parser.Parsed.Good;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class X12ParserTest {

  @Test
  @DisplayName("one segment, tag `B10`, elements `S1`, `1Z999`, `FDXG`")
  void test_1() {
    Separators separators = new Separators('*', ' ', '~');
    X12Parser parser = new X12Parser(separators);
    String input = "B10*S1*1Z999*FDXG~";
    List<Parsed> segments = parser.parse(input);
    List<Parsed> expectedSegments =
        List.of(new Good(new Segment("B10", List.of("S1", "1Z999", "FDXG"))));
    assertEquals(1, segments.size());
    assertEquals(expectedSegments, segments);
  }

  @Test
  @DisplayName("three elements, middle is `\"\"`")
  void test_2() {
    Separators separators = new Separators('*', ' ', '~');
    X12Parser parser = new X12Parser(separators);
    String input = "B10*S1**FDXG~";
    List<Parsed> segments = parser.parse(input);
    List<Parsed> expectedSegments =
        List.of(new Good(new Segment("B10", List.of("S1", "", "FDXG"))));
    assertEquals(1, segments.size());
    assertEquals(expectedSegments, segments);
  }

  @Test
  @DisplayName("two segments, in order")
  void test_3() {
    Separators separators = new Separators('*', ' ', '~');
    X12Parser parser = new X12Parser(separators);
    String input = "B10*S1*1Z999*FDXG~AT7*D*NS***20260806~";
    List<Parsed> segments = parser.parse(input);
    List<Parsed> expectedSegments =
        List.of(
            new Good(new Segment("B10", List.of("S1", "1Z999", "FDXG"))),
            new Good(new Segment("AT7", List.of("D", "NS", "", "", "20260806"))));
    assertEquals(2, segments.size());
    assertEquals(expectedSegments, segments);
  }

  @Test
  @DisplayName("no terminator. One bad segment. No guess")
  void test_4() {
    Separators separators = new Separators('*', ' ', '~');
    X12Parser parser = new X12Parser(separators);
    String input = "B10*S1*1Z999*FDXG";
    List<Parsed> segments = parser.parse(input);
    List<Parsed> expectedSegments =
        List.of(new Bad("B10*S1*1Z999*FDXG", "Invalid Segment: Missing Segment Separator '~'"));
    assertEquals(1, segments.size());
    assertEquals(expectedSegments, segments);
  }

  @Test
  @DisplayName("one segment. The tag is the first element slot, not a special case")
  void test_5() {
    Separators separators = new Separators('*', ' ', '~');
    X12Parser parser = new X12Parser(separators);
    String input = "N1*SF*ACME*92*12345~";
    List<Parsed> segments = parser.parse(input);
    List<Parsed> expectedSegments =
        List.of(new Good(new Segment("N1", List.of("SF", "ACME", "92", "12345"))));
    assertEquals(1, segments.size());
    assertEquals(expectedSegments, segments);
  }

  @Test
  @DisplayName("empty elements kept. Count them ")
  void test_6() {
    Separators separators = new Separators('*', ' ', '~');
    X12Parser parser = new X12Parser(separators);
    String input = "AT7*D*NS***20260806*1800~";
    List<Parsed> segments = parser.parse(input);
    List<Parsed> expectedSegments =
        List.of(new Good(new Segment("AT7", List.of("D", "NS", "", "", "20260806", "1800"))));
    assertEquals(1, segments.size());
    assertEquals(expectedSegments, segments);
    switch (expectedSegments.getFirst()) {
      case Good(Segment segment) -> {
        assertEquals(2, segment.elements().stream().filter(String::isEmpty).count());
      }
      case Bad _ -> fail("Expected good");
    }
  }

  @Test
  @DisplayName("one element, not two. You are not splitting sub-elements yet")
  void test_7() {
    Separators separators = new Separators('*', ' ', '~');
    X12Parser parser = new X12Parser(separators);
    String input = "N1*SF*AC:ME*92*12345~";
    List<Parsed> segments = parser.parse(input);
    List<Parsed> expectedSegments =
        List.of(new Good(new Segment("N1", List.of("SF", "AC:ME", "92", "12345"))));
    assertEquals(1, segments.size());
    assertEquals(expectedSegments, segments);
  }

  @Test
  @DisplayName("Empty segment and no elements")
  void test_8() {
    Separators separators = new Separators('*', ' ', '~');
    X12Parser parser = new X12Parser(separators);
    String input = "N1*SF*AC:ME*92*12345~~";
    List<Parsed> segments = parser.parse(input);
    List<Parsed> expectedSegments =
        List.of(
            new Good(new Segment("N1", List.of("SF", "AC:ME", "92", "12345"))),
            new Good(new Segment("", List.of())));
    assertEquals(2, segments.size());
    assertEquals(expectedSegments, segments);
  }

  @Test
  @DisplayName("one segment. The middle element is 1Z9|99. The ? is gone")
  void test_9() {
    Separators separators = new Separators('|', ' ', '~');
    String input = "B10|S1|1Z9?|99|FDXG~";
    X12Parser parser = new X12Parser(separators);
    List<Parsed> expected = List.of(new Good(new Segment("B10", List.of("S1", "1Z9|99", "FDXG"))));
    assertEquals(expected, parser.parse(input));
  }

  @DisplayName("one segment, not two. The ?~ did not end it. The value ends with ~")
  @Test
  void test_10() {
    Separators separators = new Separators('|', ' ', '~');
    String input = "B10|S1|1Z999|FDXG?~~";
    X12Parser parser = new X12Parser(separators);
    List<Parsed> expected = List.of(new Good(new Segment("B10", List.of("S1", "1Z999", "FDXG~"))));
    assertEquals(expected, parser.parse(input));
  }

  @DisplayName("the element is what?. A doubled release is one ?")
  @Test
  void test_11() {
    Separators separators = new Separators('|', ' ', '~');
    String input = "B10|S1|what??|FDXG~";
    X12Parser parser = new X12Parser(separators);
    List<Parsed> expected = List.of(new Good(new Segment("B10", List.of("S1", "what?", "FDXG"))));
    assertEquals(expected, parser.parse(input));
  }

  @DisplayName("a Bad. A release with no character to release is not data")
  @Test
  void test_12() {
    String input = "B10|S1|1Z999|FDXG?";
    Separators separators = new Separators('|', ' ', '~');
    X12Parser parser = new X12Parser(separators);
    List<Parsed> expected =
        List.of(new Bad("B10|S1|1Z999|FDXG?", "release, with nothing to release"));
    assertEquals(expected, parser.parse(input));
  }

  @DisplayName("the element is 1Z9X99. The ? released the X and is gone")
  @Test
  void test_13() {
    String input = "B10|S1|1Z9?X99|FDXG~";
    Separators separators = new Separators('|', ' ', '~');
    X12Parser parser = new X12Parser(separators);
    List<Parsed> expected = List.of(new Good(new Segment("B10", List.of("S1", "1Z9X99", "FDXG"))));
    assertEquals(expected, parser.parse(input));
  }

  @DisplayName("one element containing >. You still do not split on >")
  @Test
  void test_14() {
    String input = "B10|S1|1Z9?>99|FDXG~";
    Separators separators = new Separators('|', ' ', '~');
    X12Parser parser = new X12Parser(separators);
    List<Parsed> expected = List.of(new Good(new Segment("B10", List.of("S1", "1Z9>99", "FDXG"))));
    assertEquals(expected, parser.parse(input));
  }

  @DisplayName("Release release on the boundary of an element")
  @Test
  void test_15() {
    String input = "B10|S1|end??|FDXG~";
    Separators separators = new Separators('|', ' ', '~');
    X12Parser parser = new X12Parser(separators);
    List<Parsed> expected = List.of(new Good(new Segment("B10", List.of("S1", "end?", "FDXG"))));
    assertEquals(expected, parser.parse(input));
  }

  @DisplayName("Release release on the boundary of a segment")
  @Test
  void test_16() {
    String input = "B10|S1|end|FDXG??~";
    Separators separators = new Separators('|', ' ', '~');
    X12Parser parser = new X12Parser(separators);
    List<Parsed> expected = List.of(new Good(new Segment("B10", List.of("S1", "end", "FDXG?"))));
    assertEquals(expected, parser.parse(input));
  }
}
