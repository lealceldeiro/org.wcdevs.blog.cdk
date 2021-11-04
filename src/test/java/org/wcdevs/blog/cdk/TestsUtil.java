package org.wcdevs.blog.cdk;

import org.mockito.invocation.InvocationOnMock;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;

final class TestsUtil {
  private enum ObjectType {
    SIMPLE, LIST, MAP
  }

  private static final Logger log = Logger.getLogger(TestsUtil.class.getName());

  private TestsUtil() {
  }

  static Object jsiiObjectMapperAnswer(InvocationOnMock invocation) {
    return answerForInvocationWithIndex(invocation, 1);
  }

  static Object kernelAnswer(InvocationOnMock invocation) {
    return answerForInvocationWithIndex(invocation, 2);
  }

  private static Object answerForInvocationWithIndex(InvocationOnMock invocation, int argIndex) {
    Class<?> type = TestsReflectionUtil.getField(invocation.getArgument(argIndex), "type");
    ObjectType objectType = ObjectType.SIMPLE;
    if (type == null) {
      var elType = TestsReflectionUtil.getField(invocation.getArgument(argIndex), "elementType");
      type = TestsReflectionUtil.getField(elType, "type");
      objectType = ObjectType.LIST;
    }
    return answerFrom(type, objectType);
  }

  private static Object answerFrom(Class<?> type, ObjectType objectType) {
    var simpleObject = objectFrom(type);
    switch (objectType) {
      case LIST:
        return List.of(simpleObject);
      case MAP:
        return Map.of("", simpleObject);
      case SIMPLE:
      default:
        // fall through
    }
    return simpleObject;
  }

  private static Object objectFrom(Class<?> type) {
    return type == null || String.class.isAssignableFrom(type) ? "mockedKernelData" : mock(type);
  }
}
