package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Tags;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ApplicationEnvironmentTest {
  private static String randomString() {
    return UUID.randomUUID().toString();
  }

  @Test
  void testToString() {
    var app = randomString();
    var env = randomString();

    var appEnv = new ApplicationEnvironment(app, env);
    assertEquals(Util.sanitize(Util.joinedString("-", app, env)), appEnv.toString());
  }

  @Test
  void prefixed() {
    var app = randomString();
    var env = randomString();
    var value = randomString();

    var appEnv = new ApplicationEnvironment(app, env);
    assertEquals(Util.joinedString("-", appEnv, value), appEnv.prefixed(value));
  }

  @Test
  void tag() {
    var construct = mock(Construct.class);
    var app = randomString();
    var env = randomString();
    var appEnv = new ApplicationEnvironment(app, env);

    var tags = mock(Tags.class);
    doNothing().when(tags).add(any(), any());

    try (var mockedTags = mockStatic(Tags.class)) {
      mockedTags.when(() -> Tags.of(any())).thenReturn(tags);

      appEnv.tag(construct);

      verify(tags, times(1)).add("environment", env);
      verify(tags, times(1)).add("application", app);
    }
  }

  @Test
  void getApplicationName() {
    var app = randomString();
    var env = randomString();
    var appEnv = new ApplicationEnvironment(app, env);

    assertEquals(app, appEnv.getApplicationName());
  }

  @Test
  void getEnvironmentName() {

    var app = randomString();
    var env = randomString();
    var appEnv = new ApplicationEnvironment(app, env);

    assertEquals(env, appEnv.getEnvironmentName());
  }
}
