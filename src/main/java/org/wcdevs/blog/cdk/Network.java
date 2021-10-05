package org.wcdevs.blog.cdk;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Tags;
import software.amazon.awscdk.services.ec2.CfnSecurityGroupIngress;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.ISubnet;
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
import software.amazon.awscdk.services.ssm.StringParameter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.wcdevs.blog.cdk.Util.joinedString;

@Setter(AccessLevel.PRIVATE)
@Getter(AccessLevel.PRIVATE)
public final class Network extends Construct {
  private static final String CLUSTER_NAME = "ecsCluster";

  private static final String ALL_IP_PROTOCOLS = "-1";
  // see https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-ec2-security-group-ingress.html
  private static final String ALL_IP_RANGES_CIDR = "0.0.0.0/0";

  private static final String PARAM_VPC_ID = "vpcId";
  private static final String PARAM_HTTP_LISTENER_ARN = "httpListenerArn";
  private static final String PARAM_HTTPS_LISTENER_ARN = "httpsListenerArn";
  private static final String PARAM_LOAD_BALANCER_SECURITY_GROUP_ID = "lBSecGroupId";
  private static final String PARAM_LOAD_BALANCER_ARN = "lBArn";
  private static final String PARAM_LOAD_BALANCER_DNS_NAME = "lBDnsName";
  private static final String PARAM_LOAD_BALANCER_CANONICAL_HOSTED_ZONE_ID = "lBCanHostedZoneId";
  private static final String PARAM_CLUSTER_NAME = "clusterName";
  private static final String PARAM_AVAILABILITY_ZONES = "availabilityZn";
  private static final String PARAM_ISOLATED_SUBNETS = "isolatedSubNetId";
  private static final String PARAM_PUBLIC_SUBNETS = "publicSubNetId";

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

    saveNetworkInfoToParameterStore(network);

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

  private static void saveNetworkInfoToParameterStore(Network network) {
    createStringParameter(network, PARAM_VPC_ID, network.getVpc().getVpcId());
    createStringParameter(network, PARAM_CLUSTER_NAME, network.getEcsCluster().getClusterName());
    createStringParameter(network, PARAM_LOAD_BALANCER_SECURITY_GROUP_ID,
                          network.getLoadBalancerSecurityGroup().getSecurityGroupId());
    createStringParameter(network, PARAM_LOAD_BALANCER_ARN,
                          network.getLoadBalancer().getLoadBalancerArn());
    createStringParameter(network, PARAM_LOAD_BALANCER_DNS_NAME,
                          network.getLoadBalancer().getLoadBalancerDnsName());
    createStringParameter(network, PARAM_LOAD_BALANCER_CANONICAL_HOSTED_ZONE_ID,
                          network.getLoadBalancer().getLoadBalancerCanonicalHostedZoneId());
    createStringParameter(network, PARAM_HTTP_LISTENER_ARN,
                          network.getHttpListener().getListenerArn());

    var httpsListenerArn = network.getHttpsListener() != null
                           ? network.getHttpsListener().getListenerArn()
                           : "null";
    createStringParameter(network, PARAM_HTTPS_LISTENER_ARN, httpsListenerArn);

    createStringListParameter(network, PARAM_AVAILABILITY_ZONES,
                              network.getVpc().getAvailabilityZones(),
                              Function.identity());

    createStringListParameter(network, PARAM_ISOLATED_SUBNETS,
                              network.getVpc().getIsolatedSubnets(),
                              ISubnet::getSubnetId);

    createStringListParameter(network, PARAM_PUBLIC_SUBNETS,
                              network.getVpc().getPublicSubnets(),
                              ISubnet::getSubnetId);
  }

  private static void createStringParameter(Network network, String id, String stringValue) {
    StringParameter.Builder.create(network, id)
                           .parameterName(parameterName(network.getEnvironmentName(), id))
                           .stringValue(stringValue)
                           .build();
  }

  private static <T> void createStringListParameter(Network network, String id,
                                                    List<? extends T> values,
                                                    Function<T, String> mapper) {
    // StringListParameter is currently broken: https://github.com/aws/aws-cdk/issues/3586
    for (var i = 0; i < values.size(); i++) {
      createStringParameter(network, idForParameterListItem(id, i), mapper.apply(values.get(i)));
    }
  }

  private static String idForParameterListItem(String id, int elementIndex) {
    return joinedString("-", id, elementIndex);
  }

  private static String parameterName(String environmentName, String parameterName) {
    return environmentName + "-Network-" + parameterName;
  }

  // region parameters store getters
  public static String getParameter(Network network, String id) {
    var parameterName = parameterName(network.getEnvironmentName(), id);
    return StringParameter.fromStringParameterName(network, id, parameterName).getStringValue();
  }

  public static String getVPCId(Network network) {
    return getParameter(network, PARAM_VPC_ID);
  }

  public static String getClusterName(Network network) {
    return getParameter(network, PARAM_CLUSTER_NAME);
  }

  public static String getLoadBalancerSecurityGroupId(Network network) {
    return getParameter(network, PARAM_LOAD_BALANCER_SECURITY_GROUP_ID);
  }

  public static String getLoadBalancerArn(Network network) {
    return getParameter(network, PARAM_LOAD_BALANCER_ARN);
  }

  public static String getLoadBalancerDnsName(Network network) {
    return getParameter(network, PARAM_LOAD_BALANCER_DNS_NAME);
  }

  public static String getLoadBalancerCanonicalHostedZoneId(Network network) {
    return getParameter(network, PARAM_LOAD_BALANCER_CANONICAL_HOSTED_ZONE_ID);
  }

  public static String getHttpListenerArn(Network network) {
    return getParameter(network, PARAM_HTTP_LISTENER_ARN);
  }

  public static String getHttpsListenerArn(Network network) {
    return getParameter(network, PARAM_HTTPS_LISTENER_ARN);
  }

  public static List<String> getParameterList(Network network, String id, int totalElements) {
    return IntStream.range(0, totalElements)
                    .mapToObj(i -> getParameter(network, idForParameterListItem(id, i)))
                    .collect(Collectors.toUnmodifiableList());
  }

  public static List<String> getAvailabilityZones(Network network) {
    return getParameterList(network, PARAM_AVAILABILITY_ZONES,
                            network.getVpc().getAvailabilityZones().size());
  }

  public static List<String> getIsolatedSubnets(Network network) {
    return getParameterList(network, PARAM_ISOLATED_SUBNETS,
                            network.getVpc().getIsolatedSubnets().size());
  }

  public static List<String> getPublicSubnets(Network network) {
    return getParameterList(network, PARAM_PUBLIC_SUBNETS,
                            network.getVpc().getIsolatedSubnets().size());
  }
  // endregion

  public static InputParameters newInputParameters() {
    return new InputParameters();
  }

  public static InputParameters newInputParameters(String sslCertificate) {
    return new InputParameters(sslCertificate);
  }

  @Getter(AccessLevel.PACKAGE)
  public static final class InputParameters {
    private final String sslCertificateArn;

    @Setter(AccessLevel.PACKAGE)
    private int natGatewayNumber;

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
