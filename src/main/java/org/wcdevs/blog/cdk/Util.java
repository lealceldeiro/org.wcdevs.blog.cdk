package org.wcdevs.blog.cdk;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Util {
  static final String NON_ALPHANUMERIC_VALUES_AND_HYPHEN = "[^a-zA-Z0-9-]";
  static final String NON_ALPHANUMERIC_VALUES = "[^a-zA-Z0-9]";
  static final String LOWERCASE_LETTERS_ONLY = "[a-z]";

  private Util() {
  }

  public static String string(Object... values) {
    return Arrays.stream(values)
                 .filter(Objects::nonNull)
                 .map(Object::toString)
                 .collect(Collectors.joining());
  }

  public static String joinedString(CharSequence joiner, Object... values) {
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
  public static String sanitize(String value) {
    return Objects.requireNonNull(value).replaceAll(NON_ALPHANUMERIC_VALUES_AND_HYPHEN, "");
  }

  /**
   * Returns the provided string with all characters not compatible with a DB name striped.
   *
   * @param value String value to be sanitized.
   *
   * @return The sanitized String.
   */
  public static String dbSanitized(String value) {
    var alphanumeric = Objects.requireNonNull(value).replaceAll(NON_ALPHANUMERIC_VALUES, "");
    if (alphanumeric.isEmpty()) {
      alphanumeric = "dbName";
    }
    return alphanumeric.substring(0, 1).matches(LOWERCASE_LETTERS_ONLY)
           ? alphanumeric
           : "a" + alphanumeric;
  }
}
