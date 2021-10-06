package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;

final class ReflectionUtil {
  private ReflectionUtil() {
  }

  @SuppressWarnings("unchecked")
  static <T> T getField(Object o, String fieldName) {
    Assertions.assertNotNull(o);
    Assertions.assertNotNull(fieldName);
    try {
      Field field = o.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return (T) field.get(o);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      return null;
    }
  }

  static void setField(Object o, String fieldName, Object fieldValue) {
    Assertions.assertNotNull(o);
    Assertions.assertNotNull(fieldName);
    try {
      Field field = o.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(o, fieldValue);
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
  }
}
