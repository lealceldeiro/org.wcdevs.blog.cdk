package org.wcdevs.blog.cdk;

import org.mockito.invocation.InvocationOnMock;
import software.amazon.awscdk.core.ICfnResourceOptions;
import software.amazon.awscdk.services.cognito.OAuthScope;
import software.amazon.awscdk.services.cognito.UserPoolClientIdentityProvider;
import software.amazon.awscdk.services.ec2.ISubnet;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.lambda.Runtime;

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
    if (type != null) {
      if (String.class.isAssignableFrom(type)) {
        return "mockedKernelData";
      } else if (ICfnResourceOptions.class.isAssignableFrom(type)) {
        return mock(ICfnResourceOptions.class);
      } else if (Vpc.class.isAssignableFrom(type)) {
        return mock(Vpc.class);
      } else if (ISubnet.class.isAssignableFrom(type)) {
        return mock(ISubnet.class);
      } else if (OAuthScope.class.isAssignableFrom(type)) {
        return mock(OAuthScope.class);
      } else if (UserPoolClientIdentityProvider.class.isAssignableFrom(type)) {
        return mock(UserPoolClientIdentityProvider.class);
      } else if (Runtime.class.isAssignableFrom(type)) {
        return mock(Runtime.class);
      }
      log.warning("unmatched 'type' in kernel response mock");
    }
    log.warning("'type' and 'elementType' are null");
    return "defaultMockedKernelData";
  }
}
