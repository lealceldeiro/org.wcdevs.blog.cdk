package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import software.amazon.awscdk.core.Construct;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class NetworkUtilTest {
  @Test
  void vpcFromWithIllegalNumberOfIsolatedSubnetsPerAZ() {
    testVpcFromThrowsWithIllegalArgs(0, 1, 1, 1);
  }

  @Test
  void vpcFromWithIllegalNumberOfPublicSubnetsPerAZ() {
    testVpcFromThrowsWithIllegalArgs(1, 0, 1, 1);
  }

  @Test
  void vpcFromWithIllegalMaxAZs() {
    testVpcFromThrowsWithIllegalArgs(1, 1, 1, 0);
  }

  @Test
  void vpcFromWithIllegalNatGatewayNumber() {
    testVpcFromThrowsWithIllegalArgs(1, 1, 0, 1);
  }

  void testVpcFromThrowsWithIllegalArgs(int numberOfIsolatedSubnetsPerAZ,
                                        int numberOfPublicSubnetsPerAZ,
                                        int natGatewayNumber,
                                        int maxAZs) {
    String random = UUID.randomUUID().toString();
    Executable executable = () -> NetworkUtil.vpcFrom(mock(Construct.class),
                                                      numberOfIsolatedSubnetsPerAZ,
                                                      numberOfPublicSubnetsPerAZ,
                                                      random, random,
                                                      natGatewayNumber,
                                                      maxAZs);
    assertThrows(IllegalArgumentException.class, executable);
  }
}
