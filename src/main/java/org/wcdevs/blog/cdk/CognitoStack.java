package org.wcdevs.blog.cdk;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.wcdevs.blog.cdk.Util.DASH_JOINER;
import static org.wcdevs.blog.cdk.Util.joinedString;
import static software.amazon.awscdk.customresources.AwsCustomResourcePolicy.ANY_RESOURCE;

public final class CognitoStack extends Stack {
  private static final String PARAM_USER_POOL_ID = "userPoolId";
  private static final String PARAM_USER_POOL_CLIENT_ID = "userPoolClientId";
  private static final String PARAM_USER_POOL_CLIENT_NAME = "userPoolClientName";
  private static final String PARAM_USER_POOL_CLIENT_SECRET_ARN = "userPoolClientSecretArn";
  private static final String USER_POOL_CLIENT_SECRET = "userPoolClientSecret";
  private static final String PARAM_USER_POOL_LOGOUT_URL = "userPoolLogoutUrl";
  private static final String PARAM_USER_POOL_PROVIDER_URL = "userPoolProviderUrl";

  private static final String CONSTRUCT_NAME = "cognito-stack";

  public static final String USER_POOL_CLIENT_SECRET_HOLDER = "userPoolClientSecretValue";

  private CognitoStack(Construct scope, String id, StackProps props) {
    super(scope, id, props);
  }

  public static CognitoStack newInstance(Construct scope, String id, Environment awsEnvironment,
                                         ApplicationEnvironment applicationEnvironment,
                                         InputParameters inputParameters) {
    var inParams = Objects.requireNonNull(inputParameters);
    var appEnv = Objects.requireNonNull(applicationEnvironment);
    var region = Objects.requireNonNull(awsEnvironment.getRegion());

    var stackName = appEnv.prefixed(joinedString(DASH_JOINER, id, CONSTRUCT_NAME));
    var cognitoPros = StackProps.builder()
                                .stackName(stackName)
                                .env(awsEnvironment)
                                .build();
    var cognitoStack = new CognitoStack(scope, stackName, cognitoPros);

    var userPool = userPool(cognitoStack, inParams, appEnv);
    var userPoolClient = userPoolClient(cognitoStack, userPool, inParams);
    var logoutUrl = inParams.getFullLogoutUrlForRegion(region);

    createUserPoolDomain(cognitoStack, userPool, inParams);

    var secretName = appEnv.prefixed(USER_POOL_CLIENT_SECRET);
    // FIXME: do not store actual secret value
//    var userPoolClientSecret = userPoolClientSecret(cognitoStack, region, userPool.getUserPoolId(),
//                                                    userPoolClient.getUserPoolClientId(),
//                                                    secretName);
    var clientSecretValue = userPoolClientSecretValue(cognitoStack, region,
                                                      userPool.getUserPoolId(),
                                                      userPoolClient.getUserPoolClientId());

    createStringParameter(cognitoStack, appEnv, PARAM_USER_POOL_ID, userPool.getUserPoolId());
    createStringParameter(cognitoStack, appEnv, PARAM_USER_POOL_CLIENT_ID,
                          userPoolClient.getUserPoolClientId());
    createStringParameter(cognitoStack, appEnv, PARAM_USER_POOL_CLIENT_NAME,
                          userPoolClient.getUserPoolClientName());
    createStringParameter(cognitoStack, appEnv, PARAM_USER_POOL_CLIENT_SECRET_ARN,
                          clientSecretValue);
    createStringParameter(cognitoStack, appEnv, PARAM_USER_POOL_LOGOUT_URL, logoutUrl);
    createStringParameter(cognitoStack, appEnv, PARAM_USER_POOL_PROVIDER_URL,
                          userPool.getUserPoolProviderUrl());

    return cognitoStack;
  }

  // FIXME: make method available again when security fix is done
  static ISecret getUserPoolClientSecret(Construct scope, OutputParameters outParams) {
    var arn = Objects.requireNonNull(outParams.getUserPoolClientSecretArn());
    return Secret.fromSecretCompleteArn(scope, USER_POOL_CLIENT_SECRET, arn);
  }

  // region helpers
  private static UserPool userPool(Construct scope, InputParameters inParams,
                                   ApplicationEnvironment applicationEnvironment) {
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
                           .userPoolName(applicationEnvironment.prefixed("user-pool"))
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

  private static UserPoolClient userPoolClient(Construct scope, IUserPool userPool,
                                               InputParameters inParams) {
    var callbackUrls = join(inParams.getUserPoolOauthCallBackUrls(), inParams.getAppLoginUrl());
    var logoutUrls = List.of(inParams.getApplicationUrl());
    var flows = OAuthFlows.builder()
                          .authorizationCodeGrant(inParams.isFlowAuthorizationCodeGrantEnabled())
                          .implicitCodeGrant(inParams.isFlowImplicitCodeGrantEnabled())
                          .clientCredentials(inParams.isFlowClientCredentialsEnabled())
                          .build();
    var oAuthConf = OAuthSettings.builder()
                                 .callbackUrls(callbackUrls)
                                 .logoutUrls(logoutUrls)
                                 .flows(flows)
                                 .scopes(List.of(OAuthScope.EMAIL, OAuthScope.OPENID,
                                                 OAuthScope.PROFILE))
                                 .build();
    var identityProviders = join(inParams.getUserPoolSuppoertedIdentityProviders(),
                                 UserPoolClientIdentityProvider.COGNITO);

    return UserPoolClient.Builder.create(scope, "userPoolClient")
                                 .userPoolClientName(inParams.getApplicationName() + "-client")
                                 .generateSecret(inParams.isUserPoolGenerateSecret())
                                 .userPool(userPool)
                                 .oAuth(oAuthConf)
                                 .supportedIdentityProviders(identityProviders)
                                 .build();
  }

  private static void createUserPoolDomain(Construct scope, IUserPool userPool,
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

  private static void createStringParameter(Construct scope, ApplicationEnvironment appEnv,
                                            String id, String value) {
    StringParameter.Builder.create(scope, id)
                           .parameterName(createParameterName(appEnv, id))
                           .stringValue(value)
                           .build();
  }

  private static String createParameterName(ApplicationEnvironment appEnv, String parameterName) {
    return joinedString(DASH_JOINER, appEnv.getEnvironmentName(), CONSTRUCT_NAME, parameterName);
  }

  @SafeVarargs
  private static <T> List<T> join(Collection<? extends T> additional, T... elements) {
    return Stream.concat(Stream.of(Objects.requireNonNull(elements)),
                         Objects.requireNonNull(additional.stream()))
                 .collect(toList());
  }

  private static ISecret userPoolClientSecret(Stack scope, String awsRegion, String userPoolId,
                                              String userPoolClientId, String secretName) {
    var userPoolClientSecretValue = userPoolClientSecretValue(scope, awsRegion, userPoolId,
                                                              userPoolClientId);
    var secretTemplate = String.format("{\"%s\":\"%s\"}", USER_POOL_CLIENT_SECRET_HOLDER,
                                       userPoolClientSecretValue);
    var secretString = SecretStringGenerator.builder()
                                            .secretStringTemplate(secretTemplate)
                                            .build();
    return Secret.Builder.create(scope, USER_POOL_CLIENT_SECRET)
                         .secretName(secretName)
                         .description("User pool client secret")
                         .generateSecretString(secretString)
                         .build();
  }

  private static String userPoolClientSecretValue(Stack scope, String awsRegion,
                                                  String userPoolId, String userPoolClientId) {
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
    var userPoolResource = AwsCustomResource.Builder.create(scope, "describeUserPool")
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
  public static OutputParameters getOutputParameters(Construct scope,
                                                     ApplicationEnvironment appEnvironment) {
    return new OutputParameters(getParameterUserPoolId(scope, appEnvironment),
                                getParameterUserPoolClientId(scope, appEnvironment),
                                getParameterUserPoolClientName(scope, appEnvironment),
                                getParameterUserPoolClientSecretArn(scope, appEnvironment),
                                getParameterLogoutUrl(scope, appEnvironment),
                                getParameterUserPoolProviderUrl(scope, appEnvironment));
  }

  public static String getParameter(Construct scope, ApplicationEnvironment applicationEnvironment,
                                    String id) {
    return StringParameter.fromStringParameterName(scope, id,
                                                   createParameterName(applicationEnvironment, id))
                          .getStringValue();
  }

  public static String getParameterUserPoolId(Construct scope,
                                              ApplicationEnvironment applicationEnvironment) {
    return getParameter(scope, applicationEnvironment, PARAM_USER_POOL_ID);
  }

  public static String getParameterUserPoolClientId(Construct scope,
                                                    ApplicationEnvironment applicationEnvironment) {
    return getParameter(scope, applicationEnvironment, PARAM_USER_POOL_CLIENT_ID);
  }

  public static String getParameterUserPoolClientName(Construct scope,
                                                      ApplicationEnvironment applicationEnvironment) {
    return getParameter(scope, applicationEnvironment, PARAM_USER_POOL_CLIENT_NAME);
  }

  public static String getParameterUserPoolClientSecretArn(Construct scope,
                                                           ApplicationEnvironment appEnvironment) {
    return getParameter(scope, appEnvironment, PARAM_USER_POOL_CLIENT_SECRET_ARN);
  }

  public static String getParameterLogoutUrl(Construct scope,
                                             ApplicationEnvironment applicationEnvironment) {
    return getParameter(scope, applicationEnvironment, PARAM_USER_POOL_LOGOUT_URL);
  }

  public static String getParameterUserPoolProviderUrl(Construct scope,
                                                       ApplicationEnvironment appEnvironment) {
    return getParameter(scope, appEnvironment, PARAM_USER_POOL_PROVIDER_URL);
  }
  // endregion

  @lombok.Builder
  @Getter(AccessLevel.PACKAGE)
  public static final class InputParameters {
    // https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-app-integration.html
    static final String DEFAULT_COGNITO_LOGOUT_URL_TPL
        = "https://%s.auth.%s.amazoncognito.com/logout";
    static final String DEFAULT_COGNITO_OAUTH_LOGIN_URL_TEMPLATE
        = "%s/login/oauth2/code/cognito";

    @lombok.Builder.Default
    private String cognitoLogoutUrlTemplate = DEFAULT_COGNITO_LOGOUT_URL_TPL;
    @lombok.Builder.Default
    private String cognitoOauthLoginUrlTemplate = DEFAULT_COGNITO_OAUTH_LOGIN_URL_TEMPLATE;

    private String loginPageDomainPrefix;

    private String applicationName;
    private String applicationUrl;

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
    private boolean userPoolGenerateSecret = true;
    @lombok.Builder.Default
    private List<UserPoolClientIdentityProvider> userPoolSuppoertedIdentityProviders = emptyList();
    @lombok.Builder.Default
    private List<String> userPoolOauthCallBackUrls = emptyList();

    private boolean flowAuthorizationCodeGrantEnabled;
    private boolean flowImplicitCodeGrantEnabled;
    private boolean flowClientCredentialsEnabled;

    String getFullLogoutUrlForRegion(String region) {
      return String.format(getCognitoLogoutUrlTemplate(), getLoginPageDomainPrefix(), region);
    }

    String getAppLoginUrl() {
      return String.format(getCognitoOauthLoginUrlTemplate(), getApplicationUrl());
    }
  }

  @Getter
  @AllArgsConstructor(access = AccessLevel.PACKAGE)
  public static final class OutputParameters {
    private final String userPoolId;
    private final String userPoolClientId;
    private final String userPoolClientName;
    private final String userPoolClientSecretArn;
    private final String logoutUrl;
    private final String providerUrl;
  }
}
