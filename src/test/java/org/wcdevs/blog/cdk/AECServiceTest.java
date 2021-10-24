package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AECServiceTest {
  private static final Random RANDOM = new SecureRandom();

  private static String randomString() {
    return UUID.randomUUID().toString();
  }

  @Test
  void newInstanceNoHttps() {
    testNewInstance(randomString(), null, emptyList(), false, emptyMap(), false, emptyList());
  }

  @Test
  void newInstanceWithHttps() {
    testNewInstance(randomString(), randomString(), emptyList(), false, emptyMap(), false,
                    emptyList());
  }

  @Test
  void newInstanceWithPolicyStatements() {
    testNewInstance(randomString(), null, List.of(mock(PolicyStatement.class)), false, emptyMap(),
                    false, emptyList());
  }

  @Test
  void newInstanceNoIsEcrSource() {
    testNewInstance(randomString(), null, emptyList(), true, emptyMap(), false, emptyList());
  }

  @Test
  void newInstanceStickySession() {
    testNewInstance(randomString(), null, emptyList(), false, emptyMap(), true, emptyList());
  }

  @Test
  void newInstanceWithEnvVars() {
    testNewInstance(randomString(), null, emptyList(), false, Map.of("k1", "v1"), true,
                    emptyList());
  }

  @Test
  void newInstanceWithSecGroupIdToAllowAccessFromECS() {
    testNewInstance(randomString(), null, emptyList(), false, emptyMap(), false,
                    List.of(randomString(), randomString()));
  }

  void testNewInstance(String httpListenerArn, String httpsListenerArn,
                       List<PolicyStatement> taskPolicyStatements, Boolean isEcrSource,
                       Map<String, String> environmentVars, boolean stickySession,
                       List<String> securityGroupIdsToGrantIngressFromEcs) {
    StaticallyMockedCdk.executeTest(() -> {
      // given
      try (
          var mockedFn = mockStatic(Fn.class);
          var mockedRepository = mockStatic(Repository.class)
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

        var dockerImageMock = mock(AECService.DockerImage.class);
        when(dockerImageMock.isEcrSource()).thenReturn(isEcrSource);
        when(dockerImageMock.getDockerRepositoryName()).thenReturn(randomString());
        when(dockerImageMock.getDockerImageTag()).thenReturn(randomString());
        when(dockerImageMock.getDockerImageUrl()).thenReturn(randomString());

        var inputParams = mock(AECService.InputParameters.class);
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
        var actual = AECService.newInstance(scope, id, awsEnvironment, appEnv, inputParams,
                                            netOutParams);
        // then
        assertNotNull(actual);
      }
    });
  }

  @Test
  void newInputParametersOK() {
    assertNotNull(AECService.newInputParameters(mock(AECService.DockerImage.class),
                                                Map.of("k1", "v1"), List.of("id1")));
  }

  @Test
  void newInputParametersNPEForNullDockerImage() {
    testNewInputParametersNPE(null, Map.of("k1", "v1"), List.of("id1"));
  }

  @Test
  void newInputParametersNPEForNullEnvironmentVariables() {
    testNewInputParametersNPE(mock(AECService.DockerImage.class), null, List.of("id1"));
  }

  @Test
  void newInputParametersNPEForNullListOfGroupIds() {
    testNewInputParametersNPE(mock(AECService.DockerImage.class), Map.of("k1", "v1"), null);
  }

  void testNewInputParametersNPE(AECService.DockerImage dockerImage,
                                 Map<String, String> environmentVariables,
                                 List<String> secGroupIdsToGrantIngressFromEcs) {
    Executable executable = () -> AECService.newInputParameters(dockerImage, environmentVariables,
                                                                secGroupIdsToGrantIngressFromEcs);
    assertThrows(NullPointerException.class, executable);
  }

  @Test
  void newDockerImageOK() {
    assertNotNull(AECService.newDockerImage(randomString(), randomString(), randomString()));
  }

  @Test
  void newDockerImageNPEWithNullDockerRepoName() {
    testNewDockerImageNPE(null, randomString(), randomString());
  }

  @Test
  void newDockerImageNPEWithNullDockerImageTag() {
    testNewDockerImageNPE(randomString(), null, randomString());
  }

  @Test
  void newDockerImageNPEWithNullDockerImageUrl() {
    testNewDockerImageNPE(randomString(), randomString(), null);
  }

  void testNewDockerImageNPE(String dockerRepositoryName, String dockerImageTag,
                             String dockerImageUrl) {
    Executable executable = () -> AECService.newDockerImage(dockerRepositoryName, dockerImageTag,
                                                            dockerImageUrl);
    assertThrows(NullPointerException.class, executable);
  }

  @Test
  void testInputParameterGetDockerImage() {
    var dockerImage = mock(AECService.DockerImage.class);
    var inputParameters = new AECService.InputParameters(dockerImage, emptyMap(), emptyList());

    assertEquals(dockerImage, inputParameters.getDockerImage());
  }

  @Test
  void testInputParameterGetEnvironmentVariables() {
    var environmentVariables = Map.of(randomString(), randomString());
    var inputParameters = new AECService.InputParameters(mock(AECService.DockerImage.class),
                                                         environmentVariables, emptyList());

    assertEquals(environmentVariables, inputParameters.getEnvironmentVariables());
  }

  @Test
  void testInputParameterGetSecGroupIdsToGrantIngressFromEcs() {
    List<String> secGroupIdsToGrantIngressFromEcs = emptyList();
    var inputParameters = new AECService.InputParameters(mock(AECService.DockerImage.class),
                                                         emptyMap(),
                                                         secGroupIdsToGrantIngressFromEcs);

    assertEquals(secGroupIdsToGrantIngressFromEcs,
                 inputParameters.getSecurityGroupIdsToGrantIngressFromEcs());
  }

  @Test
  void testInputParameterGetTaskRolePolicyStatements() {
    testInputParameterGet("taskRolePolicyStatements", List.of(mock(PolicyStatement.class)),
                          AECService.InputParameters::getTaskRolePolicyStatements);
  }

  <T> void testInputParameterGet(String fieldName, T expected,
                                 Function<? super AECService.InputParameters, T> mapper) {
    var inputParameters = new AECService.InputParameters(mock(AECService.DockerImage.class),
                                                         emptyMap(), emptyList());
    TestsReflectionUtil.setField(inputParameters, fieldName, expected);

    assertEquals(expected, mapper.apply(inputParameters));
  }

  @Test
  void testInputParameterGetHealthCheckIntervalSeconds() {
    testInputParameterGet("healthCheckIntervalSeconds", RANDOM.nextInt(),
                          AECService.InputParameters::getHealthCheckIntervalSeconds);
  }

  @Test
  void testInputParameterGetHealthCheckPath() {
    testInputParameterGet("healthCheckPath", randomString(),
                          AECService.InputParameters::getHealthCheckPath);
  }

  @Test
  void testInputParameterGetContainerPort() {
    testInputParameterGet("containerPort", RANDOM.nextInt(),
                          AECService.InputParameters::getContainerPort);
  }

  @Test
  void testInputParameterGetContainerProtocol() {
    testInputParameterGet("containerProtocol", randomString(),
                          AECService.InputParameters::getContainerProtocol);
  }

  @Test
  void testInputParameterGetHealthCheckTimeoutSeconds() {
    testInputParameterGet("healthCheckTimeoutSeconds", RANDOM.nextInt(),
                          AECService.InputParameters::getHealthCheckTimeoutSeconds);
  }

  @Test
  void testInputParameterGetHealthyThresholdCount() {
    testInputParameterGet("healthyThresholdCount", RANDOM.nextInt(),
                          AECService.InputParameters::getHealthyThresholdCount);
  }

  @Test
  void testInputParameterGetUnhealthyThresholdCount() {
    testInputParameterGet("unhealthyThresholdCount", RANDOM.nextInt(),
                          AECService.InputParameters::getUnhealthyThresholdCount);
  }

  @Test
  void testInputParameterGetLogRetention() {
    testInputParameterGet("logRetention", RetentionDays.FIVE_DAYS,
                          AECService.InputParameters::getLogRetention);
  }

  @Test
  void testInputParameterGetCpu() {
    testInputParameterGet("cpu", RANDOM.nextInt(),
                          AECService.InputParameters::getCpu);
  }

  @Test
  void testInputParameterGetMemory() {
    testInputParameterGet("memory", RANDOM.nextInt(),
                          AECService.InputParameters::getMemory);
  }

  @Test
  void testInputParameterGetDesiredInstancesCount() {
    testInputParameterGet("desiredInstancesCount", RANDOM.nextInt(),
                          AECService.InputParameters::getDesiredInstancesCount);
  }

  @Test
  void testInputParameterGetMaximumInstancesPercent() {
    testInputParameterGet("maximumInstancesPercent", RANDOM.nextInt(),
                          AECService.InputParameters::getMaximumInstancesPercent);
  }

  @Test
  void testInputParameterGetMinimumHealthyInstancesPercent() {
    testInputParameterGet("minimumHealthyInstancesPercent", RANDOM.nextInt(),
                          AECService.InputParameters::getMinimumHealthyInstancesPercent);
  }

  @Test
  void testInputParameterGetStickySessionsEnabled() {
    testInputParameterGet("stickySessionsEnabled", RANDOM.nextBoolean(),
                          AECService.InputParameters::isStickySessionsEnabled);
  }

  @Test
  void testInputParameterGetStickySessionsCookieDuration() {
    testInputParameterGet("stickySessionsCookieDuration", RANDOM.nextInt(),
                          AECService.InputParameters::getStickySessionsCookieDuration);
  }

  @Test
  void testInputParameterGetAwsLogsDateTimeFormat() {
    testInputParameterGet("awsLogsDateTimeFormat", randomString(),
                          AECService.InputParameters::getAwsLogsDateTimeFormat);
  }

  @Test
  void testInputParameterSetTaskRolePolicyStatements() {
    var expected = List.of(mock(PolicyStatement.class));
    testInputParameterSet("taskRolePolicyStatements", expected,
                          input -> input.setTaskRolePolicyStatements(expected));
  }

  <T> void testInputParameterSet(String fieldName, T expected,
                                 Consumer<? super AECService.InputParameters> consumer) {
    var inputParameters = new AECService.InputParameters(mock(AECService.DockerImage.class),
                                                         emptyMap(), emptyList());
    consumer.accept(inputParameters);
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
  void testInputParameterSetContainerPort() {
    var expected = RANDOM.nextInt();
    testInputParameterSet("containerPort", expected,
                          input -> input.setContainerPort(expected));
  }

  @Test
  void testInputParameterSetContainerProtocol() {
    var expected = randomString();
    testInputParameterSet("containerProtocol", expected,
                          input -> input.setContainerProtocol(expected));
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
  void testInputParameterSetMmory() {
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
    var dockerImage = new AECService.DockerImage(expected, randomString(), randomString());

    assertEquals(expected, dockerImage.getDockerRepositoryName());
  }

  @Test
  void testDockerImageGetDockerImageTag() {
    var expected = randomString();
    var dockerImage = new AECService.DockerImage(randomString(), expected, randomString());

    assertEquals(expected, dockerImage.getDockerImageTag());
  }

  @Test
  void testDockerImageGetDockerImageUrl() {
    var expected = randomString();
    var dockerImage = new AECService.DockerImage(randomString(), randomString(), expected);

    assertEquals(expected, dockerImage.getDockerImageUrl());
  }

  @Test
  void dockerImageIsEcrSourceReturnsTrueIfDockerRepositoryNameIsNotNull() {
    assertTrue(new AECService.DockerImage(randomString(), randomString(), randomString())
                   .isEcrSource());
  }

  @Test
  void dockerImageIsEcrSourceReturnsFalseIfDockerRepositoryNameIsNotNull() {
    assertFalse(new AECService.DockerImage(null, randomString(), randomString())
                    .isEcrSource());
  }

  @Test
  void testServiceListenerRulesGetHttpRule() {
    var httpRule = mock(CfnListenerRule.class);
    var rules = new AECService.ServiceListenerRules(httpRule, mock(CfnListenerRule.class));

    assertEquals(httpRule, rules.getHttpRule());
  }

  @Test
  void testServiceListenerRulesGetHttpsRule() {
    var httpsRule = mock(CfnListenerRule.class);
    var rules = new AECService.ServiceListenerRules(mock(CfnListenerRule.class), httpsRule);

    assertEquals(httpsRule, rules.getHttpsRule());
  }
}
