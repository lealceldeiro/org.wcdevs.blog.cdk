package org.wcdevs.blog.cdk;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Tags;
import software.amazon.awscdk.services.ec2.CfnSecurityGroupIngress;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ICluster;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetGroupsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.IListenerCertificate;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCertificate;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;

import java.util.Objects;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.wcdevs.blog.cdk.Util.joinedString;

@Setter(AccessLevel.PRIVATE)
public final class Network extends Construct {
  private static final String CLUSTER_NAME = "ecsCluster";

  private String environmentName;
  private IVpc vpc;
  private ICluster ecsCluster;
  private ISecurityGroup loadBalancerSecurityGroup;
  private IApplicationLoadBalancer loadBalancer;
  private IApplicationListener httpListener;
  private Optional<IApplicationListener> httpsListener;

  private Network(Construct scope, String id) {
    super(scope, id);
  }

  public static Network newInstance(Construct scopeArg, String idArg, String envName,
                                    InputParameters inputParameters) {
    Construct scope = Objects.requireNonNull(scopeArg);
    String id = Objects.requireNonNull(idArg);
    String environmentName = Objects.requireNonNull(envName);

    Network network = new Network(scope, id);
    network.setEnvironmentName(environmentName);

    IVpc vpc = vpcFrom(network, environmentName, inputParameters.getNatGatewayNumber(),
                       inputParameters.getMaxAZs());
    network.setVpc(vpc);

    ICluster cluster = clusterFrom(network, vpc, joinedString("-", environmentName, CLUSTER_NAME));
    network.setEcsCluster(cluster);

    LoadBalancerInfo lBalancerInfo = createLoadBalancer(network, environmentName, vpc,
                                                        inputParameters.getListeningInternalPort(),
                                                        inputParameters.getListeningInternalPort(),
                                                        inputParameters.getSslCertificateArn());
    network.setLoadBalancerSecurityGroup(lBalancerInfo.getSecurityGroup());
    network.setLoadBalancer(lBalancerInfo.getApplicationLoadBalancer());
    network.setHttpListener(lBalancerInfo.getHttpListener());
    network.setHttpsListener(lBalancerInfo.getHttpsListener());

    // TODO: Create output parameters

    Tags.of(network).add("environment", environmentName);

    return network;
  }

  private static ICluster clusterFrom(Construct scope, IVpc vpc, String clusterName) {
    return Cluster.Builder.create(scope, "cluster").vpc(vpc).clusterName(clusterName).build();
  }

  private static IVpc vpcFrom(Construct scope, String environmentName, int natGatewayNumber,
                              int maxAZs) {
    String publicSubnetName = joinedString("-", environmentName, "publicSubnet");
    SubnetConfiguration publicSubnet = subnetFrom(publicSubnetName, SubnetType.PUBLIC);

    String isolatedSubnetName = joinedString("-", environmentName, "isolatedSubnet");
    SubnetConfiguration privateSubnet = subnetFrom(isolatedSubnetName, SubnetType.ISOLATED);

    return Vpc.Builder.create(scope, "vpc")
                      .natGateways(natGatewayNumber)
                      .maxAzs(maxAZs)
                      .subnetConfiguration(asList(publicSubnet, privateSubnet))
                      .build();
  }

  private static SubnetConfiguration subnetFrom(String name, SubnetType subnetType) {
    return SubnetConfiguration.builder()
                              .subnetType(subnetType)
                              .name(name)
                              .build();
  }

  private static LoadBalancerInfo createLoadBalancer(Construct scope, String environmentName,
                                                     IVpc vpc, int internalPort, int externalPort,
                                                     Optional<String> sslCertificateArn) {
    String secGroupName = joinedString("-", environmentName, "loadbalancerSecurityGroup");
    String description = "Public access to the load balancer.";
    ISecurityGroup lbSecGroup = SecurityGroup.Builder.create(scope, "loadbalancerSecurityGroup")
                                                     .securityGroupName(secGroupName)
                                                     .description(description)
                                                     .vpc(vpc)
                                                     .build();
    CfnSecurityGroupIngress.Builder.create(scope, "ingressToLoadbalancer")
                                   .groupId(lbSecGroup.getSecurityGroupId())
                                   .cidrIp("0.0.0.0/0")
                                   .ipProtocol("-1")
                                   .build();

    String loadbalancerName = joinedString("-", environmentName, "loadbalancer");
    IApplicationLoadBalancer loadBalancer
        = ApplicationLoadBalancer.Builder.create(scope, "loadbalancer")
                                         .loadBalancerName(loadbalancerName)
                                         .vpc(vpc)
                                         .internetFacing(true)
                                         .securityGroup(lbSecGroup)
                                         .build();

    String targetGroupName = joinedString("-", environmentName, "no-op-targetGroup");
    IApplicationTargetGroup targetGroup
        = ApplicationTargetGroup.Builder.create(scope, "targetGroup")
                                        .vpc(vpc)
                                        .port(internalPort)
                                        .protocol(ApplicationProtocol.HTTP)
                                        .targetGroupName(targetGroupName)
                                        .targetType(TargetType.IP)
                                        .build();

    BaseApplicationListenerProps httpListenerProps
        = BaseApplicationListenerProps.builder()
                                      .port(externalPort)
                                      .protocol(ApplicationProtocol.HTTP)
                                      .open(true)
                                      .build();
    AddApplicationTargetGroupsProps appTargetGroupProps
        = AddApplicationTargetGroupsProps.builder()
                                         .targetGroups(singletonList(targetGroup))
                                         .build();
    IApplicationListener httpListener = loadBalancer.addListener("httpListener",
                                                                 httpListenerProps);
    httpListener.addTargetGroups("http", appTargetGroupProps);

    Optional<IApplicationListener> optionalHttpsListener = Optional.empty();
    if (sslCertificateArn.isPresent()) {
      IListenerCertificate certificate = ListenerCertificate.fromArn(sslCertificateArn.get());
      BaseApplicationListenerProps httpsListenerProps
          = BaseApplicationListenerProps.builder()
                                        .port(443)
                                        .protocol(ApplicationProtocol.HTTPS)
                                        .certificates(singletonList(certificate))
                                        .open(true)
                                        .build();
      IApplicationListener httpsListener = loadBalancer.addListener("httpsListener",
                                                                    httpsListenerProps);
      AddApplicationTargetGroupsProps appsTargetGroupProps
          = AddApplicationTargetGroupsProps.builder()
                                           .targetGroups(singletonList(targetGroup))
                                           .build();
      httpsListener.addTargetGroups("https", appsTargetGroupProps);

      optionalHttpsListener = Optional.of(httpsListener);
    }

    return new LoadBalancerInfo(lbSecGroup, loadBalancer, httpListener, optionalHttpsListener);
  }

  @Getter(AccessLevel.PACKAGE)
  public static final class InputParameters {
    private Optional<String> sslCertificateArn;

    @Setter(AccessLevel.PACKAGE)
    private int natGatewayNumber = 0;

    @Setter(AccessLevel.PACKAGE)
    private int maxAZs = 2;

    @Setter(AccessLevel.PACKAGE)
    private int listeningInternalPort = 8080;

    @Setter(AccessLevel.PACKAGE)
    private int listeningExternalPort = 80;

    InputParameters() {
      this.sslCertificateArn = Optional.empty();
    }

    InputParameters(String sslCertificateArn) {
      this.sslCertificateArn = Optional.of(sslCertificateArn);
    }
  }

  @RequiredArgsConstructor
  @Getter(AccessLevel.PACKAGE)
  private static final class LoadBalancerInfo {
    private final ISecurityGroup securityGroup;
    private final IApplicationLoadBalancer applicationLoadBalancer;
    private final IApplicationListener httpListener;
    private final Optional<IApplicationListener> httpsListener;
  }
}
