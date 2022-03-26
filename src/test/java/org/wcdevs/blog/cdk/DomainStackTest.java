package org.wcdevs.blog.cdk;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.constructs.Construct;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DomainStackTest {
  private static String randomString() {
    return UUID.randomUUID().toString();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void newInstance(boolean sslCertificateExists) {
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
        mockedNetwork.when(() -> Network.isArnNotNull(any())).thenReturn(sslCertificateExists);

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

        assertNotNull(DomainStack.newInstance(scope, id, awsEnv, appEnv, hostedZoneDomain,
                                              applicationDomain));
      }
    });
  }
}
