package org.wcdevs.blog.cdk;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

final class Util {
  private Util() {}

  static String string(Object ... values) {
    return Arrays.stream(values)
                 .filter(Objects::nonNull)
                 .map(Object::toString)
                 .collect(Collectors.joining());
  }

  static String joinedString(String joiner, Object... values) {
    return Arrays.stream(values)
                 .filter(Objects::nonNull)
                 .map(Object::toString)
                 .collect(Collectors.joining(joiner));
  }
}
