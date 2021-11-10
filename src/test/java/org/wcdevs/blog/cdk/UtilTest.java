package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.ConstructNode;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UtilTest {


  @Test
  void string() {
    String s1 = randomString(), s2 = UUID.randomUUID().toString();
    assertEquals(s1 + s2, Util.string(s1, s2));
  }

  private static String randomString() {
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

  @Test
  void getValueInAppReturnsNullOK() {
    StaticallyMockedCdk.executeTest(() -> {
      var node = mock(ConstructNode.class);
      var app = mock(App.class);
      when(app.getNode()).thenReturn(node);

      assertNull(Util.getValueInApp(randomString(), app, false));
    });
  }

  private static Stream<Arguments> getValueOrDefaultReturnsOKForNotNullDefaultArgs() {
    return Stream.of(arguments(null, false, false, false),
                     arguments(randomString(), true, false, false),
                     arguments(randomString(), false, true, false),
                     arguments(randomString(), false, false, true),
                     arguments(randomString(), false, false, false));
  }

  @ParameterizedTest
  @MethodSource("getValueOrDefaultReturnsOKForNotNullDefaultArgs")
  void getValueOrDefaultReturnsOKForNotNullDefault(String key, boolean nullApp,
                                                   boolean nullNode, boolean nullValue) {
    StaticallyMockedCdk.executeTest(() -> {
      var appWithNullNode = mock(App.class);

      var node = nullNode ? null : mock(ConstructNode.class);
      var appWithNullValue = mock(App.class);
      when(appWithNullValue.getNode()).thenReturn(node);

      var nodeValue = randomString();
      var nodeWithValue = nullNode ? null : mock(ConstructNode.class);
      if (!nullNode) {
        when(nodeWithValue.tryGetContext(any())).thenReturn(nodeValue);
      }
      var appWithValue = mock(App.class);
      when(appWithValue.getNode()).thenReturn(nodeWithValue);

      var app = nullNode ? appWithNullNode : (nullValue ? appWithNullValue : appWithValue);

      var expected = !nullApp && !nullNode && !nullValue ? nodeValue : randomString();
      var actual = Util.getValueOrDefault(key, nullApp ? null : app, expected);
      assertEquals(expected, actual);
    });
  }

  @Test
  void getValueInAppReturnsNotNullOK() {
    StaticallyMockedCdk.executeTest(() -> {
      var expected = randomString();
      var node = mock(ConstructNode.class);
      when(node.tryGetContext(any())).thenReturn(expected);

      var app = mock(App.class);
      when(app.getNode()).thenReturn(node);

      assertEquals(expected, Util.getValueInApp(randomString(), app, false));
    });
  }

  @Test
  void getValueInAppRequireNotNullValueReturnsOk() {
    StaticallyMockedCdk.executeTest(() -> {
      var expected = randomString();
      var node = mock(ConstructNode.class);
      when(node.tryGetContext(any())).thenReturn(expected);

      var app = mock(App.class);
      when(app.getNode()).thenReturn(node);

      assertEquals(expected, Util.getValueInApp(randomString(), app));
    });
  }

  @ParameterizedTest
  @MethodSource("keyValueMockAppAndReturnedMock")
  void getValueInAppThrowsNPEIfArgumentIsNull(String valueKey, boolean nullApp, Object expected) {
    StaticallyMockedCdk.executeTest(() -> {
      var node = mock(ConstructNode.class);
      when(node.tryGetContext(any())).thenReturn(expected);

      var app = mock(App.class);
      when(app.getNode()).thenReturn(node);

      assertThrows(NullPointerException.class,
                   () -> Util.getValueInApp(valueKey, !nullApp ? app : null));
    });
  }

  static Stream<Arguments> keyValueMockAppAndReturnedMock() {
    return Stream.of(arguments(randomString(), true, randomString()),
                     arguments(null, false, randomString()),
                     arguments(randomString(), false, null));
  }

  @Test
  void environmentFrom() {
    StaticallyMockedCdk.executeTest(() -> assertNotNull(Util.environmentFrom(randomString(),
                                                                             randomString())));
  }

  @ParameterizedTest
  @MethodSource("valueAndExpectedForNotEmptyNotNullTest")
  void notEmptyNotNullReturnsExpected(String value, boolean expected) {
    assertEquals(expected, Util.isNotEmptyNotNull(value));
  }

  static Stream<Arguments> valueAndExpectedForNotEmptyNotNullTest() {
    return Stream.of(arguments(randomString(), true),
                     arguments(null, false),
                     arguments("", false),
                     arguments("null", false));
  }
}
