package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class UtilTest {
  @Test
  void string() {
    String s1 = UUID.randomUUID().toString(), s2 = UUID.randomUUID().toString();
    assertEquals(s1 + s2, Util.string(s1, s2));
  }
}
