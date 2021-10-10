package org.wcdevs.blog.cdk;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

final class Util {
  static final String NON_ALPHANUMERIC_VALUES = "[^a-zA-Z0-9-]";

  private Util() {
  }

  static String string(Object... values) {
    return Arrays.stream(values)
                 .filter(Objects::nonNull)
                 .map(Object::toString)
                 .collect(Collectors.joining());
  }

  static String joinedString(CharSequence joiner, Object... values) {
    return Arrays.stream(values)
                 .filter(Objects::nonNull)
                 .map(Object::toString)
                 .collect(Collectors.joining(joiner));
  }

  /**
   * Returns the provided string with the non-alphanumeric characters striped. Useful when some AWS
   * resources don't cope with them in resource names.
   *
   * @param value String value to be sanitized.
   *
   * @return The sanitized String.
   */
  static String sanitize(String value) {
    return Objects.requireNonNull(value).replaceAll(NON_ALPHANUMERIC_VALUES, "");
  }
}
