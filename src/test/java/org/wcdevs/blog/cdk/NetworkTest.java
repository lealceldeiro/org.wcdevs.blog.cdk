package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Tags;
import software.amazon.awscdk.services.ec2.ISubnet;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCertificate;
import software.amazon.awscdk.services.ssm.IStringParameter;
import software.amazon.awscdk.services.ssm.StringParameter;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class NetworkTest {
  @FunctionalInterface
  interface TriFunction<A, B, C, R> {
    R apply(A argument1, B argument2, C argument3);

    default <V> TriFunction<A, B, C, V> andThen(Function<? super R, ? extends V> after) {
      Objects.requireNonNull(after);
      return (A arg1, B arg2, C arg3) -> after.apply(apply(arg1, arg2, arg3));
    }
  }

  private static final Random RANDOM = new SecureRandom();
  private static final int RANDOM_UPPER_BOUND = 9000;

  private static String randomString() {
    return UUID.randomUUID().toString();
  }

  @Test
  void newInstanceWithCertificate() {
    testNewInstance(randomString(), 1, 1, 1, 1);
  }

  @Test
  void newInstanceWithoutCertificate() {
    testNewInstance(null, 1, 1, 1, 1);
  }

  void testNewInstance(String sslCertificateArg, int numberOfIsolatedSubnetsPerAZ,
                       int numberPublicSubnetsPerAZ, int natGatewayNumber, int maxAZs) {
    StaticallyMockedCdk.executeTest(() -> {
      var subnets = Collections.singletonList(mock(ISubnet.class));
      when(subnets.get(0).getSubnetId()).thenReturn(randomString());

      var vpcMock = mock(IVpc.class);
      when(vpcMock.getVpcId()).thenReturn(randomString());
      when(vpcMock.getAvailabilityZones()).thenReturn(Collections.singletonList(randomString()));
      when(vpcMock.getIsolatedSubnets()).thenReturn(subnets);
      when(vpcMock.getPublicSubnets()).thenReturn(subnets);

      var subnetConfigurationBuilderMock = mock(SubnetConfiguration.Builder.class);
      when(subnetConfigurationBuilderMock.subnetType(any()))
          .thenReturn(subnetConfigurationBuilderMock);
      when(subnetConfigurationBuilderMock.name(any()))
          .thenReturn(subnetConfigurationBuilderMock);
      when(subnetConfigurationBuilderMock.build())
          .thenReturn(mock(SubnetConfiguration.class));

      var clusterMock = mock(Cluster.class);
      when(clusterMock.getClusterName()).thenReturn(randomString());

      var clusterBuilderMock = mock(Cluster.Builder.class);
      when(clusterBuilderMock.vpc(any())).thenReturn(clusterBuilderMock);
      when(clusterBuilderMock.clusterName(any())).thenReturn(clusterBuilderMock);
      when(clusterBuilderMock.build()).thenReturn(clusterMock);

      var secGroupMock = mock(SecurityGroup.class);
      when(secGroupMock.getSecurityGroupId()).thenReturn(randomString());

      var secGroupBuilderMock = mock(SecurityGroup.Builder.class);
      when(secGroupBuilderMock.securityGroupName(any())).thenReturn(secGroupBuilderMock);
      when(secGroupBuilderMock.description(any())).thenReturn(secGroupBuilderMock);
      when(secGroupBuilderMock.vpc(any())).thenReturn(secGroupBuilderMock);
      when(secGroupBuilderMock.build()).thenReturn(secGroupMock);

      var httpListener = mock(ApplicationListener.class);
      doNothing().when(httpListener).addTargetGroups(any(), any());
      when(httpListener.getListenerArn()).thenReturn(randomString());

      var appLoadBalancerMock = mock(ApplicationLoadBalancer.class);
      when(appLoadBalancerMock.addListener(any(), any())).thenReturn(httpListener);
      when(appLoadBalancerMock.getLoadBalancerArn()).thenReturn(randomString());
      when(appLoadBalancerMock.getLoadBalancerDnsName()).thenReturn(randomString());
      when(appLoadBalancerMock.getLoadBalancerCanonicalHostedZoneId()).thenReturn(randomString());

      var appLoBalancerBuilderMock = mock(ApplicationLoadBalancer.Builder.class);
      when(appLoBalancerBuilderMock.loadBalancerName(any())).thenReturn(appLoBalancerBuilderMock);
      when(appLoBalancerBuilderMock.vpc(any())).thenReturn(appLoBalancerBuilderMock);
      when(appLoBalancerBuilderMock.internetFacing(anyBoolean()))
          .thenReturn(appLoBalancerBuilderMock);
      when(appLoBalancerBuilderMock.securityGroup(any())).thenReturn(appLoBalancerBuilderMock);
      when(appLoBalancerBuilderMock.build()).thenReturn(appLoadBalancerMock);

      var sslCertificateMock = mock(ListenerCertificate.class);

      var stringParameterBuilder = mock(StringParameter.Builder.class);
      when(stringParameterBuilder.parameterName(any())).thenReturn(stringParameterBuilder);
      when(stringParameterBuilder.stringValue(any())).thenReturn(stringParameterBuilder);
      when(stringParameterBuilder.build()).thenReturn(mock(StringParameter.class));

      var tagsMock = mock(Tags.class);
      doNothing().when(tagsMock).add(any(), any());

      try (
          var mockedStringParameterBuilder = mockStatic(StringParameter.Builder.class);
          var mockedApplicationLoBalancer = mockStatic(ApplicationLoadBalancer.Builder.class);
          var mockedTags = mockStatic(Tags.class);
          var mockedListenerCertificate = mockStatic(ListenerCertificate.class)
      ) {
        mockedStringParameterBuilder.when(() -> StringParameter.Builder.create(any(), any()))
                                    .thenReturn(stringParameterBuilder);
        mockedApplicationLoBalancer.when(() -> ApplicationLoadBalancer.Builder.create(any(), any()))
                                   .thenReturn(appLoBalancerBuilderMock);
        mockedListenerCertificate.when(() -> ListenerCertificate.fromArn(any()))
                                 .thenReturn(sslCertificateMock);
        mockedTags.when(() -> Tags.of(any())).thenReturn(tagsMock);

        var scope = mock(Construct.class);
        var inputParams = mock((Network.InputParameters.class));
        when(inputParams.getSslCertificateArn()).thenReturn(sslCertificateArg);
        when(inputParams.getMaxAZs()).thenReturn(maxAZs);
        when(inputParams.getNumberOfIsolatedSubnetsPerAZ())
            .thenReturn(numberOfIsolatedSubnetsPerAZ);
        when(inputParams.getNumberOfPublicSubnetsPerAZ()).thenReturn(numberPublicSubnetsPerAZ);
        when(inputParams.getNatGatewayNumber()).thenReturn(natGatewayNumber);
        var appEnv = Network.defaultNetworkApplicationEnvironment(randomString());

        var actual = Network.newInstance(scope, randomString(), appEnv, inputParams);
        assertNotNull(actual);
      }
    });
  }

  @Test
  void getParameter() {
    var stringParamMock = mock(IStringParameter.class);
    String expected = randomString();
    when(stringParamMock.getStringValue()).thenReturn(expected);

    try (var mockedStringParameter = mockStatic(StringParameter.class)) {
      mockedStringParameter.when(() -> StringParameter.fromStringParameterName(any(), any(), any()))
                           .thenReturn(stringParamMock);
      var appEnv = Network.defaultNetworkApplicationEnvironment(randomString());

      assertEquals(expected, Network.getParameter(mock(Network.class), appEnv, randomString()));
    }
  }

  private static Stream<Arguments> getParameterReturnsNullForNullArgumentArgs() {
    return Stream.of(arguments(null, randomString(), randomString()),
                     arguments(mock(Network.class), null, randomString()),
                     arguments(mock(Network.class), randomString(), null));
  }

  @ParameterizedTest
  @MethodSource("getParameterReturnsNullForNullArgumentArgs")
  void getParameterReturnsNullForNullArgument(Construct scope, String environment, String id) {
    var appEnv = Network.defaultNetworkApplicationEnvironment(environment);
    Assertions.assertNull(Network.getParameter(scope, appEnv, id));
  }

  static Stream<Arguments> getterReturnsOK() {
    BiFunction<Construct, ApplicationEnvironment, String> getVPCId = Network::getVPCId,
        getClusterName = Network::getClusterName,
        getLoadBalancerSecurityGroupId = Network::getLoadBalancerSecurityGroupId,
        getLoadBalancerArn = Network::getLoadBalancerArn,
        getLoadBalancerDnsName = Network::getLoadBalancerDnsName,
        getLoadBalancerCanonicalHostedZoneId = Network::getLoadBalancerCanonicalHostedZoneId,
        getHttpListenerArn = Network::getHttpListenerArn,
        getHttpsListenerArn = Network::getHttpsListenerArn;

    return Stream.of(arguments(getVPCId), arguments(getClusterName),
                     arguments(getLoadBalancerSecurityGroupId), arguments(getLoadBalancerArn),
                     arguments(getLoadBalancerDnsName),
                     arguments(getLoadBalancerCanonicalHostedZoneId), arguments(getHttpListenerArn),
                     arguments(getHttpsListenerArn));
  }

  @ParameterizedTest
  @MethodSource("getterReturnsOK")
  <T> void getterReturnsOK(BiFunction<? super Construct, ? super ApplicationEnvironment, ? extends String> netFn) {
    var expected = randomString();

    var stringParamMock = mock(IStringParameter.class);
    when(stringParamMock.getStringValue()).thenReturn(expected);

    try (var mockedStringParameter = mockStatic(StringParameter.class)) {
      mockedStringParameter.when(() -> StringParameter.fromStringParameterName(any(), any(), any()))
                           .thenReturn(stringParamMock);
      var appEnv = Network.defaultNetworkApplicationEnvironment(randomString());
      assertEquals(expected, netFn.apply(mock(Network.class), appEnv));
    }
  }

  @Test
  void getParameterList() {
    var stringParamMock = mock(IStringParameter.class);
    String expected1 = randomString() + "0";
    String expected2 = randomString() + "1";
    when(stringParamMock.getStringValue()).thenReturn(expected1).thenReturn(expected2);

    try (var mockedStringParameter = mockStatic(StringParameter.class)) {
      mockedStringParameter.when(() -> StringParameter.fromStringParameterName(any(), any(), any()))
                           .thenReturn(stringParamMock);
      var appEnv = Network.defaultNetworkApplicationEnvironment(randomString());
      List<String> actual = Network.getParameterList(mock(Network.class), appEnv,
                                                     randomString(), 2);

      assertEquals(expected1, actual.get(0));
      assertEquals(expected2, actual.get(1));
    }
  }

  void testGetParameterList(TriFunction<? super Construct, ? super ApplicationEnvironment, ? super Integer, ? extends List<String>> networkMethod,
                            IVpc iVpcMock) {
    int numberOfElements = 2;
    var stringParamMock = mock(IStringParameter.class);
    String expected1 = randomString() + "0";
    String expected2 = randomString() + "1";
    when(stringParamMock.getStringValue()).thenReturn(expected1).thenReturn(expected2);

    Network networkMock = mock(Network.class);
    when(networkMock.getVpc()).thenReturn(iVpcMock);

    try (var mockedStringParameter = mockStatic(StringParameter.class)) {
      mockedStringParameter.when(() -> StringParameter.fromStringParameterName(any(), any(), any()))
                           .thenReturn(stringParamMock);

      var appEnv = Network.defaultNetworkApplicationEnvironment(randomString());
      List<String> actual = networkMethod.apply(networkMock, appEnv, numberOfElements);

      assertEquals(expected1, actual.get(0));
      assertEquals(expected2, actual.get(1));
    }
  }

  @Test
  void getAvailabilityZones() {
    IVpc vpcMock = mock(IVpc.class);
    when(vpcMock.getAvailabilityZones()).thenReturn(List.of(randomString(), randomString()));
    testGetParameterList(Network::getAvailabilityZones, vpcMock);
  }

  @Test
  void getIsolatedSubnets() {
    IVpc vpcMock = mock(IVpc.class);
    ISubnet subnet = mock(ISubnet.class);
    when(vpcMock.getIsolatedSubnets()).thenReturn(List.of(subnet, subnet));

    testGetParameterList(Network::getIsolatedSubnets, vpcMock);
  }

  @Test
  void getPublicSubnets() {
    IVpc vpcMock = mock(IVpc.class);
    ISubnet subnet = mock(ISubnet.class);
    when(vpcMock.getIsolatedSubnets()).thenReturn(List.of(subnet, subnet));

    testGetParameterList(Network::getPublicSubnets, vpcMock);
  }

  @Test
  void outputParametersFromReturnsOKWithDefaults() {
    var stringParamMock = mock(IStringParameter.class);
    String expected = randomString();
    when(stringParamMock.getStringValue()).thenReturn(expected);

    try (var mockedStringParameter = mockStatic(StringParameter.class)) {
      mockedStringParameter.when(() -> StringParameter.fromStringParameterName(any(), any(), any()))
                           .thenReturn(stringParamMock);
      var scope = mock(Construct.class);
      var appEnv = Network.defaultNetworkApplicationEnvironment(randomString());
      Network.OutputParameters output = Network.outputParametersFrom(scope, appEnv);
      assertNotNull(output);
      assertEquals(expected, output.getVpcId());
      assertEquals(expected, output.getHttpListenerArn());
      assertEquals(expected, output.getHttpsListenerArn().orElseThrow());
      assertEquals(expected, output.getLoadbalancerSecurityGroupId());
      assertEquals(expected, output.getSslCertificateArn());
      assertEquals(expected, Network.getSslCertificateArn(scope, appEnv));
      assertEquals(expected, output.getEcsClusterName());
      assertEquals(expected, output.getLoadBalancerArn());
      assertEquals(expected, output.getLoadBalancerDnsName());
      assertEquals(expected, output.getLoadBalancerCanonicalHostedZoneId());
      assertTrue(output.getAvailabilityZones().contains(expected));
      assertTrue(output.getIsolatedSubnets().contains(expected));
      assertTrue(output.getPublicSubnets().contains(expected));
      assertEquals(Network.DEFAULT_NUMBER_OF_AZ, output.getAvailabilityZones().size());
      assertEquals(Network.DEFAULT_NUMBER_OF_AZ * Network.DEFAULT_NUMBER_OF_ISOLATED_SUBNETS_PER_AZ,
                   output.getIsolatedSubnets().size());
      assertEquals(Network.DEFAULT_NUMBER_OF_AZ * Network.DEFAULT_NUMBER_OF_PUBLIC_SUBNETS_PER_AZ,
                   output.getPublicSubnets().size());
    }
  }

  static Stream<Arguments> outputParametersFromThrowsWithIllegalArgsArguments() {
    return Stream.of(arguments(0, 1, 1), arguments(1, 0, 1), arguments(1, 1, 0));
  }

  @ParameterizedTest
  @MethodSource("outputParametersFromThrowsWithIllegalArgsArguments")
  void outputParametersFromThrowsWithIllegalArgs(int numberOfIsolatedSubnetsPerAz,
                                                 int numberOfPublicSubnetsPerAz,
                                                 int totalAvailabilityZones) {
    var appEnv = Network.defaultNetworkApplicationEnvironment(randomString());
    Executable executable = () -> Network.outputParametersFrom(mock(Construct.class),
                                                               appEnv,
                                                               numberOfIsolatedSubnetsPerAz,
                                                               numberOfPublicSubnetsPerAz,
                                                               totalAvailabilityZones);
    assertThrows(IllegalArgumentException.class, executable);
  }


  static Stream<Arguments> vpcFromThrowsWithIllegalArgsArguments() {
    return Stream.of(arguments(1, 1, 0), arguments(1, 0, 1), arguments(0, 1, 1));
  }

  @ParameterizedTest
  @MethodSource("vpcFromThrowsWithIllegalArgsArguments")
  void vpcFromThrowsWithIllegalArgs(int numberOfIsolatedSubnetsPerAZ,
                                    int numberOfPublicSubnetsPerAZ,
                                    int maxAZs) {
    var nat = new SecureRandom().nextInt();
    Executable executable = () -> testNewInstance(randomString(), numberOfIsolatedSubnetsPerAZ,
                                                  numberOfPublicSubnetsPerAZ, nat, maxAZs);
    assertThrows(IllegalArgumentException.class, executable);
  }

  @Test
  void testInputParameters() {
    Random random = new SecureRandom();

    var sslCertificateArn = randomString();
    var natGatewayNumber = random.nextInt();
    var numberOfIsolatedSubnetsPerAZ = random.nextInt();
    var numberOfPublicSubnetsPerAZ = random.nextInt();
    var maxAZs = random.nextInt();
    var listeningExternalPort = random.nextInt();
    var listeningInternalPort = random.nextInt();
    var listeningHttpsPort = random.nextInt();
    var input = Network.InputParameters.builder()
                                       .sslCertificateArn(sslCertificateArn)
                                       .natGatewayNumber(natGatewayNumber)
                                       .numberOfIsolatedSubnetsPerAZ(numberOfIsolatedSubnetsPerAZ)
                                       .numberOfPublicSubnetsPerAZ(numberOfPublicSubnetsPerAZ)
                                       .maxAZs(maxAZs)
                                       .listeningExternalHttpPort(listeningExternalPort)
                                       .listeningInternalHttpPort(listeningInternalPort)
                                       .listeningHttpsPort(listeningHttpsPort)
                                       .build();

    assertNotNull(input);
    assertEquals(sslCertificateArn, input.getSslCertificateArn());
    assertEquals(natGatewayNumber, input.getNatGatewayNumber());
    assertEquals(numberOfIsolatedSubnetsPerAZ, input.getNumberOfIsolatedSubnetsPerAZ());
    assertEquals(numberOfPublicSubnetsPerAZ, input.getNumberOfPublicSubnetsPerAZ());
    assertEquals(maxAZs, input.getMaxAZs());
    assertEquals(listeningExternalPort, input.getListeningExternalHttpPort());
    assertEquals(listeningInternalPort, input.getListeningInternalHttpPort());
    assertEquals(listeningHttpsPort, input.getListeningHttpsPort());
    assertEquals(String.valueOf(listeningHttpsPort), input.getListeningHttpsPortString());
  }

  private static Stream<Arguments> isArnNotNullReturnsCorrectlyArgs() {
    return Stream.of(arguments(randomString(), true), arguments("", false), arguments("  ", false),
                     arguments(null, false), arguments("null", false));
  }

  @ParameterizedTest
  @MethodSource("isArnNotNullReturnsCorrectlyArgs")
  void isArnNotNullReturnsCorrectly(String input, boolean expected) {
    assertEquals(expected, Network.isArnNotNull(input));
  }
}
