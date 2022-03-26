package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.services.lambda.AssetCode;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DeploymentSequencerStackTest {
  private static String randomString() {
    return UUID.randomUUID().toString();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void newInstance(boolean isFifo) {
    StaticallyMockedCdk.executeTest(() -> {
      try (
          var mockedCode = mockStatic(Code.class);
          var mockedFunctionProps = mockStatic(FunctionProps.class)
      ) {
        mockedCode.when(() -> Code.fromAsset(any())).thenReturn(mock(AssetCode.class));

        var functionPropsBuilderMock = mock(FunctionProps.Builder.class);
        when(functionPropsBuilderMock.code(any())).thenReturn(functionPropsBuilderMock);
        when(functionPropsBuilderMock.runtime(any())).thenReturn(functionPropsBuilderMock);
        when(functionPropsBuilderMock.runtime(any())).thenReturn(functionPropsBuilderMock);
        when(functionPropsBuilderMock.handler(any())).thenReturn(functionPropsBuilderMock);
        when(functionPropsBuilderMock.logRetention(any())).thenReturn(functionPropsBuilderMock);
        when(functionPropsBuilderMock.reservedConcurrentExecutions(any()))
            .thenReturn(functionPropsBuilderMock);
        when(functionPropsBuilderMock.events(any())).thenReturn(functionPropsBuilderMock);
        when(functionPropsBuilderMock.environment(any())).thenReturn(functionPropsBuilderMock);
        when(functionPropsBuilderMock.build()).thenReturn(mock(FunctionProps.class));
        mockedFunctionProps.when(FunctionProps::builder).thenReturn(functionPropsBuilderMock);

        var scope = mock(Construct.class);
        var appEnv = mock(ApplicationEnvironment.class);
        when(appEnv.prefixed(any())).thenReturn(randomString());

        var awsEnv = mock(Environment.class);
        when(awsEnv.getRegion()).thenReturn(randomString());

        var inputParams = mock(DeploymentSequencerStack.InputParameters.class);
        when(inputParams.getQueueName()).thenReturn(randomString());
        when(inputParams.isFifo()).thenReturn(isFifo);
        when(inputParams.getCodeDirectory()).thenReturn(randomString());
        when(inputParams.getGithubTokenKey()).thenReturn(randomString());
        when(inputParams.getGithubToken()).thenReturn(randomString());
        when(inputParams.getQueueUrlKey()).thenReturn(randomString());
        when(inputParams.getRegionKey()).thenReturn(randomString());

        var actual = DeploymentSequencerStack.newInstance(scope, awsEnv, appEnv, inputParams);
        assertNotNull(actual);
      }
    });
  }

  @Test
  void inputParametersBuilder() {
    StaticallyMockedCdk.executeTest(() -> {
      Random random = new SecureRandom();
      var fifo = random.nextBoolean();
      var queueName = randomString();
      var codeDirectory = randomString();
      var githubToken = randomString();
      var githubTokenKey = randomString();
      var handler = randomString();
      var retentionDays = mock(RetentionDays.class);
      var queryUrlKey = randomString();
      var regionKey = randomString();
      var concurrentExecutions = random.nextInt();

      var runtime = mock(Runtime.class);
      var input = DeploymentSequencerStack.InputParameters
          .builder()
          .fifo(fifo)
          .queueName(queueName)
          .codeDirectory(codeDirectory)
          .githubToken(githubToken)
          .githubTokenKey(githubTokenKey)
          .handler(handler)
          .logRetentionDays(retentionDays)
          .queueUrlKey(queryUrlKey)
          .regionKey(regionKey)
          .runtime(runtime)
          .reservedConcurrentExecutions(concurrentExecutions)
          .build();
      assertNotNull(input);
      assertEquals(fifo, input.isFifo());
      assertEquals(queueName + (fifo ? ".fifo" : ""), input.getQueueName());
      assertEquals(codeDirectory, input.getCodeDirectory());
      assertEquals(githubToken, input.getGithubToken());
      assertEquals(githubTokenKey, input.getGithubTokenKey());
      assertEquals(handler, input.getHandler());
      assertEquals(retentionDays, input.getLogRetentionDays());
      assertEquals(queryUrlKey, input.getQueueUrlKey());
      assertEquals(regionKey, input.getRegionKey());
      assertEquals(runtime, input.getRuntime());
      assertEquals(concurrentExecutions, input.getReservedConcurrentExecutions());
    });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void suffixDependeOnFifoProp(boolean fifo) {
    StaticallyMockedCdk.executeTest(() -> {
      var input = DeploymentSequencerStack.InputParameters.builder().fifo(fifo).build();
      assertEquals(fifo, input.getQueueName().endsWith(".fifo"));
    });
  }
}
