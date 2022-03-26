package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.ICfnRuleConditionExpression;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnListenerRule;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ElasticContainerServiceTest {
  private static final Random RANDOM = new SecureRandom();

  private static String randomString() {
    return UUID.randomUUID().toString();
  }

  static Stream<Arguments> newInstanceParameters() {
    return Stream.of(
        arguments(randomString(), null, emptyList(), false, emptyMap(), false, emptyList()),
        arguments(randomString(), randomString(), emptyList(), false, emptyMap(), false,
                  emptyList()),
        arguments(randomString(), null, List.of(mock(PolicyStatement.class)), false, emptyMap(),
                  false, emptyList()),
        arguments(randomString(), null, emptyList(), true, emptyMap(), false, emptyList()),
        arguments(randomString(), null, emptyList(), false, emptyMap(), true, emptyList()),
        arguments(randomString(), null, emptyList(), false, Map.of("k1", "v1"), true, emptyList()),
        arguments(randomString(), null, emptyList(), false, emptyMap(), false,
                  List.of(randomString(), randomString()))
                    );
  }

  @ParameterizedTest
  @MethodSource("newInstanceParameters")
  void newInstance(String httpListenerArn, String httpsListenerArn,
                   List<PolicyStatement> taskPolicyStatements, Boolean isEcrSource,
                   Map<String, String> environmentVars, boolean stickySession,
                   List<String> securityGroupIdsToGrantIngressFromEcs) {
    StaticallyMockedCdk.executeTest(() -> {
      // given
      try (
          var mockedFn = mockStatic(Fn.class);
          var mockedRepository = mockStatic(Repository.class);
          var ignored = mockStatic(CfnListenerRule.class)
      ) {
        mockedFn.when(() -> Fn.conditionEquals(any(), any()))
                .thenReturn(mock(ICfnRuleConditionExpression.class));
        mockedFn.when(() -> Fn.conditionNot(any()))
                .thenReturn(mock(ICfnRuleConditionExpression.class));
        mockedRepository.when(() -> Repository.fromRepositoryName(any(), any(), any()))
                        .thenReturn(mock(IRepository.class));

        var scope = mock(Construct.class);
        var id = randomString();

        var awsEnvironment = mock(Environment.class);
        when(awsEnvironment.getRegion()).thenReturn(randomString());

        var appEnv = mock(ApplicationEnvironment.class);
        when(appEnv.prefixed(any())).thenReturn(randomString());

        var dockerImageMock = mock(ElasticContainerService.DockerImage.class);
        when(dockerImageMock.isEcrSource()).thenReturn(isEcrSource);
        when(dockerImageMock.getDockerRepositoryName()).thenReturn(randomString());
        when(dockerImageMock.getDockerImageTag()).thenReturn(randomString());
        when(dockerImageMock.getDockerImageUrl()).thenReturn(randomString());

        var inputParams = mock(ElasticContainerService.InputParameters.class);
        when(inputParams.getTaskRolePolicyStatements()).thenReturn(taskPolicyStatements);
        when(inputParams.getDockerImage()).thenReturn(dockerImageMock);
        when(inputParams.getAwsLogsDateTimeFormat()).thenReturn(randomString());
        when(inputParams.getEnvironmentVariables()).thenReturn(environmentVars);
        when(inputParams.getTaskRolePolicyStatements()).thenReturn(taskPolicyStatements);
        when(inputParams.isStickySessionsEnabled()).thenReturn(stickySession);
        when(inputParams.getSecurityGroupIdsToGrantIngressFromEcs())
            .thenReturn(securityGroupIdsToGrantIngressFromEcs);

        var netOutParams = mock(Network.OutputParameters.class);
        when(netOutParams.getHttpsListenerArn()).thenReturn(Optional.ofNullable(httpsListenerArn));
        when(netOutParams.getHttpListenerArn()).thenReturn(httpListenerArn);

        // when
        var actual = ElasticContainerService.newInstance(scope, id, awsEnvironment, appEnv,
                                                         inputParams, netOutParams);
        // then
        assertNotNull(actual);
      }
    });
  }

  @Test
  void newInputParametersOverload2OK() {
    var dockerImage = mock(ElasticContainerService.DockerImage.class);
    var actual = ElasticContainerService.newInputParameters(dockerImage);
    assertNotNull(actual);
    assertTrue(actual.getEnvironmentVariables().isEmpty());
  }

  @Test
  void testDockerImageGetDockerRepositoryName() {
    var expected = randomString();
    var dockerImage = new ElasticContainerService.DockerImage(expected, randomString(),
                                                              randomString());

    assertEquals(expected, dockerImage.getDockerRepositoryName());
  }

  @Test
  void testDockerImageGetDockerImageTag() {
    var expected = randomString();
    var dockerImage = new ElasticContainerService.DockerImage(randomString(), expected,
                                                              randomString());

    assertEquals(expected, dockerImage.getDockerImageTag());
  }

  @Test
  void testDockerImageGetDockerImageUrl() {
    var expected = randomString();
    var dockerImage = new ElasticContainerService.DockerImage(randomString(), randomString(),
                                                              expected);

    assertEquals(expected, dockerImage.getDockerImageUrl());
  }

  @Test
  void dockerImageIsEcrSourceReturnsTrueIfDockerRepositoryNameIsNotNull() {
    assertTrue(new ElasticContainerService.DockerImage(randomString(), randomString(),
                                                       randomString())
                   .isEcrSource());
  }

  @Test
  void dockerImageIsEcrSourceReturnsFalseIfDockerRepositoryNameIsNotNull() {
    assertFalse(new ElasticContainerService.DockerImage(null, randomString(), randomString())
                    .isEcrSource());
  }

  @Test
  void testServiceListenerRulesGetHttpRule() {
    StaticallyMockedCdk.executeTest(() -> {
      try (var ignored = mockStatic(CfnListenerRule.class)) {
        var httpRule = mock(CfnListenerRule.class);
        var rules = new ElasticContainerService.ServiceListenerRules(httpRule,
                                                                     mock(CfnListenerRule.class));

        assertEquals(httpRule, rules.getHttpRule());
      }
    });
  }

  @Test
  void testServiceListenerRulesGetHttpsRule() {
    StaticallyMockedCdk.executeTest(() -> {
      try (var ignored = mockStatic(CfnListenerRule.class)) {
        var httpsRule = mock(CfnListenerRule.class);
        var rules = new ElasticContainerService.ServiceListenerRules(mock(CfnListenerRule.class),
                                                                     httpsRule);

        assertEquals(httpsRule, rules.getHttpsRule());
      }
    });
  }

  @Test
  void inputParameters() {
    StaticallyMockedCdk.executeTest(() -> {
      var applicationPort = RANDOM.nextInt();
      var applicationProtocol = randomString();
      var dockerImage = mock(ElasticContainerService.DockerImage.class);
      var cpu = RANDOM.nextInt();
      var awsLogsDateTimeFormat = randomString();
      var desiredInstancesCount = RANDOM.nextInt();
      var environmentVariables = Map.of(randomString(), randomString());
      var healthCheckIntervalSeconds = RANDOM.nextInt();
      var healthCheckPath = randomString();
      var healthCheckPort = RANDOM.nextInt();
      var healthCheckProtocol = randomString();
      var healthCheckTimeoutSeconds = RANDOM.nextInt();
      var healthyThresholdCount = RANDOM.nextInt();
      var logRetention = mock(RetentionDays.class);
      var maximumInstancesPercent = RANDOM.nextInt();
      var memory = RANDOM.nextInt();
      var minimumHealthyInstancesPercent = RANDOM.nextInt();
      var stickySessionsCookieDuration = RANDOM.nextInt();
      var stickySessionsEnabled = RANDOM.nextBoolean();
      var securityGroupIdsToGrantIngressFromEcs = List.of(randomString());
      var taskRolePolicyStatements = List.of(mock(PolicyStatement.class));
      var unhealthyThresholdCount = RANDOM.nextInt();
      var actual = ElasticContainerService.InputParameters
          .builder()
          .applicationPort(applicationPort)
          .applicationProtocol(applicationProtocol)
          .dockerImage(dockerImage)
          .cpu(cpu)
          .awsLogsDateTimeFormat(awsLogsDateTimeFormat)
          .desiredInstancesCount(desiredInstancesCount)
          .environmentVariables(environmentVariables)
          .healthCheckIntervalSeconds(healthCheckIntervalSeconds)
          .healthCheckPath(healthCheckPath)
          .healthCheckPort(healthCheckPort)
          .healthCheckProtocol(healthCheckProtocol)
          .healthCheckTimeoutSeconds(healthCheckTimeoutSeconds)
          .healthyThresholdCount(healthyThresholdCount)
          .logRetention(logRetention)
          .maximumInstancesPercent(maximumInstancesPercent)
          .memory(memory)
          .minimumHealthyInstancesPercent(minimumHealthyInstancesPercent)
          .stickySessionsCookieDuration(stickySessionsCookieDuration)
          .stickySessionsEnabled(stickySessionsEnabled)
          .securityGroupIdsToGrantIngressFromEcs(securityGroupIdsToGrantIngressFromEcs)
          .taskRolePolicyStatements(taskRolePolicyStatements)
          .unhealthyThresholdCount(unhealthyThresholdCount)
          .build();

      assertEquals(applicationPort, actual.getApplicationPort());
      assertEquals(applicationProtocol, actual.getApplicationProtocol());
      assertSame(dockerImage, actual.getDockerImage());
      assertEquals(cpu, actual.getCpu());
      assertEquals(awsLogsDateTimeFormat, actual.getAwsLogsDateTimeFormat());
      assertEquals(desiredInstancesCount, actual.getDesiredInstancesCount());
      assertEquals(environmentVariables, actual.getEnvironmentVariables());
      assertEquals(healthCheckIntervalSeconds, actual.getHealthCheckIntervalSeconds());
      assertEquals(healthCheckPath, actual.getHealthCheckPath());
      assertEquals(healthCheckPort, actual.getHealthCheckPort());
      assertEquals(String.valueOf(healthCheckPort), actual.getHealthCheckPortString());
      assertEquals(healthCheckProtocol, actual.getHealthCheckProtocol());
      assertEquals(healthCheckTimeoutSeconds, actual.getHealthCheckTimeoutSeconds());
      assertEquals(healthyThresholdCount, actual.getHealthyThresholdCount());
      assertEquals(logRetention, actual.getLogRetention());
      assertEquals(maximumInstancesPercent, actual.getMaximumInstancesPercent());
      assertEquals(memory, actual.getMemory());
      assertEquals(minimumHealthyInstancesPercent, actual.getMinimumHealthyInstancesPercent());
      assertEquals(stickySessionsCookieDuration, actual.getStickySessionsCookieDuration());
      assertEquals(stickySessionsCookieDuration, actual.getStickySessionsCookieDuration());
      assertEquals(stickySessionsEnabled, actual.isStickySessionsEnabled());
      assertEquals(securityGroupIdsToGrantIngressFromEcs, actual.getSecurityGroupIdsToGrantIngressFromEcs());
      assertEquals(taskRolePolicyStatements, actual.getTaskRolePolicyStatements());
      assertEquals(unhealthyThresholdCount, actual.getUnhealthyThresholdCount());
    });
  }

  @Test
  void dockerImage() {
    var dockerImageUrl = randomString();
    var dockerImageTag = randomString();
    var dockerRepositoryName = randomString();
    var dockerImage = ElasticContainerService.DockerImage.builder()
                                                         .dockerImageUrl(dockerImageUrl)
                                                         .dockerImageTag(dockerImageTag)
                                                         .dockerRepositoryName(dockerRepositoryName)
                                                         .build();
    assertEquals(dockerImageUrl, dockerImage.getDockerImageUrl());
    assertEquals(dockerImageTag, dockerImage.getDockerImageTag());
    assertEquals(dockerRepositoryName, dockerImage.getDockerRepositoryName());
  }
}
