package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class UtilTest {
  @Test
  void string() {
    String s1 = randomString(), s2 = UUID.randomUUID().toString();
    assertEquals(s1 + s2, Util.string(s1, s2));
  }

  private String randomString() {
    return UUID.randomUUID().toString();
  }

  @Test
  void joinedString() {
    String s1 = randomString(), s2 = randomString(), joiner = randomString();
    assertEquals(s1 + joiner + s2, Util.joinedString(joiner, s1, s2));
  }
}
