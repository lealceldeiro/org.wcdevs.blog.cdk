package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.Fn;
import software.amazon.awscdk.core.ICfnRuleConditionExpression;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnListenerRule;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.logs.RetentionDays;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
  void newInputParametersOK() {
    var dockerImage = mock(ElasticContainerService.DockerImage.class);
    assertNotNull(ElasticContainerService.newInputParameters(dockerImage,
                                                             Map.of("k1", "v1"), List.of("id1")));
  }

  @Test
  void newInputParametersNPEForNullDockerImage() {
    testNewInputParametersNPE(null, Map.of("k1", "v1"), List.of("id1"));
  }

  @Test
  void newInputParametersNPEForNullEnvironmentVariables() {
    testNewInputParametersNPE(mock(ElasticContainerService.DockerImage.class), null,
                              List.of("id1"));
  }

  @Test
  void newInputParametersNPEForNullListOfGroupIds() {
    testNewInputParametersNPE(mock(ElasticContainerService.DockerImage.class),
                              Map.of("k1", "v1"), null);
  }

  void testNewInputParametersNPE(ElasticContainerService.DockerImage dockerImage,
                                 Map<String, String> environmentVariables,
                                 List<String> secGroupIdsToGrantIngressFromEcs) {
    Executable executable
        = () -> ElasticContainerService.newInputParameters(dockerImage, environmentVariables,
                                                           secGroupIdsToGrantIngressFromEcs);
    assertThrows(NullPointerException.class, executable);
  }

  @Test
  void newDockerImageOK() {
    assertNotNull(ElasticContainerService.newDockerImage(randomString(), randomString(),
                                                         randomString()));
  }

  static Stream<Arguments> newDockerImageThrowsIAEIFParamsAreIncorrectArgs() {
    return Stream.of(arguments(null, null, null),
                     arguments(null, randomString(), null),
                     arguments(randomString(), null, null));
  }

  @ParameterizedTest
  @MethodSource("newDockerImageThrowsIAEIFParamsAreIncorrectArgs")
  void newDockerImageThrowsIAEIFParamsAreIncorrect(String dockerRepositoryName,
                                                   String dockerImageTag, String dockerImageUrl) {
    Executable executable = () -> ElasticContainerService.newDockerImage(dockerRepositoryName,
                                                                         dockerImageTag,
                                                                         dockerImageUrl);
    assertThrows(IllegalArgumentException.class, executable);
  }

  @Test
  void testInputParameterGetDockerImage() {
    var dockerImage = mock(ElasticContainerService.DockerImage.class);
    var inputParameters = new ElasticContainerService.InputParameters(dockerImage, emptyMap(),
                                                                      emptyList());

    assertSame(dockerImage, inputParameters.getDockerImage());
  }

  @Test
  void testInputParameterGetEnvironmentVariables() {
    var environmentVariables = Map.of(randomString(), randomString());
    var dockerImage = mock(ElasticContainerService.DockerImage.class);
    var inputParameters = new ElasticContainerService.InputParameters(dockerImage,
                                                                      environmentVariables,
                                                                      emptyList());

    assertEquals(environmentVariables, inputParameters.getEnvironmentVariables());
  }

  @Test
  void testInputParameterGetSecGroupIdsToGrantIngressFromEcs() {
    List<String> secGroupIdsToGrantIngressFromEcs = emptyList();
    var dockerImage = mock(ElasticContainerService.DockerImage.class);
    var inputParameters
        = new ElasticContainerService.InputParameters(dockerImage,
                                                      emptyMap(),
                                                      secGroupIdsToGrantIngressFromEcs);

    assertEquals(secGroupIdsToGrantIngressFromEcs,
                 inputParameters.getSecurityGroupIdsToGrantIngressFromEcs());
  }

  @Test
  void testInputParameterGetTaskRolePolicyStatements() {
    testInputParameterGet("taskRolePolicyStatements", List.of(mock(PolicyStatement.class)),
                          ElasticContainerService.InputParameters::getTaskRolePolicyStatements);
  }

  <T> void testInputParameterGet(String fieldName, T expected,
                                 Function<? super ElasticContainerService.InputParameters, T> map) {
    var dockerImage = mock(ElasticContainerService.DockerImage.class);
    var inputParameters = new ElasticContainerService.InputParameters(dockerImage, emptyMap(),
                                                                      emptyList());
    TestsReflectionUtil.setField(inputParameters, fieldName, expected);

    assertEquals(expected, map.apply(inputParameters));
  }

  @Test
  void testInputParameterGetHealthCheckIntervalSeconds() {
    testInputParameterGet("healthCheckIntervalSeconds", RANDOM.nextInt(),
                          ElasticContainerService.InputParameters::getHealthCheckIntervalSeconds);
  }

  @Test
  void testInputParameterGetHealthCheckPath() {
    testInputParameterGet("healthCheckPath", randomString(),
                          ElasticContainerService.InputParameters::getHealthCheckPath);
  }

  @Test
  void testInputParameterGetContainerPort() {
    testInputParameterGet("applicationPort", RANDOM.nextInt(),
                          ElasticContainerService.InputParameters::getApplicationPort);
  }

  @Test
  void testInputParameterGetHealthCheckPort() {
    testInputParameterGet("healthCheckPort", RANDOM.nextInt(),
                          ElasticContainerService.InputParameters::getHealthCheckPort);
  }

  @Test
  void testInputParameterGetHealthCheckPortString() {
    var dockerImage = mock(ElasticContainerService.DockerImage.class);
    var inputParameters = new ElasticContainerService.InputParameters(dockerImage, emptyMap(),
                                                                      emptyList());
    var intValue = RANDOM.nextInt();
    TestsReflectionUtil.setField(inputParameters, "healthCheckPort", intValue);

    assertEquals(String.valueOf(intValue), inputParameters.getHealthCheckPortString());
  }

  @Test
  void testInputParameterGetApplicationProtocol() {
    testInputParameterGet("applicationProtocol", randomString(),
                          ElasticContainerService.InputParameters::getApplicationProtocol);
  }

  @Test
  void testInputParameterGetHealthCheckProtocol() {
    testInputParameterGet("healthCheckProtocol", randomString(),
                          ElasticContainerService.InputParameters::getHealthCheckProtocol);
  }

  @Test
  void testInputParameterGetHealthCheckTimeoutSeconds() {
    testInputParameterGet("healthCheckTimeoutSeconds", RANDOM.nextInt(),
                          ElasticContainerService.InputParameters::getHealthCheckTimeoutSeconds);
  }

  @Test
  void testInputParameterGetHealthyThresholdCount() {
    testInputParameterGet("healthyThresholdCount", RANDOM.nextInt(),
                          ElasticContainerService.InputParameters::getHealthyThresholdCount);
  }

  @Test
  void testInputParameterGetUnhealthyThresholdCount() {
    testInputParameterGet("unhealthyThresholdCount", RANDOM.nextInt(),
                          ElasticContainerService.InputParameters::getUnhealthyThresholdCount);
  }

  @Test
  void testInputParameterGetLogRetention() {
    testInputParameterGet("logRetention", RetentionDays.FIVE_DAYS,
                          ElasticContainerService.InputParameters::getLogRetention);
  }

  @Test
  void testInputParameterGetCpu() {
    testInputParameterGet("cpu", RANDOM.nextInt(),
                          ElasticContainerService.InputParameters::getCpu);
  }

  @Test
  void testInputParameterGetMemory() {
    testInputParameterGet("memory", RANDOM.nextInt(),
                          ElasticContainerService.InputParameters::getMemory);
  }

  @Test
  void testInputParameterGetDesiredInstancesCount() {
    testInputParameterGet("desiredInstancesCount", RANDOM.nextInt(),
                          ElasticContainerService.InputParameters::getDesiredInstancesCount);
  }

  @Test
  void testInputParameterGetMaximumInstancesPercent() {
    testInputParameterGet("maximumInstancesPercent", RANDOM.nextInt(),
                          ElasticContainerService.InputParameters::getMaximumInstancesPercent);
  }

  @Test
  void testInputParameterGetMinimumHealthyInstancesPercent() {
    testInputParameterGet("minimumHealthyInstancesPercent", RANDOM.nextInt(),
                          ElasticContainerService.InputParameters::getMinimumHealthyInstancesPercent);
  }

  @Test
  void testInputParameterGetStickySessionsEnabled() {
    testInputParameterGet("stickySessionsEnabled", RANDOM.nextBoolean(),
                          ElasticContainerService.InputParameters::isStickySessionsEnabled);
  }

  @Test
  void testInputParameterGetStickySessionsCookieDuration() {
    testInputParameterGet("stickySessionsCookieDuration", RANDOM.nextInt(),
                          ElasticContainerService.InputParameters::getStickySessionsCookieDuration);
  }

  @Test
  void testInputParameterGetAwsLogsDateTimeFormat() {
    testInputParameterGet("awsLogsDateTimeFormat", randomString(),
                          ElasticContainerService.InputParameters::getAwsLogsDateTimeFormat);
  }

  @Test
  void testInputParameterSetTaskRolePolicyStatements() {
    var expected = List.of(mock(PolicyStatement.class));
    testInputParameterSet("taskRolePolicyStatements", expected,
                          input -> input.setTaskRolePolicyStatements(expected));
  }

  <T> void testInputParameterSet(String fieldName, T expected,
                                 Consumer<? super ElasticContainerService.InputParameters> cons) {
    var dockerImage = mock(ElasticContainerService.DockerImage.class);
    var inputParameters = new ElasticContainerService.InputParameters(dockerImage, emptyMap(),
                                                                      emptyList());
    cons.accept(inputParameters);
    assertEquals(expected, TestsReflectionUtil.getField(inputParameters, fieldName));
  }

  @Test
  void testInputParameterSetHealthCheckIntervalSeconds() {
    var expected = RANDOM.nextInt();
    testInputParameterSet("healthCheckIntervalSeconds", expected,
                          input -> input.setHealthCheckIntervalSeconds(expected));
  }

  @Test
  void testInputParameterSetHealthCheckPath() {
    var expected = randomString();
    testInputParameterSet("healthCheckPath", expected,
                          input -> input.setHealthCheckPath(expected));
  }

  @Test
  void testInputParameterSetHealthCheckPort() {
    var expected = RANDOM.nextInt();
    testInputParameterSet("healthCheckPort", expected,
                          input -> input.setHealthCheckPort(expected));
  }

  @Test
  void testInputParameterSetHealthCheckProtocol() {
    var expected = randomString();
    testInputParameterSet("healthCheckProtocol", expected,
                          input -> input.setHealthCheckProtocol(expected));
  }

  @Test
  void testInputParameterSetApplicationPort() {
    var expected = RANDOM.nextInt();
    testInputParameterSet("applicationPort", expected,
                          input -> input.setApplicationPort(expected));
  }

  @Test
  void testInputParameterSetApplicationProtocol() {
    var expected = randomString();
    testInputParameterSet("applicationProtocol", expected,
                          input -> input.setApplicationProtocol(expected));
  }

  @Test
  void testInputParameterSetHealthCheckTimeoutSeconds() {
    var expected = RANDOM.nextInt();
    testInputParameterSet("healthCheckTimeoutSeconds", expected,
                          input -> input.setHealthCheckTimeoutSeconds(expected));
  }

  @Test
  void testInputParameterSetHealthyThresholdCount() {
    var expected = RANDOM.nextInt();
    testInputParameterSet("healthyThresholdCount", expected,
                          input -> input.setHealthyThresholdCount(expected));
  }

  @Test
  void testInputParameterSetUnhealthyThresholdCount() {
    var expected = RANDOM.nextInt();
    testInputParameterSet("unhealthyThresholdCount", expected,
                          input -> input.setUnhealthyThresholdCount(expected));
  }

  @Test
  void testInputParameterSetLogRetention() {
    var expected = RetentionDays.FOUR_MONTHS;
    testInputParameterSet("logRetention", expected,
                          input -> input.setLogRetention(expected));
  }

  @Test
  void testInputParameterSetCpu() {
    var expected = RANDOM.nextInt();
    testInputParameterSet("cpu", expected,
                          input -> input.setCpu(expected));
  }

  @Test
  void testInputParameterSetMemory() {
    var expected = RANDOM.nextInt();
    testInputParameterSet("memory", expected,
                          input -> input.setMemory(expected));
  }

  @Test
  void testInputParameterSetDesiredInstancesCount() {
    var expected = RANDOM.nextInt();
    testInputParameterSet("desiredInstancesCount", expected,
                          input -> input.setDesiredInstancesCount(expected));
  }

  @Test
  void testInputParameterSetMaximumInstancesPercent() {
    var expected = RANDOM.nextInt();
    testInputParameterSet("maximumInstancesPercent", expected,
                          input -> input.setMaximumInstancesPercent(expected));
  }

  @Test
  void testInputParameterSetMinimumHealthyInstancesPercent() {
    var expected = RANDOM.nextInt();
    testInputParameterSet("minimumHealthyInstancesPercent", expected,
                          input -> input.setMinimumHealthyInstancesPercent(expected));
  }

  @Test
  void testInputParameterSetStickySessionsEnabled() {
    var expected = RANDOM.nextBoolean();
    testInputParameterSet("stickySessionsEnabled", expected,
                          input -> input.setStickySessionsEnabled(expected));
  }

  @Test
  void testInputParameterSetStickySessionsCookieDuration() {
    var expected = RANDOM.nextInt();
    testInputParameterSet("stickySessionsCookieDuration", expected,
                          input -> input.setStickySessionsCookieDuration(expected));
  }

  @Test
  void testInputParameterSetAwsLogsDateTimeFormat() {
    var expected = randomString();
    testInputParameterSet("awsLogsDateTimeFormat", expected,
                          input -> input.setAwsLogsDateTimeFormat(expected));
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
}
