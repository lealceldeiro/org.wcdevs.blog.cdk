package org.wcdevs.blog.cdk;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import software.amazon.awscdk.core.CfnCondition;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.Fn;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.services.ec2.CfnSecurityGroup;
import software.amazon.awscdk.services.ec2.CfnSecurityGroupIngress;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.CfnService;
import software.amazon.awscdk.services.ecs.CfnTaskDefinition;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnListenerRule;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnTargetGroup;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.IGrantable;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

/**
 * Create an ECS service within and ECS cluster that is provided by a Network construct. For that:
 *
 * <ul>
 *   <li>
 *     It defines an ECS task that hosts a given Docker image.
 *   </li>
 *   <li>
 *     It adds a Service to the ECS cluster previously deployed in a Network construct and adds the
 *     tasks to it.
 *   </li>
 *   <li>
 *     It creates a target group for the load balancer deployed in a Network construct and binds
 *     it to the ECS service.
 *   </li>
 *   <li>
 *     It creates a security group for an ECS containers and configures it so the load balancer
 *     may route traffic to the Docker containers.
 *   </li>
 *   <li>
 *     It creates a log group so the application can send logs to CloudWatch.
 *   </li>
 * </ul>
 *
 * @see AECService#newInstance(Construct, String, Environment, ApplicationEnvironment, InputParameters, Network.OutputParameters)
 * @see AECService#newDockerImage(String, String, String)
 * @see AECService#newInputParameters(DockerImage, Map, List)
 * @see Network#outputParametersFrom(Construct, String)
 */
public final class AECService extends Construct {
  private static final String AWS_SERVICE_ECS_TASKS_AMAZON_COM = "ecs-tasks.amazon.com";

  private static final String NETWORK_MODE_AWS_VPC = "awsvpc";
  private static final String LUNCH_TYPE_FARGATE = "FARGATE";

  private static final String TARGET_TYPE_IP = "ip";
  private static final String TARGET_TYPE_INSTANCE = "instance";
  private static final String TARGET_TYPE_LAMBDA = "lambda";
  private static final String TARGET_TYPE_ALB = "alb";

  public static final String LOG_DRIVER_AWS_LOGS = "awslogs";
  public static final String LOG_DRIVER_SPLUNK = "splunk";
  public static final String LOG_DRIVER_AWS_FIRE_LENS = "awsfirelens";

  private static final String ASSIGN_PUBLIC_IP_ENABLED = "ENABLED";

  private static final String STICKY_SESSIONS_ENABLED = "stickiness.enabled";
  private static final String STICKY_SESSIONS_TYPE = "stickiness.type";
  private static final String STICKY_SESSIONS_LB_COOKIE_DURATION = "stickiness.lb_cookie.duration_seconds";
  private static final String STICKY_SESSIONS_TYPE_LB_COOKIE = "lb_cookie";
  private static final String STRING_TRUE = "true";

  private static final String LISTENER_RULE_ACTION_TYPE_FORWARD = "forward";
  private static final String LISTENER_RULE_ACTION_TYPE_FIXED_RESPONSE = "fixed-response";
  private static final String LISTENER_RULE_ACTION_TYPE_REDIRECT = "redirect";
  private static final String LISTENER_RULE_CONDITION_PATH_PATTERN = "path-pattern";
  private static final String LISTENER_RULE_CONDITION_HTTP_REQ_METHOD = "http-request-method";
  private static final String LISTENER_RULE_CONDITION_HOST_HEADER = "host-header";
  private static final String LISTENER_RULE_CONDITION_SOURCE_IP = "source-ip";
  private static final int HTTP_LISTENER_RULE_FORWARD_PATH_PRIORITY = 3;
  private static final int HTTPS_LISTENER_RULE_FORWARD_PATH_PRIORITY = 5;

  private AECService(Construct scope, String id) {
    super(scope, id);
  }

  public static AECService newInstance(Construct scope, String id, Environment awsEnvironment,
                                       ApplicationEnvironment applicationEnvironment,
                                       InputParameters inputParameters,
                                       Network.OutputParameters networkOutputParameters) {
    var validScope = Objects.requireNonNull(scope);
    var validId = Objects.requireNonNull(id);
    var awsEnv = Objects.requireNonNull(awsEnvironment);
    var inParameters = Objects.requireNonNull(inputParameters);
    var netOutputParameters = Objects.requireNonNull(networkOutputParameters);
    var appEnv = Objects.requireNonNull(applicationEnvironment);

    var aECService = new AECService(validScope, validId);

    var targetGroup = targetGroup(aECService, inParameters, netOutputParameters);
    var serviceHttpListenerRules = httpListenerRules(aECService, targetGroup, netOutputParameters);

    var logGroup = LogGroup.Builder.create(aECService, "ecsLogGroup")
                                   .logGroupName(applicationEnvironment.prefixed("logs"))
                                   .retention(inParameters.getLogRetention())
                                   .removalPolicy(RemovalPolicy.DESTROY)
                                   .build();

    var ecsTaskExecutionRole = ecsTaskExecutionRole(aECService, appEnv);
    var ecsTaskRole = ecsTaskRole(aECService, appEnv, inParameters);

    var dockerImageUrl = dockerImageRepositoryUrl(aECService, inParameters, ecsTaskExecutionRole);

    var containerDefProperty = containerDefinitionProperty(awsEnv, logGroup, appEnv, inParameters,
                                                           dockerImageUrl);

    var taskDefinition = taskDefinition(aECService, inParameters, ecsTaskExecutionRole, ecsTaskRole,
                                        containerDefProperty);

    var ecsSecurityGroup = ecsSecurityGroup(aECService, inParameters, netOutputParameters);

    var cfnService = cfnService(aECService, taskDefinition, targetGroup, ecsSecurityGroup, appEnv,
                                inParameters, netOutputParameters);
    // https://stackoverflow.com/q/61250772/5640649
    cfnService.addDependsOn(serviceHttpListenerRules.getHttpRule());

    return aECService;
  }

  // region helpers
  private static CfnTargetGroup targetGroup(AECService scope, InputParameters params,
                                            Network.OutputParameters netOutputParameters) {
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-elasticloadbalancingv2-targetgroup.html
    return CfnTargetGroup.Builder.create(scope, "targetGroup")
                                 .healthCheckIntervalSeconds(params.getHealthCheckIntervalSeconds())
                                 .healthCheckPath(params.getHealthCheckPath())
                                 .healthCheckPort(String.valueOf(params.getContainerPort()))
                                 .healthCheckProtocol(params.getContainerProtocol())
                                 .healthCheckTimeoutSeconds(params.getHealthCheckTimeoutSeconds())
                                 .healthyThresholdCount(params.getHealthyThresholdCount())
                                 .unhealthyThresholdCount(params.getUnhealthyThresholdCount())
                                 .targetGroupAttributes(stickySessionsConf(params))
                                 .targetType(TARGET_TYPE_IP)
                                 .port(params.getContainerPort())
                                 .protocol(params.getContainerProtocol())
                                 .vpcId(netOutputParameters.getVpcId())
                                 .build();
  }

  private static List<CfnTargetGroup.TargetGroupAttributeProperty> stickySessionsConf(InputParameters params) {
    var cookieDuration = String.valueOf(params.getStickySessionsCookieDuration());

    // https://docs.aws.amazon.com/elasticloadbalancing/latest/application/sticky-sessions.html
    // https://docs.aws.amazon.com/elasticloadbalancing/latest/classic/elb-sticky-sessions.html
    return !params.isStickySessionsEnabled()
           ? emptyList()
           : List.of(CfnTargetGroup.TargetGroupAttributeProperty
                         .builder()
                         .key(STICKY_SESSIONS_ENABLED)
                         .value(STRING_TRUE)
                         .build(),
                     CfnTargetGroup.TargetGroupAttributeProperty
                         .builder()
                         .key(STICKY_SESSIONS_TYPE)
                         .value(STICKY_SESSIONS_TYPE_LB_COOKIE)
                         .build(),
                     CfnTargetGroup.TargetGroupAttributeProperty
                         .builder()
                         .key(STICKY_SESSIONS_LB_COOKIE_DURATION)
                         .value(cookieDuration)
                         .build());
  }

  private static ServiceListenerRules httpListenerRules(Construct scope, CfnTargetGroup targetGroup,
                                                        Network.OutputParameters netOutputParams) {
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-elasticloadbalancingv2-listenerrule.html
    var actionProperty = CfnListenerRule.ActionProperty.builder()
                                                       .targetGroupArn(targetGroup.getRef())
                                                       .type(LISTENER_RULE_ACTION_TYPE_FORWARD)
                                                       .build();
    var conditionProperty = CfnListenerRule.RuleConditionProperty.builder()
                                                                 .field(LISTENER_RULE_CONDITION_PATH_PATTERN)
                                                                 .values(List.of("*"))
                                                                 .build();
    var nullValue = Network.NULL_HTTPS_LISTENER_ARN_VALUE;
    var httpsListenerArn = netOutputParams.getHttpsListenerArn()
                                          .orElse(nullValue);

    var httpsListenerIsNotNull = Fn.conditionNot(Fn.conditionEquals(httpsListenerArn, nullValue));
    var httpsListenerArnExists = CfnCondition.Builder.create(scope, "httpsListenerRuleCondition")
                                                     .expression(httpsListenerIsNotNull)
                                                     .build();

    var httpsListenerRule = CfnListenerRule.Builder.create(scope, "httpsListenerRule")
                                                   .actions(List.of(actionProperty))
                                                   .conditions(List.of(conditionProperty))
                                                   .listenerArn(httpsListenerArn)
                                                   .priority(HTTP_LISTENER_RULE_FORWARD_PATH_PRIORITY)
                                                   .build();
    httpsListenerRule.getCfnOptions().setCondition(httpsListenerArnExists);

    var httpListenerRule = CfnListenerRule.Builder.create(scope, "httpListenerRule")
                                                  .actions(List.of(actionProperty))
                                                  .conditions(List.of(conditionProperty))
                                                  .listenerArn(
                                                      netOutputParams.getHttpListenerArn()
                                                              )
                                                  .priority(HTTPS_LISTENER_RULE_FORWARD_PATH_PRIORITY)
                                                  .build();

    return new ServiceListenerRules(httpsListenerRule, httpListenerRule);
  }

  private static Role ecsTaskExecutionRole(Construct scope, ApplicationEnvironment appEnv) {
    var resources = List.of("*");
    var actions = List.of("ecr:GetAuthorizationToken", "ecr:BatchCheckLayerAvailability",
                          "ecr:GetDownloadUrlForLayer", "ecr:BatchGetImage", "logs:CreateLogStream",
                          "logs:PutLogEvents");
    var statements = List.of(PolicyStatement.Builder.create()
                                                    .effect(Effect.ALLOW)
                                                    .resources(resources)
                                                    .actions(actions)
                                                    .build());
    var policyDocument = PolicyDocument.Builder.create().statements(statements).build();
    var policies = Map.of(appEnv.prefixed("ecsTaskExecutionRolePolicy"), policyDocument);

    return Role.Builder.create(scope, "ecsTaskExecutionRole")
                       .assumedBy(ServicePrincipal.Builder.create(AWS_SERVICE_ECS_TASKS_AMAZON_COM)
                                                          .build())
                       .path("/")
                       .inlinePolicies(policies)
                       .build();
  }

  private static Role ecsTaskRole(Construct scope, ApplicationEnvironment appEnv,
                                  AECService.InputParameters params) {
    var iamPrincipal = ServicePrincipal.Builder.create(AWS_SERVICE_ECS_TASKS_AMAZON_COM).build();
    var roleBuilder = Role.Builder.create(scope, "ecsTaskRole").assumedBy(iamPrincipal).path("/");

    var taskRolePolicyStatements = params.getTaskRolePolicyStatements();
    if (taskRolePolicyStatements != null && !taskRolePolicyStatements.isEmpty()) {
      var policyDocument = PolicyDocument.Builder.create()
                                                 .statements(taskRolePolicyStatements)
                                                 .build();
      roleBuilder.inlinePolicies(Map.of(appEnv.prefixed("ecsTaskRolePolicy"), policyDocument));
    }

    return roleBuilder.build();
  }

  private static String dockerImageRepositoryUrl(Construct scope, InputParameters params,
                                                 IGrantable ecsTaskExecutionRole) {
    var dockerImage = Objects.requireNonNull(params.getDockerImage());
    if (dockerImage.isEcrSource()) {
      var dockerRepositoryName = Objects.requireNonNull(dockerImage.getDockerRepositoryName());
      var dockerRepository = Repository.fromRepositoryName(scope, "ecrRepository",
                                                           dockerRepositoryName);
      dockerRepository.grantPullPush(ecsTaskExecutionRole);
      return dockerRepository.repositoryUriForTag(dockerImage.getDockerImageTag());
    }
    return Objects.requireNonNull(dockerImage.getDockerImageUrl());
  }

  private static CfnTaskDefinition.ContainerDefinitionProperty containerDefinitionProperty(Environment awsEnv,
                                                                                           LogGroup logGroup,
                                                                                           ApplicationEnvironment appEnv,
                                                                                           InputParameters params,
                                                                                           String dockerImageRepositoryUrl) {
    var logConfOptions = Map.of("awslogs-group", logGroup.getLogGroupName(),
                                "awslogs-region", Objects.requireNonNull(awsEnv.getRegion()),
                                "awslogs-stream-prefix", appEnv.prefixed("stream"),
                                "awslogs-datetime-format", params.getAwsLogsDateTimeFormat());
    var logConf = CfnTaskDefinition.LogConfigurationProperty.builder()
                                                            .logDriver(LOG_DRIVER_AWS_LOGS)
                                                            .options(logConfOptions)
                                                            .build();
    var portMapping = CfnTaskDefinition.PortMappingProperty.builder()
                                                           .containerPort(params.getContainerPort())
                                                           .build();
    var environmentVars = cfnTaskDefKeyValuePropertiesFrom(params.getEnvironmentVariables());

    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-ecs-taskdefinition.html
    return CfnTaskDefinition.ContainerDefinitionProperty.builder()
                                                        .name(containerName(appEnv))
                                                        .cpu(params.getCpu())
                                                        .memory(params.getMemory())
                                                        .image(dockerImageRepositoryUrl)
                                                        .logConfiguration(logConf)
                                                        .portMappings(List.of(portMapping))
                                                        .environment(environmentVars)
                                                        .build();
  }

  private static String containerName(ApplicationEnvironment appEnv) {
    return appEnv.prefixed("container");
  }

  private static List<CfnTaskDefinition.KeyValuePairProperty> cfnTaskDefKeyValuePropertiesFrom(Map<String, String> source) {
    return source.entrySet().stream()
                 .map(AECService::cfnTaskDefKeyValuePropertyFrom)
                 .collect(Collectors.toList());
  }

  private static CfnTaskDefinition.KeyValuePairProperty cfnTaskDefKeyValuePropertyFrom(Map.Entry<String, String> entry) {
    return CfnTaskDefinition.KeyValuePairProperty.builder()
                                                 .name(entry.getKey())
                                                 .value(entry.getValue())
                                                 .build();
  }

  private static CfnTaskDefinition taskDefinition(Construct scope, InputParameters params,
                                                  IRole ecsTaskExecutionRole,
                                                  Role ecsTaskRole,
                                                  CfnTaskDefinition.ContainerDefinitionProperty containerDef) {
    return CfnTaskDefinition.Builder.create(scope, "taskDefinition")
                                    .cpu(String.valueOf(params.getCpu()))
                                    .memory(String.valueOf(params.getMemory()))
                                    .networkMode(NETWORK_MODE_AWS_VPC)
                                    // https://docs.aws.amazon.com/AmazonECS/latest/userguide/fargate-task-defs.html
                                    .requiresCompatibilities(List.of(LUNCH_TYPE_FARGATE))
                                    .executionRoleArn(ecsTaskExecutionRole.getRoleArn())
                                    .taskRoleArn(ecsTaskRole.getRoleArn())
                                    .containerDefinitions(List.of(containerDef))
                                    .build();
  }

  private static CfnSecurityGroup ecsSecurityGroup(Construct scope, InputParameters params,
                                                   Network.OutputParameters netOutputParameters) {
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-ec2-security-group.html
    var secGroup = CfnSecurityGroup.Builder.create(scope, "ecsSecurityGroup")
                                           .vpcId(netOutputParameters.getVpcId())
                                           .groupDescription("Security Group for the ECS container")
                                           .build();
    // allow the ECS containers to access each other
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-ec2-security-group-ingress.html
    CfnSecurityGroupIngress.Builder.create(scope, "ecsIngressFromSelf")
                                   .ipProtocol(Network.ALL_IP_PROTOCOLS)
                                   .sourceSecurityGroupId(secGroup.getAttrGroupId())
                                   .groupId(secGroup.getAttrGroupId())
                                   .build();
    // allow the load balancer to access the container
    CfnSecurityGroupIngress.Builder.create(scope, "ecsIngressFromLoadBalancer")
                                   .ipProtocol(Network.ALL_IP_PROTOCOLS)
                                   .sourceSecurityGroupId(netOutputParameters.getLoadbalancerSecurityGroupId())
                                   .groupId(secGroup.getAttrGroupId())
                                   .build();
    allowIngressFromEcsToSecurityGroupIds(scope, secGroup.getAttrGroupId(),
                                          params.getSecurityGroupIdsToGrantIngressFromEcs());
    return secGroup;
  }

  private static void allowIngressFromEcsToSecurityGroupIds(Construct scope, String ecsSecGroupId,
                                                            Collection<String> secGroupsToGrantIngressFromEcs) {
    IntFunction<String> idFn = counter -> String.format("securityGroupIngress%s", counter);
    AtomicInteger counter = new AtomicInteger(1);
    Optional.ofNullable(secGroupsToGrantIngressFromEcs)
            .orElse(emptyList())
            .forEach(id -> CfnSecurityGroupIngress.Builder.create(scope, idFn.apply(counter.getAndIncrement()))
                                                          .sourceSecurityGroupId(ecsSecGroupId)
                                                          .groupId(id)
                                                          .ipProtocol(Network.ALL_IP_PROTOCOLS)
                                                          .build());
  }

  private static CfnService cfnService(Construct scope, CfnTaskDefinition taskDefinition,
                                       CfnTargetGroup targetGroup, CfnSecurityGroup securityGroup,
                                       ApplicationEnvironment appEnv, InputParameters params,
                                       Network.OutputParameters netOutputParameters) {
    var deployConf = CfnService.DeploymentConfigurationProperty
        .builder()
        .maximumPercent(params.getMaximumInstancesPercent())
        .minimumHealthyPercent(params.getMinimumHealthyInstancesPercent())
        .build();
    var loadBalancerConf = CfnService.LoadBalancerProperty.builder()
                                                          .containerName(containerName(appEnv))
                                                          .containerPort(params.getContainerPort())
                                                          .targetGroupArn(targetGroup.getRef())
                                                          .build();
    var vpcConf = CfnService.AwsVpcConfigurationProperty.builder()
                                                        .assignPublicIp(ASSIGN_PUBLIC_IP_ENABLED)
                                                        .securityGroups(List.of(securityGroup.getAttrGroupId()))
                                                        .subnets(netOutputParameters.getPublicSubnets())
                                                        .build();
    var netProps = CfnService.NetworkConfigurationProperty.builder()
                                                          .awsvpcConfiguration(vpcConf)
                                                          .build();

    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-ecs-service.html
    return CfnService.Builder.create(scope, "ecsService")
                             .cluster(netOutputParameters.getEcsClusterName())
                             .launchType(LUNCH_TYPE_FARGATE)
                             .deploymentConfiguration(deployConf)
                             .desiredCount(params.getDesiredInstancesCount())
                             .taskDefinition(taskDefinition.getRef())
                             .loadBalancers(List.of(loadBalancerConf))
                             .networkConfiguration(netProps)
                             .build();
  }
  // endregion

  // region inner classes
  public InputParameters newInputParameters(DockerImage dockerImage,
                                            Map<String, String> environmentVariables,
                                            List<String> securityGroupIdsToGrantIngressFromEcs) {
    return new InputParameters(Objects.requireNonNull(dockerImage),
                               Objects.requireNonNull(environmentVariables),
                               Objects.requireNonNull(securityGroupIdsToGrantIngressFromEcs));
  }

  public DockerImage newDockerImage(String dockerRepositoryName, String dockerImageTag,
                                    String dockerImageUrl) {
    return new DockerImage(Objects.requireNonNull(dockerRepositoryName),
                           Objects.requireNonNull(dockerImageTag),
                           Objects.requireNonNull(dockerImageUrl));
  }

  @Getter(AccessLevel.PACKAGE)
  @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
  @Setter
  public static final class InputParameters {
    private final DockerImage dockerImage;
    private final Map<String, String> environmentVariables;
    private final List<String> securityGroupIdsToGrantIngressFromEcs;

    private List<PolicyStatement> taskRolePolicyStatements = emptyList();
    private int healthCheckIntervalSeconds = 15;
    private String healthCheckPath = "/";
    private int containerPort = 8080;
    private String containerProtocol = "HTTP";
    private int healthCheckTimeoutSeconds = 5;
    private int healthyThresholdCount = 2;
    private int unhealthyThresholdCount = 8;
    private RetentionDays logRetention = RetentionDays.ONE_WEEK;
    private int cpu = 256;
    private int memory = 512;
    private int desiredInstancesCount = 2;
    private int maximumInstancesPercent = 200;
    private int minimumHealthyInstancesPercent = 50;
    private boolean stickySessionsEnabled = false;
    private int stickySessionsCookieDuration = 3600;
    private String awsLogsDateTimeFormat = "%Y-%m-%dT%H:%M:%S.%f%z";
  }

  @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
  @Getter(AccessLevel.PACKAGE)
  public static class DockerImage {
    /**
     * Name of an ECR repository.
     */
    private final String dockerRepositoryName;
    private final String dockerImageTag;
    private final String dockerImageUrl;

    boolean isEcrSource() {
      return this.dockerRepositoryName != null;
    }
  }

  @Getter(AccessLevel.PACKAGE)
  @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
  private static class ServiceListenerRules {
    private final CfnListenerRule httpsRule;
    private final CfnListenerRule httpRule;
  }
  // endregion
}
