package org.wcdevs.blog.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.constructs.IConstruct;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Util {
  static final String NON_ALPHANUMERIC_VALUES_AND_HYPHEN = "[^a-zA-Z0-9-]";
  private static final String NON_ALPHANUMERIC_VALUES = "[^a-zA-Z0-9]";
  private static final String LOWERCASE_LETTERS_ONLY = "[a-z]";

  public static final String DASH_JOINER = "-";

  private Util() {
  }

  public static <T> T getValueInApp(String valueKey, App app) {
    return getValueInApp(valueKey, app, true);
  }

  @SuppressWarnings("unchecked")
  public static <T> T getValueInApp(String valueKey, App app, boolean notNull) {
    T value = (T) Objects.requireNonNull(app)
                         .getNode()
                         .tryGetContext(Objects.requireNonNull(valueKey));
    return notNull
           ? Objects.requireNonNull(value, String.format("'%s' cannot be null", valueKey))
           : value;
  }

  @SuppressWarnings("unchecked")
  public static <T> T getValueOrDefault(String valueKey, IConstruct app, T defaultValue) {
    if (Objects.isNull(valueKey) || Objects.isNull(app) || Objects.isNull(app.getNode())) {
      return defaultValue;
    }
    var value = app.getNode().tryGetContext(valueKey);
    return Objects.nonNull(value) ? (T) value : defaultValue;
  }

  public static Environment environmentFrom(String accountId, String region) {
    return Environment.builder()
                      .account(Objects.requireNonNull(accountId))
                      .region(Objects.requireNonNull(region))
                      .build();
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

  public static boolean isNotEmptyNotNull(String value) {
    return value != null && !value.isEmpty() && !"null".equals(value);
  }
}
