package org.wcdevs.blog.cdk;

import lombok.AccessLevel;
import lombok.Getter;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.events.targets.LambdaFunction;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DeploymentSequencerStack extends Stack {
  private static final String GITHUB_TOKEN_KEY = "GITHUB_TOKEN";
  private static final String QUEUE_URL_KEY = "QUEUE_URL";
  private static final String REGION_KEY = "REGION";
  private static final String FUNCTION_ID = "depSeqFun";

  private DeploymentSequencerStack(Construct scope, String id, StackProps props) {
    super(Objects.requireNonNull(scope), Objects.requireNonNull(id), Objects.requireNonNull(props));
  }

  public static DeploymentSequencerStack newInstance(Construct scope, Environment awsEnvironment,
                                                     ApplicationEnvironment applicationEnvironment,
                                                     InputParameters inputParameters) {
    var inParams = Objects.requireNonNull(inputParameters);
    var appEnv = Objects.requireNonNull(applicationEnvironment);

    var name = appEnv.prefixed(Util.joinedString(Util.DASH_JOINER, "deployment", "seq", "stack"));
    var stackProps = StackProps.builder()
                               .stackName(name)
                               .env(awsEnvironment)
                               .build();
    var stack = new DeploymentSequencerStack(scope, name, stackProps);

    var queueName = appEnv.prefixed(inParams.getQueueName());
    var deploymentQueue = Queue.Builder.create(stack, queueName)
                                       .queueName(queueName)
                                       .fifo(inParams.isFifo())
                                       .build();
    var eventSource = SqsEventSource.Builder.create(deploymentQueue).build();

    var code = Code.fromAsset(Objects.requireNonNull(inParams.getCodeDirectory()));
    var envVars = Map.of(inParams.getGithubTokenKey(),
                         Objects.requireNonNull(inParams.getGithubToken()),
                         inParams.getQueueUrlKey(),
                         Objects.requireNonNull(deploymentQueue.getQueueUrl()),
                         inParams.getRegionKey(),
                         Objects.requireNonNull(awsEnvironment.getRegion()));
    var functionProps
        = FunctionProps.builder()
                       .code(code)
                       .runtime(inParams.getRuntime())
                       .handler(inParams.getHandler())
                       .logRetention(inParams.getLogRetentionDays())
                       .reservedConcurrentExecutions(inParams.getReservedConcurrentExecutions())
                       .events(List.of(eventSource))
                       .environment(envVars)
                       .build();
    var fnId = appEnv.prefixed(FUNCTION_ID);
    LambdaFunction.Builder.create(new Function(stack, fnId, functionProps)).build();

    return stack;
  }

  @lombok.Builder
  @Getter(AccessLevel.PACKAGE)
  public static class InputParameters {
    private static final String FIFO_SUFFIX = ".fifo";
    private static final String QUEUE_ID = "depQueue";

    /**
     * Path to the ZIP file containing the lambda function. This attribute is required.
     */
    private String codeDirectory;
    /**
     * This attribute is required.
     */
    private String githubToken;

    @lombok.Builder.Default
    private boolean fifo = true;
    @lombok.Builder.Default
    private String queueName = QUEUE_ID;
    @lombok.Builder.Default
    private Runtime runtime = Runtime.NODEJS_12_X;
    @lombok.Builder.Default
    private String handler = "index.handler";
    @lombok.Builder.Default
    private RetentionDays logRetentionDays = RetentionDays.TWO_WEEKS;
    @lombok.Builder.Default
    private int reservedConcurrentExecutions = 1;
    @lombok.Builder.Default
    private String githubTokenKey = GITHUB_TOKEN_KEY;
    @lombok.Builder.Default
    private String queueUrlKey = QUEUE_URL_KEY;
    @lombok.Builder.Default
    private String regionKey = REGION_KEY;

    String getQueueName() {
      return this.queueName + suffix();
    }

    private String suffix() {
      return isFifo() ? FIFO_SUFFIX : "";
    }
  }
}
