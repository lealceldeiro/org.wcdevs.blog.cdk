package org.wcdevs.blog.cdk;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
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
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.wcdevs.blog.cdk.Util.joinedString;
import static software.amazon.awscdk.customresources.AwsCustomResourcePolicy.ANY_RESOURCE;

public final class CognitoStack extends Stack {
  private static final String PARAM_USER_POOL_ID = "userPoolId";
  private static final String PARAM_USER_POOL_CLIENT_ID = "userPoolClientId";
  private static final String PARAM_USER_POOL_CLIENT_SECRET = "userPoolClientSecret";
  private static final String PARAM_USER_POOL_LOGOUT_URL = "userPoolLogoutUrl";
  private static final String PARAM_USER_POOL_PROVIDER_URL = "userPoolProviderUrl";

  private static final String CONSTRUCT_NAME = "Cognito";
  private static final String DASH_JOINER = "-";
  private static final String LOG_OUT_URL_TPL = "https://%s.auth.%s.amazoncognito.com/logout";

  private CognitoStack(Construct scope, String id, StackProps props) {
    super(scope, id, props);
  }

  public static CognitoStack newInstance(Construct scope, String id, Environment awsEnvironment,
                                         ApplicationEnvironment applicationEnvironment,
                                         InputParameters inputParameters) {
    var inParams = Objects.requireNonNull(inputParameters);
    var region = Objects.requireNonNull(awsEnvironment.getRegion());

    var cognitoPros = StackProps.builder()
                                .stackName(applicationEnvironment.prefixed(CONSTRUCT_NAME))
                                .env(awsEnvironment)
                                .build();
    var cognitoStack = new CognitoStack(scope, id, cognitoPros);

    var userPool = userPool(cognitoStack, inParams);
    var userPoolClient = userPoolClient(cognitoStack, userPool, inParams);
    var logoutUrl = String.format(LOG_OUT_URL_TPL, inParams.getLoginPageDomainPrefix(), region);
    var userPoolDomain = userPoolDomain(cognitoStack, userPool, inParams);

    var userPoolClientSecret = userPoolClientSecret(cognitoStack, region, userPool.getUserPoolId(),
                                                    userPoolClient.getUserPoolClientId());

    createStringParameter(cognitoStack, applicationEnvironment, PARAM_USER_POOL_ID,
                          userPool.getUserPoolId());
    createStringParameter(cognitoStack, applicationEnvironment, PARAM_USER_POOL_CLIENT_ID,
                          userPoolClient.getUserPoolClientId());
    createStringParameter(cognitoStack, applicationEnvironment, PARAM_USER_POOL_CLIENT_SECRET,
                          userPoolClientSecret);
    createStringParameter(cognitoStack, applicationEnvironment, PARAM_USER_POOL_LOGOUT_URL,
                          logoutUrl);
    createStringParameter(cognitoStack, applicationEnvironment, PARAM_USER_POOL_PROVIDER_URL,
                          userPool.getUserPoolProviderUrl());

    return cognitoStack;
  }

  // region helpers
  private static UserPool userPool(Construct scope, InputParameters inParams) {
    var autoVerifyEmail = AutoVerifiedAttrs.builder()
                                           .email(inParams.isSignInAutoVerifyEmail())
                                           .build();
    var signInAliases = SignInAliases.builder()
                                     .username(inParams.isSignInAliasUsername())
                                     .email(inParams.isSignInAliasEmail())
                                     .build();
    var signInEmailAttributes = StandardAttribute.builder()
                                                 .required(inParams.isSignInEmailRequired())
                                                 .mutable(inParams.isSignInEmailMutable())
                                                 .build();
    var signInAttributes = StandardAttributes.builder()
                                             .email(signInEmailAttributes).build();
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
                           .userPoolName(inParams.getApplicationName() + "-user-pool")
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
    var prodAppUrl = String.format("%s/login/oauth2/code/cognito", inParams.getApplicationUrl());
    var callbackUrls = join(inParams.getUserPoolOauthCallBackUrls(), prodAppUrl);
    var logoutUrls = List.of(inParams.getApplicationUrl());
    var oAuthConf = OAuthSettings.builder()
                                 .callbackUrls(callbackUrls)
                                 .logoutUrls(logoutUrls)
                                 .flows(OAuthFlows.builder().authorizationCodeGrant(true).build())
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

  private static UserPoolDomain userPoolDomain(Construct scope, IUserPool userPool,
                                               InputParameters inParams) {
    var cognitoDomain = CognitoDomainOptions.builder()
                                            .domainPrefix(inParams.getLoginPageDomainPrefix())
                                            .build();
    return UserPoolDomain.Builder.create(scope, "userPoolDomain")
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
    return joinedString(DASH_JOINER, appEnv.getEnvironmentName(), appEnv.getApplicationName(),
                        CONSTRUCT_NAME, parameterName);
  }
  // endregion

  // region get output params
  public static OutputParameters getOutputParameters(Construct scope,
                                                     ApplicationEnvironment appEnvironment) {
    return new OutputParameters(getParameterUserPoolId(scope, appEnvironment),
                                getParameterUserPoolClientId(scope, appEnvironment),
                                getParameterUserPoolClientSecret(scope, appEnvironment),
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

  public static String getParameterUserPoolClientSecret(Construct scope,
                                                        ApplicationEnvironment appEnvironment) {
    return getParameter(scope, appEnvironment, PARAM_USER_POOL_CLIENT_SECRET);
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

  @SafeVarargs
  private static <T> List<T> join(Collection<? extends T> additional, T... elements) {
    return Stream.concat(Stream.of(Objects.requireNonNull(elements)),
                         Objects.requireNonNull(additional.stream()))
                 .collect(toList());
  }

  private static String userPoolClientSecret(Construct scope, String awsRegion, String userPoolId,
                                             String userPoolClientId) {
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

  @Value
  @Getter(AccessLevel.PACKAGE)
  public static class InputParameters {
    String loginPageDomainPrefix;

    String applicationName;
    String applicationUrl;

    boolean selfSignUpEnabled = false;
    AccountRecovery accountRecovery = AccountRecovery.EMAIL_ONLY;
    boolean signInAutoVerifyEmail = false;
    boolean signInAliasUsername = true;
    boolean signInAliasEmail = true;
    boolean signInCaseSensitive = true;
    boolean signInEmailRequired = true;
    boolean signInEmailMutable = false;
    Mfa mfa = Mfa.OFF;
    boolean passwordRequireLowercase = true;
    boolean passwordRequireDigits = true;
    boolean passwordRequireSymbols = true;
    boolean passwordRequireUppercase = true;
    int passwordMinLength = 8;
    int tempPasswordValidityInDays = 7;

    boolean userPoolGenerateSecret = true;
    List<UserPoolClientIdentityProvider> userPoolSuppoertedIdentityProviders = emptyList();
    List<String> userPoolOauthCallBackUrls = emptyList();
  }

  @Getter
  @AllArgsConstructor(access = AccessLevel.PACKAGE)
  public static class OutputParameters {
    private final String userPoolId;
    private final String userPoolClientId;
    private final String userPoolClientSecret;
    private final String logoutUrl;
    private final String providerUrl;
  }
}
