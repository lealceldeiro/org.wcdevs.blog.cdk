package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Tags;
import software.amazon.awscdk.services.ec2.ISubnet;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCertificate;
import software.amazon.awscdk.services.ssm.IStringParameter;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.jsii.JsiiEngine;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
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
    testNewInstance(randomString());
  }

  @Test
  void newInstanceWithoutCertificate() {
    testNewInstance(null);
  }

  void testNewInstance(String sslCertificateArg) {
    var subnets = Collections.singletonList(mock(ISubnet.class));
    when(subnets.get(0).getSubnetId()).thenReturn(randomString());

    IVpc vpcMock = mock(IVpc.class);
    when(vpcMock.getVpcId()).thenReturn(randomString());
    when(vpcMock.getAvailabilityZones()).thenReturn(Collections.singletonList(randomString()));
    when(vpcMock.getIsolatedSubnets()).thenReturn(subnets);
    when(vpcMock.getPublicSubnets()).thenReturn(subnets);

    var clusterMock = mock(Cluster.class);
    when(clusterMock.getClusterName()).thenReturn(randomString());

    var clusterBuilderMock = mock(Cluster.Builder.class);
    when(clusterBuilderMock.vpc(any())).thenReturn(clusterBuilderMock);
    when(clusterBuilderMock.clusterName(any())).thenReturn(clusterBuilderMock);
    when(clusterBuilderMock.build()).thenReturn(clusterMock);

    SecurityGroup secGroupMock = mock(SecurityGroup.class);
    when(secGroupMock.getSecurityGroupId()).thenReturn(randomString());

    SecurityGroup.Builder secGroupBuilderMock = mock(SecurityGroup.Builder.class);
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

    var appLoadBalancerBuilderMock = mock(ApplicationLoadBalancer.Builder.class);
    when(appLoadBalancerBuilderMock.loadBalancerName(any())).thenReturn(appLoadBalancerBuilderMock);
    when(appLoadBalancerBuilderMock.vpc(any())).thenReturn(appLoadBalancerBuilderMock);
    when(appLoadBalancerBuilderMock.internetFacing(anyBoolean()))
        .thenReturn(appLoadBalancerBuilderMock);
    when(appLoadBalancerBuilderMock.securityGroup(any())).thenReturn(appLoadBalancerBuilderMock);
    when(appLoadBalancerBuilderMock.build()).thenReturn(appLoadBalancerMock);

    var sslCertificateMock = mock(ListenerCertificate.class);

    var stringParameterBuilder = mock(StringParameter.Builder.class);
    when(stringParameterBuilder.parameterName(any())).thenReturn(stringParameterBuilder);
    when(stringParameterBuilder.stringValue(any())).thenReturn(stringParameterBuilder);
    when(stringParameterBuilder.build()).thenReturn(mock(StringParameter.class));

    var tagsMock = mock(Tags.class);
    doNothing().when(tagsMock).add(any(), any());

    try (
        var mockedJsiiEngine = mockStatic(JsiiEngine.class);
        var mockedVpcUtil = mockStatic(NetworkUtil.class);
        var mockedClusterBuilder = mockStatic(Cluster.Builder.class);
        var mockedSecGroupBuilder = mockStatic(SecurityGroup.Builder.class);
        var mockedApplicationLoadBalancer = mockStatic(ApplicationLoadBalancer.Builder.class);
        var mockedStringParameterBuilder = mockStatic(StringParameter.Builder.class);
        var mockedTags = mockStatic(Tags.class);
        var mockedListenerCertificate = mockStatic(ListenerCertificate.class)
    ) {
      mockedJsiiEngine.when(JsiiEngine::getInstance).thenReturn(mock(JsiiEngine.class));

      mockedVpcUtil.when(() -> NetworkUtil.vpcFrom(any(), anyInt(), anyInt(), any(), any(),
                                                   anyInt(), anyInt()))
                   .thenReturn(vpcMock);
      mockedVpcUtil.when(() -> NetworkUtil.cfnSecurityGroupIngressFrom(any(), any(), any(), any()))
                   .thenReturn(null);

      mockedClusterBuilder.when(() -> Cluster.Builder.create(any(), any()))
                          .thenReturn(clusterBuilderMock);

      mockedApplicationLoadBalancer.when(() -> ApplicationLoadBalancer.Builder.create(any(), any()))
                                   .thenReturn(appLoadBalancerBuilderMock);

      mockedSecGroupBuilder.when(() -> SecurityGroup.Builder.create(any(), any()))
                           .thenReturn(secGroupBuilderMock);

      mockedListenerCertificate.when(() -> ListenerCertificate.fromArn(any()))
                               .thenReturn(sslCertificateMock);

      mockedStringParameterBuilder.when(() -> StringParameter.Builder.create(any(), any()))
                                  .thenReturn(stringParameterBuilder);

      mockedTags.when(() -> Tags.of(any())).thenReturn(tagsMock);

      var scope = mock(Construct.class);
      var inputParameters = mock((Network.InputParameters.class));
      when(inputParameters.getSslCertificateArn()).thenReturn(sslCertificateArg);

      Network actual = Network.newInstance(scope, randomString(), randomString(), inputParameters);
      assertNotNull(actual);
    }
  }

  @Test
  void getParameter() {
    var stringParamMock = mock(IStringParameter.class);
    String expected = "null";
    when(stringParamMock.getStringValue()).thenReturn(expected);

    try (var mockedStringParameter = mockStatic(StringParameter.class)) {
      mockedStringParameter.when(() -> StringParameter.fromStringParameterName(any(), any(), any()))
                           .thenReturn(stringParamMock);
      assertEquals(expected, Network.getParameter(mock(Network.class), randomString(),
                                                  randomString()));
    }
  }

  <T> void testGet(BiFunction<? super Construct, ? super String, ? extends String> networkMethod) {
    var expected = randomString();

    var stringParamMock = mock(IStringParameter.class);
    when(stringParamMock.getStringValue()).thenReturn(expected);

    try (var mockedStringParameter = mockStatic(StringParameter.class)) {
      mockedStringParameter.when(() -> StringParameter.fromStringParameterName(any(), any(), any()))
                           .thenReturn(stringParamMock);
      assertEquals(expected, networkMethod.apply(mock(Network.class), randomString()));
    }
  }

  @Test
  void getVPCId() {
    testGet(Network::getVPCId);
  }

  @Test
  void getClusterName() {
    testGet(Network::getClusterName);
  }

  @Test
  void getLoadBalancerSecurityGroupId() {
    testGet(Network::getLoadBalancerSecurityGroupId);
  }

  @Test
  void getLoadBalancerArn() {
    testGet(Network::getLoadBalancerArn);
  }

  @Test
  void getLoadBalancerDnsName() {
    testGet(Network::getLoadBalancerDnsName);
  }

  @Test
  void getLoadBalancerCanonicalHostedZoneId() {
    testGet(Network::getLoadBalancerCanonicalHostedZoneId);
  }

  @Test
  void getHttpListenerArn() {
    testGet(Network::getHttpListenerArn);
  }

  @Test
  void getHttpsListenerArn() {
    testGet(Network::getHttpsListenerArn);
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
      List<String> actual = Network.getParameterList(mock(Network.class), randomString(),
                                                     randomString(), 2);

      assertEquals(expected1, actual.get(0));
      assertEquals(expected2, actual.get(1));
    }
  }

  void testGetParameterList(TriFunction<? super Construct, ? super String, ? super Integer, ? extends List<String>> networkMethod,
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

      List<String> actual = networkMethod.apply(networkMock, randomString(), numberOfElements);

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
  void newInputParametersWithoutCertificate() {
    Network.InputParameters actual = Network.newInputParameters();
    assertNotNull(actual);
    assertNull(actual.getSslCertificateArn());
  }

  @Test
  void newInputParametersWithCertificate() {
    String certificate = randomString();
    Network.InputParameters actual = Network.newInputParameters(certificate);

    assertNotNull(actual);
    assertEquals(certificate, actual.getSslCertificateArn());
  }

  @Test
  void testSetNatGatewayNumber() {
    var natNumber = Math.abs(RANDOM.nextInt(RANDOM_UPPER_BOUND));

    var inputParameters = Network.newInputParameters();
    inputParameters.setNatGatewayNumber(natNumber);

    assertEquals(natNumber, ReflectionUtil.<Integer>getField(inputParameters, "natGatewayNumber"));
  }

  @Test
  void testSetMaxAZs() {
    var maxAZs = Math.abs(RANDOM.nextInt(RANDOM_UPPER_BOUND));

    var inputParameters = Network.newInputParameters();
    inputParameters.setMaxAZs(maxAZs);

    assertEquals(maxAZs, ReflectionUtil.<Integer>getField(inputParameters, "maxAZs"));
  }

  @Test
  void testSetListeningInternalPort() {
    var listeningInternalPort = Math.abs(RANDOM.nextInt(RANDOM_UPPER_BOUND));

    var inputParameters = Network.newInputParameters();
    inputParameters.setListeningInternalPort(listeningInternalPort);

    assertEquals(listeningInternalPort,
                 ReflectionUtil.<Integer>getField(inputParameters, "listeningInternalPort"));
  }

  @Test
  void testSetListeningExternalPort() {
    var listeningExternalPort = Math.abs(RANDOM.nextInt(RANDOM_UPPER_BOUND));

    var inputParameters = Network.newInputParameters();
    inputParameters.setListeningExternalPort(listeningExternalPort);

    assertEquals(listeningExternalPort,
                 ReflectionUtil.<Integer>getField(inputParameters, "listeningExternalPort"));
  }

  @Test
  void testGetNatGatewayNumber() {
    var natGatewayNumber = RANDOM.nextInt();

    var inputParameters = Network.newInputParameters();
    ReflectionUtil.setField(inputParameters, "natGatewayNumber", natGatewayNumber);

    assertEquals(natGatewayNumber, inputParameters.getNatGatewayNumber());
  }

  @Test
  void testGetMaxAZs() {
    var maxAZs = RANDOM.nextInt();

    var inputParameters = Network.newInputParameters();
    ReflectionUtil.setField(inputParameters, "maxAZs", maxAZs);

    assertEquals(maxAZs, inputParameters.getMaxAZs());
  }

  @Test
  void testGetListeningInternalPort() {
    var listeningInternalPort = RANDOM.nextInt();

    var inputParameters = Network.newInputParameters();
    ReflectionUtil.setField(inputParameters, "listeningInternalPort", listeningInternalPort);

    assertEquals(listeningInternalPort, inputParameters.getListeningInternalPort());
  }

  @Test
  void testGetListeningExternalPort() {
    var listeningExternalPort = RANDOM.nextInt();

    var inputParameters = Network.newInputParameters();
    ReflectionUtil.setField(inputParameters, "listeningExternalPort", listeningExternalPort);

    assertEquals(listeningExternalPort, inputParameters.getListeningExternalPort());
  }

  @Test
  void testGetSslCertificateArn() {
    var sslCertificateArn = randomString();

    var inputParameters = Network.newInputParameters(sslCertificateArn);

    assertEquals(sslCertificateArn, inputParameters.getSslCertificateArn());
  }
}
