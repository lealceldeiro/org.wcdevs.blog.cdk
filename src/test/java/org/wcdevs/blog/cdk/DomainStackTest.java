package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DomainStackTest {
  private static final Random RANDOM = new SecureRandom();

  private static String randomString() {
    return UUID.randomUUID().toString();
  }

  private static Stream<Arguments> newInstanceArgs() {
    return Stream.of(arguments(false, false),
                     arguments(true, false),
                     arguments(false, true),
                     arguments(true, true));
  }

  @ParameterizedTest
  @MethodSource("newInstanceArgs")
  void newInstance(boolean withCustomInputParams, boolean sslActivated) {
    StaticallyMockedCdk.executeTest(() -> {
      try (
          var mockedNetwork = mockStatic(Network.class);
          var mockedApplicationLoadBalancer = mockStatic(ApplicationLoadBalancer.class);
          var mockedApplicationListener = mockStatic(ApplicationListener.class)
      ) {
        var netOutParamsMock = mock(Network.OutputParameters.class);
        when(netOutParamsMock.getLoadBalancerArn()).thenReturn(randomString());
        when(netOutParamsMock.getLoadbalancerSecurityGroupId()).thenReturn(randomString());
        when(netOutParamsMock.getLoadBalancerCanonicalHostedZoneId()).thenReturn(randomString());
        when(netOutParamsMock.getLoadBalancerDnsName()).thenReturn(randomString());
        mockedNetwork.when(() -> Network.outputParametersFrom(any(), any()))
                     .thenReturn(netOutParamsMock);

        mockedApplicationListener.when(() -> ApplicationListener.fromLookup(any(), any(), any()))
                                 .thenReturn(mock(ApplicationListener.class));

        var applicationLoadBalancerMock = mock(ApplicationLoadBalancer.class);
        when(applicationLoadBalancerMock.addListener(any(), any()))
            .thenReturn(mock(ApplicationListener.class));
        mockedApplicationLoadBalancer
            .when(() -> ApplicationLoadBalancer.fromApplicationLoadBalancerAttributes(any(), any(),
                                                                                      any()))
            .thenReturn(applicationLoadBalancerMock);

        var scope = mock(Construct.class);
        var id = randomString();
        var awsEnv = mock(Environment.class);
        var appEnv = mock(ApplicationEnvironment.class);
        var hostedZoneDomain = randomString();
        var applicationDomain = randomString();

        var inputParams = DomainStack.InputParameters.builder()
                                                     .sslCertificateActivated(sslActivated)
                                                     .build();

        var actual = withCustomInputParams
                     ? DomainStack.newInstance(scope, id, awsEnv, appEnv, hostedZoneDomain,
                                               applicationDomain, inputParams)
                     : DomainStack.newInstance(scope, id, awsEnv, appEnv, hostedZoneDomain,
                                               applicationDomain);
        assertNotNull(actual);
      }
    });
  }

  @Test
  void inputParameters() {
    var sslCertificateActivated = RANDOM.nextBoolean();
    var parameters = DomainStack.InputParameters.builder()
                                                .sslCertificateActivated(sslCertificateActivated)
                                                .build();
    assertNotNull(parameters);
    assertEquals(sslCertificateActivated, parameters.isSslCertificateActivated());
  }
}
