package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class UtilTest {
  @Test
  void string() {
    String s1 = randomString(), s2 = UUID.randomUUID().toString();
    assertEquals(s1 + s2, Util.string(s1, s2));
  }

  private String randomString() {
    return UUID.randomUUID().toString();
  }

  @Test
  void joinedString() {
    String s1 = randomString(), s2 = randomString(), joiner = randomString();
    assertEquals(s1 + joiner + s2, Util.joinedString(joiner, s1, s2));
  }

  @Test
  void sanitize() {
    String rawValue = UUID.randomUUID().toString() + ZonedDateTime.now();

    String sanitized = Util.sanitize(rawValue);

    assertFalse(sanitized.matches(Util.NON_ALPHANUMERIC_VALUES_AND_HYPHEN));
  }

  @ParameterizedTest
  @MethodSource("rawAndSanitizedValues")
  void dbSanitizedShouldCleanProperlyRawValueToBeAValidDBName(String rawValue, String sanitized) {
    assertEquals(sanitized, Util.dbSanitized(rawValue));
  }

  static Stream<Arguments> rawAndSanitizedValues() {
    return Stream.of(arguments("a!s@d#f$g%h^h&jj*aksi^kl(lj)kj_-KL+", "asdfghhjjaksiklljkjKL"),
                     arguments("!@#~$%^&*()_+[]:;/.,<>\"\\|", "dbName"),
                     arguments("!S@d#f$g%h^h&jj*aksi^kl(lj)kj_-KL+", "aSdfghhjjaksiklljkjKL"));
  }
}
