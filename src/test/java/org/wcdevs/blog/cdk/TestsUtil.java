package org.wcdevs.blog.cdk;

import org.mockito.invocation.InvocationOnMock;
import software.amazon.awscdk.core.ICfnResourceOptions;
import software.amazon.awscdk.services.ec2.ISubnet;
import software.amazon.awscdk.services.ec2.Vpc;

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
    String type = invocation.getArgument(1).toString();
    if (type.contains("SimpleNativeType")) {
      return "simpleMockData";
    } else if (type.contains("ListNativeType")) {
      return List.of("listItem");
    }
    log.warning("unmatched 'type'");
    return "defaultMockData";
  }

  static Object kernelAnswer(InvocationOnMock invocation) {
    Class<?> type = TestsReflectionUtil.getField(invocation.getArgument(2), "type");
    ObjectType objectType = ObjectType.SIMPLE;
    if (type == null) {
      var elementType = TestsReflectionUtil.getField(invocation.getArgument(2), "elementType");
      type = TestsReflectionUtil.getField(elementType, "type");
      objectType = ObjectType.LIST;
    }
    return kernelAnswerFrom(type, objectType);
  }

  private static Object kernelAnswerFrom(Class<?> type, ObjectType objectType) {
    var simpleObject = kernelObjectFrom(type);
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

  private static Object kernelObjectFrom(Class<?> type) {
    if (type != null) {
      if (String.class.isAssignableFrom(type)) {
        return "mockedKernelData";
      } else if (ICfnResourceOptions.class.isAssignableFrom(type)) {
        return mock(ICfnResourceOptions.class);
      } else if (Vpc.class.isAssignableFrom(type)) {
        return mock(Vpc.class);
      } else if (ISubnet.class.isAssignableFrom(type)) {
        return mock(ISubnet.class);
      }
      log.warning("unmatched 'type' in kernel response mock");
    }
    log.warning("'type' and 'elementType' are null");
    return "defaultMockedKernelData";
  }
}
