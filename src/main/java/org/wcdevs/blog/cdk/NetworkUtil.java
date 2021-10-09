package org.wcdevs.blog.cdk;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.services.ec2.CfnSecurityGroupIngress;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

final class NetworkUtil {
  private NetworkUtil() {
  }

  static IVpc vpcFrom(Construct scope,
                      int numberOfIsolatedSubnetsPerAZ, int numberOfPublicSubnetsPerAZ,
                      String isolatedSubnetsNamePrefix, String publicSubnetsNamePrefix,
                      int natGatewayNumber, int maxAZs) {
    if (numberOfIsolatedSubnetsPerAZ < 1 || numberOfPublicSubnetsPerAZ < 1) {
      throw new IllegalArgumentException("The number of private and public subnets must be >= 1");
    }

    var isolatedSubnets = subnetsStreamFrom(numberOfIsolatedSubnetsPerAZ, isolatedSubnetsNamePrefix,
                                            SubnetType.ISOLATED);
    var publicSubnets = subnetsStreamFrom(numberOfPublicSubnetsPerAZ, publicSubnetsNamePrefix,
                                          SubnetType.PUBLIC);
    var subnetConfig = Stream.concat(isolatedSubnets, publicSubnets).collect(toList());

    return Vpc.Builder.create(scope, "vpc").natGateways(natGatewayNumber).maxAzs(maxAZs)
                      .subnetConfiguration(subnetConfig).build();
  }

  private static Stream<SubnetConfiguration> subnetsStreamFrom(int numberOfIsolatedSubnets,
                                                               String subnetsNamePrefix,
                                                               SubnetType subnetType) {
    return IntStream.range(0, numberOfIsolatedSubnets)
                    .mapToObj(i -> subnetFrom(subnetsNamePrefix + i, subnetType));
  }

  private static SubnetConfiguration subnetFrom(String name, SubnetType subnetType) {
    return SubnetConfiguration.builder().subnetType(subnetType).name(name).build();
  }

  static CfnSecurityGroupIngress cfnSecurityGroupIngressFrom(Construct scope, String lBSecyGroupId,
                                                             String cidrIp, String ipProtocol) {
    return CfnSecurityGroupIngress.Builder.create(scope, "ingressToLoadbalancer")
                                          .groupId(lBSecyGroupId).cidrIp(cidrIp)
                                          .ipProtocol(ipProtocol).build();
  }
}
