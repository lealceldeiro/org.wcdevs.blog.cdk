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
  private static final String ALL_IP_PROTOCOLS = "-1";
  // see https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-ec2-security-group-ingress.html
  private static final String ALL_IP_RANGES_CIDR = "0.0.0.0/0";

  private String environmentName;
  private IVpc vpc;
  private ICluster ecsCluster;
  private ISecurityGroup loadBalancerSecurityGroup;
  private IApplicationLoadBalancer loadBalancer;
  private IApplicationListener httpListener;
  private IApplicationListener httpsListener;

  private Network(Construct scope, String id) {
    super(scope, id);
  }

  /**
   * Creates a new {@link Network} from a given scope, network id, environment name and
   * input parameters.
   *
   * @param scope           Scope in which the network will be defined.
   * @param id              Network id.
   * @param environmentName Environment name.
   * @param inputParameters Input parameters to build the network.
   *
   * @return The newly create network.
   */
  public static Network newInstance(Construct scope, String id, String environmentName,
                                    InputParameters inputParameters) {
    var validScope = Objects.requireNonNull(scope);
    var validId = Objects.requireNonNull(id);
    var envName = Objects.requireNonNull(environmentName);
    var validInParams = Objects.requireNonNull(inputParameters);

    var network = new Network(validScope, validId);
    network.setEnvironmentName(envName);

    var vpc = vpcFrom(network, envName, validInParams.getNatGatewayNumber(),
                      validInParams.getMaxAZs());
    network.setVpc(vpc);

    var cluster = clusterFrom(network, vpc, joinedString("-", envName, CLUSTER_NAME));
    network.setEcsCluster(cluster);

    var loadBalancerInfo = createLoadBalancer(network, envName, vpc,
                                              validInParams.getListeningInternalPort(),
                                              validInParams.getListeningInternalPort(),
                                              validInParams.getSslCertificateArn());
    network.setLoadBalancerSecurityGroup(loadBalancerInfo.getSecurityGroup());
    network.setLoadBalancer(loadBalancerInfo.getApplicationLoadBalancer());
    network.setHttpListener(loadBalancerInfo.getHttpListener());
    loadBalancerInfo.getHttpsListener().ifPresent(network::setHttpsListener);

    // TODO: Create output parameters

    Tags.of(network).add("environment", envName);

    return network;
  }

  private static ICluster clusterFrom(Construct scope, IVpc vpc, String clusterName) {
    return Cluster.Builder.create(scope, "cluster").vpc(vpc).clusterName(clusterName).build();
  }

  private static IVpc vpcFrom(Construct scope, String environmentName, int natGatewayNumber,
                              int maxAZs) {
    var publicSubnetName = joinedString("-", environmentName, "publicSubnet");
    var publicSubnet = subnetFrom(publicSubnetName, SubnetType.PUBLIC);

    var isolatedSubnetName = joinedString("-", environmentName, "isolatedSubnet");
    var privateSubnet = subnetFrom(isolatedSubnetName, SubnetType.ISOLATED);

    return Vpc.Builder.create(scope, "vpc")
                      .natGateways(natGatewayNumber)
                      .maxAzs(maxAZs)
                      .subnetConfiguration(asList(publicSubnet, privateSubnet))
                      .build();
  }

  private static SubnetConfiguration subnetFrom(String name, SubnetType subnetType) {
    return SubnetConfiguration.builder().subnetType(subnetType).name(name).build();
  }

  private static LoadBalancerInfo createLoadBalancer(Construct scope, String environmentName,
                                                     IVpc vpc, int internalPort, int externalPort,
                                                     String sslCertificateArn) {
    var securityGroupName = joinedString("-", environmentName, "loadbalancerSecurityGroup");
    var description = "Public access to the load balancer.";
    var loadBalancerSecurityGroup = SecurityGroup.Builder.create(scope, "loadbalancerSecurityGroup")
                                                         .securityGroupName(securityGroupName)
                                                         .description(description)
                                                         .vpc(vpc)
                                                         .build();
    CfnSecurityGroupIngress.Builder.create(scope, "ingressToLoadbalancer")
                                   .groupId(loadBalancerSecurityGroup.getSecurityGroupId())
                                   .cidrIp(ALL_IP_RANGES_CIDR)
                                   .ipProtocol(ALL_IP_PROTOCOLS)
                                   .build();

    var loadbalancerName = joinedString("-", environmentName, "loadbalancer");
    var loadBalancer = ApplicationLoadBalancer.Builder.create(scope, "loadbalancer")
                                                      .loadBalancerName(loadbalancerName)
                                                      .vpc(vpc)
                                                      .internetFacing(true)
                                                      .securityGroup(loadBalancerSecurityGroup)
                                                      .build();

    var targetGroupName = joinedString("-", environmentName, "no-op-targetGroup");
    var targetGroup = singletonList(
        ApplicationTargetGroup.Builder.create(scope, "targetGroup")
                                      .vpc(vpc)
                                      .port(internalPort)
                                      .protocol(ApplicationProtocol.HTTP)
                                      .targetGroupName(targetGroupName)
                                      .targetType(TargetType.IP)
                                      .build()
                                   );

    var httpListenerProps = BaseApplicationListenerProps.builder()
                                                        .port(externalPort)
                                                        .protocol(ApplicationProtocol.HTTP)
                                                        .open(true)
                                                        .build();
    var appTargetGroupProps = AddApplicationTargetGroupsProps.builder()
                                                             .targetGroups(targetGroup)
                                                             .build();
    var httpListener = loadBalancer.addListener("httpListener", httpListenerProps);
    httpListener.addTargetGroups("http", appTargetGroupProps);

    IApplicationListener httpsListener = null;
    if (sslCertificateArn != null) {
      var certificate = ListenerCertificate.fromArn(sslCertificateArn);
      var httpsListenerProps
          = BaseApplicationListenerProps.builder()
                                        .port(443)
                                        .protocol(ApplicationProtocol.HTTPS)
                                        .certificates(singletonList(certificate))
                                        .open(true)
                                        .build();
      httpsListener = loadBalancer.addListener("httpsListener", httpsListenerProps);
      var appsTargetGroupProps = AddApplicationTargetGroupsProps.builder()
                                                                .targetGroups(targetGroup)
                                                                .build();
      httpsListener.addTargetGroups("https", appsTargetGroupProps);
    }

    return new LoadBalancerInfo(loadBalancerSecurityGroup, loadBalancer, httpListener,
                                httpsListener);
  }

  @Getter(AccessLevel.PACKAGE)
  public static final class InputParameters {
    private final String sslCertificateArn;

    @Setter(AccessLevel.PACKAGE)
    private int natGatewayNumber = 0;

    @Setter(AccessLevel.PACKAGE)
    private int maxAZs = 2;

    @Setter(AccessLevel.PACKAGE)
    private int listeningInternalPort = 8080;

    @Setter(AccessLevel.PACKAGE)
    private int listeningExternalPort = 80;

    InputParameters() {
      this.sslCertificateArn = null;
    }

    InputParameters(String sslCertificateArn) {
      this.sslCertificateArn = sslCertificateArn;
    }
  }

  @RequiredArgsConstructor
  @Getter(AccessLevel.PACKAGE)
  private static final class LoadBalancerInfo {
    private final ISecurityGroup securityGroup;
    private final IApplicationLoadBalancer applicationLoadBalancer;
    private final IApplicationListener httpListener;
    @Getter(AccessLevel.NONE)
    private final IApplicationListener httpsListener;

    private Optional<IApplicationListener> getHttpsListener() {
      return Optional.ofNullable(httpsListener);
    }
  }
}
