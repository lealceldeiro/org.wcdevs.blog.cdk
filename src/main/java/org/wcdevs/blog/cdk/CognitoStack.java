package org.wcdevs.blog.cdk;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.customresources.SdkCallsPolicyOptions;
import software.amazon.awscdk.services.cognito.AccountRecovery;
import software.amazon.awscdk.services.cognito.AutoVerifiedAttrs;
import software.amazon.awscdk.services.cognito.CognitoDomainOptions;
import software.amazon.awscdk.services.cognito.IUserPool;
import software.amazon.awscdk.services.cognito.Mfa;
import software.amazon.awscdk.services.cognito.OAuthFlows;
import software.amazon.awscdk.services.cognito.OAuthScope;
import software.amazon.awscdk.services.cognito.OAuthSettings;
import software.amazon.awscdk.services.cognito.PasswordPolicy;
import software.amazon.awscdk.services.cognito.SignInAliases;
import software.amazon.awscdk.services.cognito.StandardAttribute;
import software.amazon.awscdk.services.cognito.StandardAttributes;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.cognito.UserPoolClient;
import software.amazon.awscdk.services.cognito.UserPoolClientIdentityProvider;
import software.amazon.awscdk.services.cognito.UserPoolDomain;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.wcdevs.blog.cdk.Util.DASH_JOINER;
import static org.wcdevs.blog.cdk.Util.joinedString;
import static software.amazon.awscdk.customresources.AwsCustomResourcePolicy.ANY_RESOURCE;

public final class CognitoStack extends Stack {
  // https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-app-integration.html
  static final String DEFAULT_COGNITO_LOGOUT_URL_TPL
      = "https://%s.auth.%s.amazoncognito.com/logout";
  private static final String DEFAULT_COGNITO_OAUTH_LOGIN_URL_TEMPLATE
      = "%s/login/oauth2/code/cognito";

  private static final String PARAM_USER_POOL_CLIENT_SECRET_ARN = "userPoolClientSecretArn";
  private static final String PARAM_USER_POOL_LOGOUT_URL = "userPoolLogoutUrl";
  private static final String PARAM_USER_POOL_PROVIDER_URL = "userPoolProviderUrl";

  private static final String CONSTRUCT_NAME = "cognito-stack";

  public static final String USER_POOL_CLIENT_SECRET_HOLDER = "userPoolClientSecret";
  public static final String USER_POOL_ID_HOLDER = "userPoolId";
  public static final String USER_POOL_CLIENT_ID_HOLDER = "userPoolClientId";
  public static final String USER_POOL_CLIENT_NAME_HOLDER = "userPoolClientName";

  private CognitoStack(Construct scope, String id, StackProps props) {
    super(scope, id, props);
  }

  public static CognitoStack newInstance(Construct scope, Environment awsEnvironment,
                                         String environmentName, InputParameters inputParameters) {
    var inParams = Objects.requireNonNull(inputParameters);
    var region = Objects.requireNonNull(awsEnvironment.getRegion());

    var envName = Objects.requireNonNull(environmentName);
    var stackName = joinedString(DASH_JOINER, envName, CONSTRUCT_NAME);
    var cognitoProps = StackProps.builder().stackName(stackName).env(awsEnvironment).build();
    var cognitoStack = new CognitoStack(scope, stackName, cognitoProps);

    var userPool = userPool(cognitoStack, inParams, envName);
    createUserPoolDomain(cognitoStack, userPool, inParams);

    var clientsSecretBaseName = joinedString(DASH_JOINER, envName, USER_POOL_CLIENT_SECRET_HOLDER);
    createUserPoolClients(cognitoStack, userPool, inParams.getUserPoolClientConfigurations())
        // and
        .forEach(clientWrapper -> {
          var secretArn = createUserPoolClientSecret(cognitoStack, region, userPool.getUserPoolId(),
                                                     clientWrapper, clientsSecretBaseName);
          var clientName = clientWrapper.getClient().getUserPoolClientName();
          var arnParamHolder = clientSecretArnParamHolder(clientName);
          createStringParameter(cognitoStack, envName, arnParamHolder, secretArn);
        });

    createStringParameter(cognitoStack, envName, PARAM_USER_POOL_LOGOUT_URL,
                          inParams.getFullLogoutUrlForRegion(region));
    createStringParameter(cognitoStack, envName, PARAM_USER_POOL_PROVIDER_URL,
                          userPool.getUserPoolProviderUrl());

    return cognitoStack;
  }

  private static Stream<UserPoolClientWrapper> createUserPoolClients(Stack scope,
                                                                     IUserPool userPool,
                                                                     Collection<UserPoolClientParameter> clientParams) {
    return clientParams.stream().map(clientParam -> userPoolClient(scope, userPool, clientParam));
  }

  private static String clientSecretArnParamHolder(String userPoolClientName) {
    return PARAM_USER_POOL_CLIENT_SECRET_ARN + userPoolClientName;
  }

  private static String clientName(String applicationName) {
    return joinedString(DASH_JOINER, applicationName, "up", "client");
  }

  // region helpers
  private static UserPool userPool(Stack scope, InputParameters inParams, String environment) {
    var autoVerifyEmail = AutoVerifiedAttrs.builder()
                                           .email(inParams.isSignInAutoVerifyEmail())
                                           .phone(inParams.isSignInAutoVerifyPhone())
                                           .build();
    var signInAliases = SignInAliases.builder()
                                     .username(inParams.isSignInAliasUsername())
                                     .email(inParams.isSignInAliasEmail())
                                     .phone(inParams.isSignInAliasPhone())
                                     .build();
    var signInEmailAttributes = StandardAttribute.builder()
                                                 .required(inParams.isSignInEmailRequired())
                                                 .mutable(inParams.isSignInEmailMutable())
                                                 .build();
    var signInPhoneAttributes = StandardAttribute.builder()
                                                 .required(inParams.isSignInPhoneRequired())
                                                 .mutable(inParams.isSignInPhoneMutable())
                                                 .build();
    var signInAttributes = StandardAttributes.builder()
                                             .email(signInEmailAttributes)
                                             .phoneNumber(signInPhoneAttributes)
                                             .build();
    var tempPasswordValidityDays = Duration.days(inParams.getTempPasswordValidityInDays());
    var passwordPolicy = PasswordPolicy.builder()
                                       .requireLowercase(inParams.isPasswordRequireLowercase())
                                       .requireDigits(inParams.isPasswordRequireDigits())
                                       .requireSymbols(inParams.isPasswordRequireSymbols())
                                       .requireUppercase(inParams.isPasswordRequireUppercase())
                                       .minLength(inParams.getPasswordMinLength())
                                       .tempPasswordValidity(tempPasswordValidityDays)
                                       .build();
    return UserPool.Builder.create(scope, "userPool")
                           .userPoolName(joinedString(DASH_JOINER, environment, "user-pool"))
                           .selfSignUpEnabled(inParams.isSelfSignUpEnabled())
                           .accountRecovery(inParams.getAccountRecovery())
                           .autoVerify(autoVerifyEmail)
                           .signInAliases(signInAliases)
                           .signInCaseSensitive(inParams.isSignInCaseSensitive())
                           .standardAttributes(signInAttributes)
                           .mfa(inParams.getMfa())
                           .passwordPolicy(passwordPolicy)
                           .build();
  }

  private static UserPoolClientWrapper userPoolClient(Stack scope, IUserPool userPool,
                                                      UserPoolClientParameter clientParam) {
    var oauthBuilder = OAuthSettings.builder();

    if (!clientParam.isOauthDisabled()) {
      var callbacks = join(clientParam.getUserPoolOauthCallBackUrls(),
                           Optional.ofNullable(clientParam.getAppLoginUrl()).orElse("")
                          ).stream().filter(s -> !s.isEmpty()).collect(toList());

      oauthBuilder.callbackUrls(callbacks).logoutUrls(List.of(clientParam.getApplicationUrl()));

      if (clientParam.isThereAScopeConfigured()) {
        oauthBuilder.scopes(clientParam.getScopes());
      }
      if (clientParam.isThereAFlowEnabled()) {
        oauthBuilder
            .flows(OAuthFlows
                       .builder()
                       .authorizationCodeGrant(clientParam.isFlowAuthorizationCodeGrantEnabled())
                       .implicitCodeGrant(clientParam.isFlowImplicitCodeGrantEnabled())
                       .clientCredentials(clientParam.isFlowClientCredentialsEnabled())
                       .build());
      }
    }

    var identityProviders = join(clientParam.getUserPoolSuppoertedIdentityProviders(),
                                 UserPoolClientIdentityProvider.COGNITO);

    var clientName = clientName(clientParam.getApplicationName());
    var builder = UserPoolClient.Builder
        .create(scope, "userPoolClient" + clientName)
        .userPoolClientName(clientName)
        .generateSecret(clientParam.isGenerateSecretEnabled())
        .userPool(userPool)
        .supportedIdentityProviders(identityProviders)
        .accessTokenValidity(clientParam.getAccessTokenValidity())
        .idTokenValidity(clientParam.getIdTokenValidity())
        .refreshTokenValidity(clientParam.getRefreshTokenValidity())
        .enableTokenRevocation(clientParam.isTokenRevocationEnabled())
        .preventUserExistenceErrors(clientParam.isReturnGenericErrorOnLoginFailed());

    var userPoolClient = clientParam.isOauthDisabled()
                         ? builder.disableOAuth(true).build()
                         : builder.oAuth(oauthBuilder.build()).build();
    return new UserPoolClientWrapper(userPoolClient, clientParam.isGenerateSecretEnabled());
  }

  private static void createUserPoolDomain(Stack scope, IUserPool userPool,
                                           InputParameters inParams) {
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-cognito-userpooldomain.html#cfn-cognito-userpooldomain-customdomainconfig
    var cognitoDomain = CognitoDomainOptions.builder()
                                            .domainPrefix(inParams.getLoginPageDomainPrefix())
                                            .build();
    UserPoolDomain.Builder.create(scope, "userPoolDomain")
                          .userPool(userPool)
                          .cognitoDomain(cognitoDomain)
                          .build();
  }

  private static void createStringParameter(Stack scope, String envName, String id, String value) {
    StringParameter.Builder.create(scope, parameterId(id))
                           .parameterName(createParameterName(envName, id))
                           .stringValue(value)
                           .build();
  }

  private static String parameterId(String id) {
    return joinedString(DASH_JOINER, CONSTRUCT_NAME, "param", id);
  }

  private static String createParameterName(String envName, String parameterName) {
    return joinedString(DASH_JOINER, envName, CONSTRUCT_NAME, parameterName);
  }

  @SafeVarargs
  private static <T> List<T> join(Collection<? extends T> additional, T... elements) {
    return Stream.concat(Stream.of(Objects.requireNonNull(elements)),
                         Optional.ofNullable(additional).orElse(emptyList()).stream())
                 .toList();
  }

  private static String createUserPoolClientSecret(Stack scope, String awsRegion, String userPoolId,
                                                   UserPoolClientWrapper clientWrapper,
                                                   String secretName) {
    var userPoolClientId = clientWrapper.getClient().getUserPoolClientId();
    var clientName = clientWrapper.getClient().getUserPoolClientName();
    var userPoolClientSecretValue = clientWrapper.isSecretGenerated()
                                    ? userPoolClientSecretValue(scope, awsRegion, userPoolId,
                                                                userPoolClientId, clientName)
                                    : "";
    var secretTpl = String.format("{\"%s\": \"%s\",\"%s\": \"%s\",\"%s\": \"%s\",\"%s\": \"%s\"}",
                                  USER_POOL_ID_HOLDER, userPoolId,
                                  USER_POOL_CLIENT_ID_HOLDER, userPoolClientId,
                                  USER_POOL_CLIENT_NAME_HOLDER, clientName,
                                  USER_POOL_CLIENT_SECRET_HOLDER, userPoolClientSecretValue);
    var secretString = SecretStringGenerator.builder()
                                            .secretStringTemplate(secretTpl)
                                            // to please AWS CDK
                                            // see https://github.com/aws/aws-cdk/issues/5810
                                            .generateStringKey("ignored")
                                            .passwordLength(10)
                                            .build();
    return Secret.Builder.create(scope, USER_POOL_CLIENT_SECRET_HOLDER + clientName)
                         .secretName(secretName + userPoolClientId)
                         .description("Secret holding the user pool client (" + clientName
                                      + ") secret values")
                         .generateSecretString(secretString)
                         .build()
                         .getSecretArn();
  }

  private static String userPoolClientSecretValue(Stack scope, String awsRegion, String userPoolId,
                                                  String userPoolClientId, String clientName) {
    // The UserPoolClient secret, can't be accessed directly
    // This custom resource will call the AWS API to get the secret,
    // See: https://github.com/aws/aws-cdk/issues/7225
    var parameters = Map.of("UserPoolId", userPoolId, "ClientId", userPoolClientId);
    var physicalResourceId = PhysicalResourceId.of(userPoolClientId);
    var userPoolClientMetadata = AwsSdkCall.builder()
                                           .region(awsRegion)
                                           .service("CognitoIdentityServiceProvider")
                                           .action("describeUserPoolClient")
                                           .parameters(parameters)
                                           .physicalResourceId(physicalResourceId)
                                           .build();

    var resourceType = "Custom::DescribeCognitoUserPoolClient";
    var policy = AwsCustomResourcePolicy.fromSdkCalls(SdkCallsPolicyOptions.builder()
                                                                           .resources(ANY_RESOURCE)
                                                                           .build());
    var userPoolResource = AwsCustomResource.Builder.create(scope, "describeUserPool" + clientName)
                                                    .resourceType(resourceType)
                                                    .installLatestAwsSdk(false)
                                                    .onUpdate(userPoolClientMetadata)
                                                    .onCreate(userPoolClientMetadata)
                                                    .policy(policy)
                                                    .build();
    return userPoolResource.getResponseField("UserPoolClient.ClientSecret");
  }
  // endregion

  // region get output params
  public static ISecret getUserPoolClientSecret(Stack scope, ApplicationEnvironment appEnv) {
    var clientName = clientName(appEnv.getApplicationName());
    var secConstructId = joinedString(DASH_JOINER, clientSecretArnParamHolder(clientName), "sec");
    var arn = getParameterUserPoolClientSecretArn(scope, appEnv);
    return Secret.fromSecretCompleteArn(scope, secConstructId, arn);
  }

  /**
   * Returns the output parameters from this stack creation except the user pool clients secret
   * ARNs. To retrieve those a call to
   * {@link CognitoStack#getParameterUserPoolClientSecretArn(Stack, ApplicationEnvironment)}
   * should be executed for each application environment for which an user pool client was
   * configured.
   *
   * @param scope  Scope to retrieve the parameters from.
   * @param appEnv Environment name associated to these parameters.
   *
   * @return An {@link OutputParameters} instance with the values.
   *
   * @see CognitoStack#getParameterUserPoolClientSecretArn(Stack, ApplicationEnvironment)
   */
  public static OutputParameters getOutputParameters(Stack scope, String appEnv) {
    return new OutputParameters(getParameterLogoutUrl(scope, appEnv),
                                getParameterUserPoolProviderUrl(scope, appEnv));
  }

  public static String getParameter(Stack scope, String appEnv, String id) {
    return StringParameter.fromStringParameterName(scope, parameterId(id),
                                                   createParameterName(appEnv, id))
                          .getStringValue();
  }

  public static String getParameterUserPoolClientSecretArn(Stack scope,
                                                           ApplicationEnvironment appEnv) {
    var clientName = clientName(appEnv.getApplicationName());
    return getParameter(scope, appEnv.getEnvironmentName(), clientSecretArnParamHolder(clientName));
  }

  public static String getParameterLogoutUrl(Stack scope, String appEnv) {
    return getParameter(scope, appEnv, PARAM_USER_POOL_LOGOUT_URL);
  }

  public static String getParameterUserPoolProviderUrl(Stack scope, String appEnv) {
    return getParameter(scope, appEnv, PARAM_USER_POOL_PROVIDER_URL);
  }
  // endregion

  @lombok.Builder
  @Getter(AccessLevel.PACKAGE)
  public static final class InputParameters {
    @lombok.Builder.Default
    private String cognitoLogoutUrlTemplate = DEFAULT_COGNITO_LOGOUT_URL_TPL;
    private String loginPageDomainPrefix;

    private boolean selfSignUpEnabled;
    @lombok.Builder.Default
    private AccountRecovery accountRecovery = AccountRecovery.EMAIL_ONLY;
    private boolean signInAutoVerifyEmail;
    private boolean signInAutoVerifyPhone;
    @lombok.Builder.Default
    private boolean signInAliasUsername = true;
    @lombok.Builder.Default
    private boolean signInAliasEmail = true;
    private boolean signInAliasPhone;
    @lombok.Builder.Default
    private boolean signInCaseSensitive = true;
    @lombok.Builder.Default
    private boolean signInEmailRequired = true;
    private boolean signInEmailMutable;
    private boolean signInPhoneRequired;
    private boolean signInPhoneMutable;
    @lombok.Builder.Default
    private Mfa mfa = Mfa.OFF;
    @lombok.Builder.Default
    private boolean passwordRequireLowercase = true;
    @lombok.Builder.Default
    private boolean passwordRequireDigits = true;
    @lombok.Builder.Default
    private boolean passwordRequireSymbols = true;
    @lombok.Builder.Default
    private boolean passwordRequireUppercase = true;
    @lombok.Builder.Default
    private int passwordMinLength = 8;
    @lombok.Builder.Default
    private int tempPasswordValidityInDays = 7;

    @lombok.Builder.Default
    private List<UserPoolClientParameter> userPoolClientConfigurations = emptyList();

    String getFullLogoutUrlForRegion(String region) {
      return String.format(getCognitoLogoutUrlTemplate(), getLoginPageDomainPrefix(), region);
    }
  }

  @lombok.Builder
  @Getter(AccessLevel.PACKAGE)
  public static final class UserPoolClientParameter {
    @lombok.Builder.Default
    private String cognitoOauthLoginUrlTemplate = DEFAULT_COGNITO_OAUTH_LOGIN_URL_TEMPLATE;

    private String applicationName;
    private String applicationUrl;

    @lombok.Builder.Default
    private Collection<String> userPoolOauthCallBackUrls = emptyList();
    private boolean flowAuthorizationCodeGrantEnabled;
    private boolean flowImplicitCodeGrantEnabled;
    private boolean flowClientCredentialsEnabled;
    @lombok.Builder.Default
    private List<OAuthScope> scopes = emptyList();
    @lombok.Builder.Default
    private Collection<UserPoolClientIdentityProvider> userPoolSuppoertedIdentityProviders
        = emptyList();
    @lombok.Builder.Default
    private Duration accessTokenValidity = Duration.hours(1);
    @lombok.Builder.Default
    private Duration idTokenValidity = Duration.hours(1);
    @lombok.Builder.Default
    private Duration refreshTokenValidity = Duration.days(15);
    private boolean tokenRevocationEnabled;
    @lombok.Builder.Default
    private boolean returnGenericErrorOnLoginFailed = true;
    @lombok.Builder.Default
    private boolean generateSecretEnabled = true;

    private boolean oauthDisabled;

    String getAppLoginUrl() {
      return String.format(getCognitoOauthLoginUrlTemplate(), getApplicationUrl());
    }

    boolean isThereAFlowEnabled() {
      return isFlowAuthorizationCodeGrantEnabled() || isFlowImplicitCodeGrantEnabled()
             || isFlowClientCredentialsEnabled();
    }

    boolean isThereAScopeConfigured() {
      return Objects.nonNull(getScopes()) && !getScopes().isEmpty();
    }
  }

  @Getter
  @AllArgsConstructor(access = AccessLevel.PACKAGE)
  public static final class OutputParameters {
    private final String logoutUrl;
    private final String providerUrl;
  }

  @AllArgsConstructor
  @Getter(AccessLevel.PACKAGE)
  static final class UserPoolClientWrapper {
    private final UserPoolClient client;
    private final boolean secretGenerated;
  }
}
