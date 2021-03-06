package org.wcdevs.blog.cdk;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListenerRule;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListenerRuleProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerAction;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCertificate;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCondition;
import software.amazon.awscdk.services.elasticloadbalancingv2.RedirectOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.wcdevs.blog.cdk.Util.DASH_JOINER;
import static org.wcdevs.blog.cdk.Util.joinedString;
import static org.wcdevs.blog.cdk.Util.string;

/**
 * Creates a base network for an application served by an ECS. The network stack will contain a VPC,
 * a configured number of public and isolated subnets (per AZ, default to one for each AZ), an ECS
 * cluster, and an internet-facing load balancer with an HTTP and an optional HTTPS listener. The
 * listeners can be used in other stacks to attach to an ECS service, for instance.
 * <p>
 * The construct exposes some output parameters to be used by other constructs.
 * </p>
 *
 * @see Network#newInstance(Construct, String, ApplicationEnvironment, InputParameters)
 * @see Network#outputParametersFrom(Construct, ApplicationEnvironment)
 * @see Network#outputParametersFrom(Construct, ApplicationEnvironment, int, int, int)
 */
@Setter(AccessLevel.PRIVATE)
@Getter(AccessLevel.PACKAGE)
public final class Network extends Construct {
  // region private constants
  private static final String CLUSTER_NAME = "EcsCluster";

  private static final String PARAM_VPC_ID = "vpcId";
  private static final String PARAM_HTTP_LISTENER_ARN = "httpListenerArn";
  private static final String PARAM_HTTPS_LISTENER_ARN = "httpsListenerArn";
  private static final String PARAM_LOAD_BALANCER_SECURITY_GROUP_ID = "lBSecGroupId";
  private static final String PARAM_LOAD_BALANCER_ARN = "lBArn";
  private static final String PARAM_LOAD_BALANCER_DNS_NAME = "lBDnsName";
  private static final String PARAM_LOAD_BALANCER_CANONICAL_HOSTED_ZONE_ID = "lBCanHostZoneId";
  private static final String PARAM_CLUSTER_NAME = "clusterName";
  private static final String PARAM_AVAILABILITY_ZONES = "availabilityZn";
  private static final String PARAM_ISOLATED_SUBNETS = "isolatedSubNet";
  private static final String PARAM_PUBLIC_SUBNETS = "publicSubNet";
  private static final String PARAM_SSL_CERTIFICATE_ARN = "sslCertificateArn";
  private static final String CONSTRUCT_NAME = "Network";
  // endregion

  // region instance members
  private ApplicationEnvironment applicationEnvironment;
  private IVpc vpc;
  private ICluster ecsCluster;
  private ISecurityGroup loadBalancerSecurityGroup;
  private IApplicationLoadBalancer loadBalancer;
  private IApplicationListener httpListener;
  private IApplicationListener httpsListener;
  // endregion

  // region public constants
  // see https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-ec2-security-group-ingress.html
  public static final String ALL_IP_RANGES_CIDR = "0.0.0.0/0";
  public static final String ALL_IP_PROTOCOLS = "-1";

  /**
   * Number of isolated subnets that will be available for each AZ by default.
   */
  public static final int DEFAULT_NUMBER_OF_ISOLATED_SUBNETS_PER_AZ = 1;
  /**
   * Number of public subnets that will be available for each AZ by default.
   */
  public static final int DEFAULT_NUMBER_OF_PUBLIC_SUBNETS_PER_AZ = 1;
  /**
   * Number of AZ by default.
   */
  public static final int DEFAULT_NUMBER_OF_AZ = 2;
  /**
   * Represents there's no https listener arn.
   */
  public static final String NULL_ARN_VALUE = "null";
  public static final String DEFAULT_APPLICATION_NAME = "default";
  // endregion

  private Network(Construct scope, String id) {
    super(scope, id);
  }

  /**
   * Creates a new {@link ApplicationEnvironment} from a given environment with a default
   * application name.
   * <p>
   * This is the equivalent to:
   * {@code new ApplicationEnvironment(Network.DEFAULT_APPLICATION_NAME, environment)}
   * </p>
   *
   * @param environment Environment name.
   *
   * @return The newly create {@link ApplicationEnvironment}.
   *
   * @see Network#DEFAULT_APPLICATION_NAME
   */
  public static ApplicationEnvironment defaultNetworkApplicationEnvironment(String environment) {
    return new ApplicationEnvironment(DEFAULT_APPLICATION_NAME, environment);
  }

  /**
   * Creates a new {@link Network} from a given scope, network id, environment name, application
   * name and input parameters.
   *
   * @param scope                  Scope in which the network will be defined.
   * @param id                     Network id.
   * @param applicationEnvironment {@link ApplicationEnvironment} instance holding the app and
   *                               environment names.
   * @param inputParameters        Input parameters to build the network.
   *
   * @return The newly create network.
   *
   * @see Network#defaultNetworkApplicationEnvironment(String)
   */
  public static Network newInstance(Construct scope, String id,
                                    ApplicationEnvironment applicationEnvironment,
                                    InputParameters inputParameters) {
    var validScope = Objects.requireNonNull(scope);
    var validId = Objects.requireNonNull(id);
    var validAppEnv = Objects.requireNonNull(applicationEnvironment);
    var envName = validAppEnv.getEnvironmentName();
    var appName = validAppEnv.getApplicationName();
    var validInParams = Objects.requireNonNull(inputParameters);

    var network = new Network(validScope, string(envName, appName, validId));
    network.setApplicationEnvironment(validAppEnv);

    var vpc = vpcFrom(network, validAppEnv, validInParams.getNatGatewayNumber(),
                      validInParams.getNumberOfIsolatedSubnetsPerAZ(),
                      validInParams.getNumberOfPublicSubnetsPerAZ(),
                      validInParams.getMaxAZs());
    network.setVpc(vpc);

    var cluster = clusterFrom(network, vpc, validAppEnv.prefixed(CLUSTER_NAME));
    network.setEcsCluster(cluster);

    var loadBalancerInfo = createLoadBalancer(network, validAppEnv, vpc, validInParams);
    network.setLoadBalancerSecurityGroup(loadBalancerInfo.getSecurityGroup());
    network.setLoadBalancer(loadBalancerInfo.getApplicationLoadBalancer());
    network.setHttpListener(loadBalancerInfo.getHttpListener());
    loadBalancerInfo.getHttpsListener().ifPresent(network::setHttpsListener);

    saveNetworkInfoToParameterStore(network, inputParameters);

    applicationEnvironment.tag(network);

    return network;
  }

  // region utility methods
  private static ICluster clusterFrom(Construct scope, IVpc vpc, String clusterName) {
    return Cluster.Builder.create(scope, "cluster").vpc(vpc).clusterName(clusterName).build();
  }

  private static IVpc vpcFrom(Construct scope, ApplicationEnvironment applicationEnvironment,
                              int natGatewayNumber, int numberOfIsolatedSubnetsPerAZ,
                              int numberOfPublicSubnetsPerAZ, int maxAZs) {
    if (numberOfIsolatedSubnetsPerAZ < 1 || numberOfPublicSubnetsPerAZ < 1 || maxAZs < 1) {
      throw new IllegalArgumentException("Number of private/public subnets and AZs must be >= 1");
    }

    var isolatedSubnetsNamePrefix = applicationEnvironment.prefixed(PARAM_ISOLATED_SUBNETS);
    var isolatedSubnets = subnetsStreamFrom(numberOfIsolatedSubnetsPerAZ, isolatedSubnetsNamePrefix,
                                            SubnetType.PRIVATE_ISOLATED);

    var publicSubnetsNamePrefix = applicationEnvironment.prefixed(PARAM_PUBLIC_SUBNETS);
    var publicSubnets = subnetsStreamFrom(numberOfPublicSubnetsPerAZ, publicSubnetsNamePrefix,
                                          SubnetType.PUBLIC);

    var subnetConfig = Stream.concat(isolatedSubnets, publicSubnets).toList();

    return Vpc.Builder.create(scope, "vpc")
                      .natGateways(natGatewayNumber)
                      .maxAzs(maxAZs)
                      .subnetConfiguration(subnetConfig)
                      .build();
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

  private static LoadBalancerInfo createLoadBalancer(Construct scope,
                                                     ApplicationEnvironment applicationEnvironment,
                                                     IVpc vpc, InputParameters inParams) {
    var securityGroupName = applicationEnvironment.prefixed("lBSecGroup");
    var description = "Public access to the load balancer.";
    var loadBalancerSecurityGroup = SecurityGroup.Builder.create(scope, securityGroupName)
                                                         .securityGroupName(securityGroupName)
                                                         .description(description)
                                                         .vpc(vpc)
                                                         .build();
    cfnSecurityGroupIngressFrom(scope, loadBalancerSecurityGroup.getSecurityGroupId());

    var loadbalancerName = applicationEnvironment.prefixed("loadbalancer");
    var loadBalancer = ApplicationLoadBalancer.Builder.create(scope, "loadbalancer")
                                                      .loadBalancerName(loadbalancerName)
                                                      .vpc(vpc)
                                                      .internetFacing(true)
                                                      .securityGroup(loadBalancerSecurityGroup)
                                                      .build();

    var targetGroupName = applicationEnvironment.prefixed("noop-tGroup");
    var targetGroup = singletonList(
        ApplicationTargetGroup.Builder.create(scope, targetGroupName)
                                      .vpc(vpc)
                                      .port(inParams.getListeningInternalHttpPort())
                                      .protocol(ApplicationProtocol.HTTP)
                                      .targetGroupName(targetGroupName)
                                      .targetType(TargetType.IP)
                                      .build()
                                   );

    var httpListenerProps
        = BaseApplicationListenerProps.builder()
                                      .port(inParams.getListeningExternalHttpPort())
                                      .protocol(ApplicationProtocol.HTTP)
                                      .open(true)
                                      .build();
    var appTargetGroupProps = AddApplicationTargetGroupsProps.builder()
                                                             .targetGroups(targetGroup)
                                                             .build();
    var httpListener = loadBalancer.addListener("httpListener", httpListenerProps);
    httpListener.addTargetGroups("httpTargetGroup", appTargetGroupProps);

    IApplicationListener httpsListener = null;
    if (isArnNotNull(inParams.getSslCertificateArn())) {
      var certificate = ListenerCertificate.fromArn(inParams.getSslCertificateArn());
      var httpsListenerProps
          = BaseApplicationListenerProps.builder()
                                        .port(inParams.getListeningHttpsPort())
                                        .protocol(ApplicationProtocol.HTTPS)
                                        .certificates(singletonList(certificate))
                                        .open(true)
                                        .build();
      httpsListener = loadBalancer.addListener("httpsListener", httpsListenerProps);
      var appsTargetGroupProps = AddApplicationTargetGroupsProps.builder()
                                                                .targetGroups(targetGroup)
                                                                .build();
      httpsListener.addTargetGroups("httpsTargetGroup", appsTargetGroupProps);

      var redirection
          = ListenerAction.redirect(RedirectOptions.builder()
                                                   .port(inParams.getListeningHttpsPortString())
                                                   .build());
      var conditions = List.of(ListenerCondition.pathPatterns(List.of("*")));
      var appListenerRuleProps = ApplicationListenerRuleProps.builder()
                                                             .listener(httpListener)
                                                             .priority(1)
                                                             .conditions(conditions)
                                                             .action(redirection)
                                                             .build();
      new ApplicationListenerRule(scope, "HttpListenerRule", appListenerRuleProps);
    }

    return new LoadBalancerInfo(loadBalancerSecurityGroup, loadBalancer, httpListener,
                                httpsListener);
  }

  private static CfnSecurityGroupIngress cfnSecurityGroupIngressFrom(Construct scope,
                                                                     String lBSecyGroupId) {
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-ec2-security-group-ingress.html
    return CfnSecurityGroupIngress.Builder.create(scope, "ingressToLoadbalancer")
                                          .groupId(lBSecyGroupId)
                                          .cidrIp(ALL_IP_RANGES_CIDR)
                                          .ipProtocol(ALL_IP_PROTOCOLS)
                                          .build();
  }

  private static void saveNetworkInfoToParameterStore(Network network, InputParameters inParams) {
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
                           : NULL_ARN_VALUE;
    createStringParameter(network, PARAM_HTTPS_LISTENER_ARN, httpsListenerArn);
    createStringParameter(network, PARAM_SSL_CERTIFICATE_ARN, inParams.getSslCertificateArn());

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
    if (Objects.nonNull(network) && Objects.nonNull(id)) {
      var valueToStore = Objects.nonNull(stringValue) ? stringValue : NULL_ARN_VALUE;
      var paramName = parameterName(network.getEnvironmentName(), network.getApplicationName(), id);

      StringParameter.Builder.create(network, id)
                             .parameterName(paramName)
                             .stringValue(valueToStore)
                             .build();
    }
  }

  private String getEnvironmentName() {
    return getApplicationEnvironment().getEnvironmentName();
  }

  private String getApplicationName() {
    return getApplicationEnvironment().getApplicationName();
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
    return joinedString(DASH_JOINER, id, elementIndex);
  }

  private static String parameterName(String envName, String appName, String parameterName) {
    return joinedString(DASH_JOINER, envName, appName, CONSTRUCT_NAME, parameterName);
  }
  // endregion

  // region parameters store getters

  public static String getParameter(Construct scope, ApplicationEnvironment applicationEnvironment,
                                    String id) {
    if (Objects.nonNull(applicationEnvironment) && Objects.nonNull(scope) && Objects.nonNull(id)) {
      var environmentName = applicationEnvironment.getEnvironmentName();
      var applicationName = applicationEnvironment.getApplicationName();

      if (Objects.nonNull(environmentName) && Objects.nonNull(applicationName)) {
        var parameterName = parameterName(environmentName, applicationName, id);
        return StringParameter.fromStringParameterName(scope, id, parameterName)
                              .getStringValue();
      }
    }
    return null;
  }

  public static String getVPCId(Construct scope, ApplicationEnvironment applicationEnvironment) {
    return getParameter(scope, applicationEnvironment, PARAM_VPC_ID);
  }

  public static String getClusterName(Construct scope,
                                      ApplicationEnvironment applicationEnvironment) {
    return getParameter(scope, applicationEnvironment, PARAM_CLUSTER_NAME);
  }

  public static String getLoadBalancerSecurityGroupId(Construct scope,
                                                      ApplicationEnvironment appEnvironment) {
    return getParameter(scope, appEnvironment,
                        PARAM_LOAD_BALANCER_SECURITY_GROUP_ID);
  }

  public static String getLoadBalancerArn(Construct scope,
                                          ApplicationEnvironment applicationEnvironment) {
    return getParameter(scope, applicationEnvironment, PARAM_LOAD_BALANCER_ARN);
  }

  public static String getLoadBalancerDnsName(Construct scope,
                                              ApplicationEnvironment applicationEnvironment) {
    return getParameter(scope, applicationEnvironment, PARAM_LOAD_BALANCER_DNS_NAME);
  }

  public static String getLoadBalancerCanonicalHostedZoneId(Construct scope,
                                                            ApplicationEnvironment appEnvironment) {
    return getParameter(scope, appEnvironment, PARAM_LOAD_BALANCER_CANONICAL_HOSTED_ZONE_ID);
  }

  public static String getHttpListenerArn(Construct scope,
                                          ApplicationEnvironment applicationEnvironment) {
    return getParameter(scope, applicationEnvironment, PARAM_HTTP_LISTENER_ARN);
  }

  public static String getHttpsListenerArn(Construct scope,
                                           ApplicationEnvironment applicationEnvironment) {
    return getParameter(scope, applicationEnvironment, PARAM_HTTPS_LISTENER_ARN);
  }

  public static String getSslCertificateArn(Construct scope,
                                            ApplicationEnvironment applicationEnvironment) {
    return getParameter(scope, applicationEnvironment, PARAM_SSL_CERTIFICATE_ARN);
  }

  public static List<String> getParameterList(Construct scope,
                                              ApplicationEnvironment applicationEnvironment,
                                              String id, int totalElements) {
    return IntStream.range(0, totalElements)
                    .mapToObj(i -> getParameter(scope, applicationEnvironment,
                                                idForParameterListItem(id, i)))
                    .filter(Objects::nonNull)
                    .toList();
  }

  public static List<String> getAvailabilityZones(Construct scope,
                                                  ApplicationEnvironment applicationEnvironment,
                                                  int totalAvailabilityZones) {
    return getParameterList(scope, applicationEnvironment,
                            PARAM_AVAILABILITY_ZONES, totalAvailabilityZones);
  }

  public static List<String> getIsolatedSubnets(Construct scope,
                                                ApplicationEnvironment applicationEnvironment,
                                                int totalIsolatedSubnets) {
    return getParameterList(scope, applicationEnvironment, PARAM_ISOLATED_SUBNETS,
                            totalIsolatedSubnets);
  }

  public static List<String> getPublicSubnets(Construct networkScope,
                                              ApplicationEnvironment applicationEnvironment,
                                              int totalPublicSubnets) {
    return getParameterList(networkScope, applicationEnvironment, PARAM_PUBLIC_SUBNETS,
                            totalPublicSubnets);
  }
  // endregion

  // region output parameters

  /**
   * Returns a {@link Network} output parameters generated by a previously constructed
   * {@link Network} instance.
   *
   * @param scope          Scope construct to be provided to the SSM to retrieve the parameters.
   * @param appEnvironment {@link ApplicationEnvironment} instance holding the name of the
   *                       application and the environment where the
   *                       {@link Network} instance was deployed.
   *
   * @return An {@link OutputParameters} instance containing the parameters from the SSM.
   *
   * @see Network#defaultNetworkApplicationEnvironment(String)
   */
  public static OutputParameters outputParametersFrom(Construct scope,
                                                      ApplicationEnvironment appEnvironment) {
    return outputParametersFrom(scope, appEnvironment,
                                DEFAULT_NUMBER_OF_ISOLATED_SUBNETS_PER_AZ,
                                DEFAULT_NUMBER_OF_PUBLIC_SUBNETS_PER_AZ,
                                DEFAULT_NUMBER_OF_AZ);
  }

  /**
   * Returns the network output parameters generated by a construct where the {@link Network}
   * instance was previously deployed.
   *
   * @param networkScope                 Scope where the network instance to retrieve the
   *                                     parameters from the SSM was deployed.
   * @param appEnvironment               {@link ApplicationEnvironment} instance holding the name
   *                                     of the application and the environment where the
   *                                     {@link Network} instance was deployed.
   * @param numberOfIsolatedSubnetsPerAz Number of isolated subnets per AZ in the deployed network.
   * @param numberOfPublicSubnetsPerAz   Number of public subnets per AZ in the deployed network.
   * @param totalAvailabilityZones       Number of total availability zones in the deployed network.
   *
   * @return An {@link OutputParameters} instance containing the parameters from the SSM.
   */
  public static OutputParameters outputParametersFrom(Construct networkScope,
                                                      ApplicationEnvironment appEnvironment,
                                                      int numberOfIsolatedSubnetsPerAz,
                                                      int numberOfPublicSubnetsPerAz,
                                                      int totalAvailabilityZones) {
    var scope = Objects.requireNonNull(networkScope);
    if (numberOfIsolatedSubnetsPerAz < 1 || numberOfPublicSubnetsPerAz < 1
        || totalAvailabilityZones < 1) {
      throw new IllegalArgumentException("The number of isolated and public subnets and the "
                                         + "total availability zones must be greater than 0");
    }

    // subnets will reside in one Availability Zone at a time:
    // https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Subnets.html#vpc-subnet-basics
    int totalIsolatedSubnets = numberOfIsolatedSubnetsPerAz * totalAvailabilityZones;
    int totalPublicSubnets = numberOfPublicSubnetsPerAz * totalAvailabilityZones;

    return new OutputParameters(
        getVPCId(scope, appEnvironment),
        getHttpListenerArn(scope, appEnvironment),
        getHttpsListenerArn(scope, appEnvironment),
        getSslCertificateArn(scope, appEnvironment),
        getLoadBalancerSecurityGroupId(scope, appEnvironment),
        getClusterName(scope, appEnvironment),
        getIsolatedSubnets(scope, appEnvironment, totalIsolatedSubnets),
        getPublicSubnets(scope, appEnvironment, totalPublicSubnets),
        getAvailabilityZones(scope, appEnvironment, totalAvailabilityZones),
        getLoadBalancerArn(scope, appEnvironment),
        getLoadBalancerDnsName(scope, appEnvironment),
        getLoadBalancerCanonicalHostedZoneId(scope, appEnvironment)
    );
  }

  public static boolean isArnNotNull(String arn) {
    return Objects.nonNull(arn)
           && !(arn.isEmpty() || arn.isBlank() || NULL_ARN_VALUE.equalsIgnoreCase(arn));
  }
  // endregion

  // region inner classes

  /**
   * Holds the input parameters to build a new {@link Network}.
   */
  @lombok.Builder
  @Getter(AccessLevel.PACKAGE)
  public static final class InputParameters {
    private String sslCertificateArn;

    private int natGatewayNumber;
    @lombok.Builder.Default
    private int numberOfIsolatedSubnetsPerAZ = DEFAULT_NUMBER_OF_ISOLATED_SUBNETS_PER_AZ;
    @lombok.Builder.Default
    private int numberOfPublicSubnetsPerAZ = DEFAULT_NUMBER_OF_PUBLIC_SUBNETS_PER_AZ;
    @lombok.Builder.Default
    private int maxAZs = DEFAULT_NUMBER_OF_AZ;
    @lombok.Builder.Default
    private int listeningInternalHttpPort = 8080;
    @lombok.Builder.Default
    private int listeningExternalHttpPort = 80;
    @lombok.Builder.Default
    private int listeningHttpsPort = 443;

    String getListeningHttpsPortString() {
      return String.valueOf(listeningHttpsPort);
    }
  }

  /**
   * Holds the output parameters generated by a previously created {@link Network} construct.
   */
  @AllArgsConstructor(access = AccessLevel.PACKAGE)
  @Getter(AccessLevel.PACKAGE)
  public static final class OutputParameters {
    private final String vpcId;
    private final String httpListenerArn;
    @Getter(AccessLevel.NONE)
    private final String httpsListenerArn;
    private final String sslCertificateArn;
    private final String loadbalancerSecurityGroupId;
    private final String ecsClusterName;
    private final List<String> isolatedSubnets;
    private final List<String> publicSubnets;
    private final List<String> availabilityZones;
    private final String loadBalancerArn;
    private final String loadBalancerDnsName;
    private final String loadBalancerCanonicalHostedZoneId;

    public Optional<String> getHttpsListenerArn() {
      return Optional.ofNullable(httpsListenerArn);
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
  // endregion
}
