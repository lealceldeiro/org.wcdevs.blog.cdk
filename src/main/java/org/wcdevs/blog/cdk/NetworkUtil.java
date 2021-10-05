package org.wcdevs.blog.cdk;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.services.ec2.CfnSecurityGroupIngress;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;

import static java.util.Arrays.asList;

final class NetworkUtil {
  private NetworkUtil() {
  }

  static IVpc vpcFrom(Construct scope, String isolatedSubnetName, String publicSubnetName,
                      int natGatewayNumber, int maxAZs) {
    var privateSubnet = subnetFrom(isolatedSubnetName, SubnetType.ISOLATED);
    var publicSubnet = subnetFrom(publicSubnetName, SubnetType.PUBLIC);

    return Vpc.Builder.create(scope, "vpc")
                      .natGateways(natGatewayNumber)
                      .maxAzs(maxAZs)
                      .subnetConfiguration(asList(publicSubnet, privateSubnet))
                      .build();
  }

  private static SubnetConfiguration subnetFrom(String name, SubnetType subnetType) {
    return SubnetConfiguration.builder().subnetType(subnetType).name(name).build();
  }

  static CfnSecurityGroupIngress cfnSecurityGroupIngressFrom(Construct scope,
                                                             String loadBalancerSecurityGroupId,
                                                             String cidrIp, String ipProtocol) {
    return CfnSecurityGroupIngress.Builder.create(scope, "ingressToLoadbalancer")
                                          .groupId(loadBalancerSecurityGroupId)
                                          .cidrIp(cidrIp)
                                          .ipProtocol(ipProtocol)
                                          .build();
  }
}
