package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.services.cognito.AccountRecovery;
import software.amazon.awscdk.services.cognito.Mfa;
import software.amazon.awscdk.services.cognito.OAuthFlows;
import software.amazon.awscdk.services.cognito.OAuthScope;
import software.amazon.awscdk.services.cognito.OAuthSettings;
import software.amazon.awscdk.services.cognito.UserPoolClient;
import software.amazon.awscdk.services.cognito.UserPoolClientIdentityProvider;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.ssm.IStringParameter;
import software.amazon.awscdk.services.ssm.StringParameter;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CognitoStackTest {
  static Stream<Arguments> newInstanceArgs() {
    return Stream.of(arguments(true, true, true, true),
                     arguments(false, true, true, true),
                     arguments(true, false, true, true),
                     arguments(true, true, false, true),
                     arguments(false, false, true, true),
                     arguments(true, false, false, true),
                     arguments(true, true, true, false),
                     arguments(false, true, true, false),
                     arguments(true, false, true, false),
                     arguments(true, true, false, false),
                     arguments(false, false, true, false),
                     arguments(true, false, false, false));
  }

  @ParameterizedTest
  @MethodSource("newInstanceArgs")
  void newInstance(boolean scopesConfigured, boolean flowsEnabled, boolean oauthDisabled,
                   boolean generateClientSecret) {
    StaticallyMockedCdk.executeTest(() -> {
      try (
          var mockedDuration = mockStatic(Duration.class);
          var mockedOAuthSettings = mockStatic(OAuthSettings.class);
          var mockedOAuthFlows = mockStatic(OAuthFlows.class);
          var ignored1 = mockStatic(OAuthScope.class);
          var ignored2 = mockStatic(UserPoolClientIdentityProvider.class);
          var ignored3 = mockStatic(PhysicalResourceId.class);
          var mockedAwsCustomResourcePolicy = mockStatic(AwsCustomResourcePolicy.class);
          var mockedStringParameterBuilder = mockStatic(StringParameter.Builder.class)
      ) {
        var oAuthSettingsBuilderMock = mock(OAuthSettings.Builder.class);
        when(oAuthSettingsBuilderMock.callbackUrls(any())).thenReturn(oAuthSettingsBuilderMock);
        when(oAuthSettingsBuilderMock.logoutUrls(any())).thenReturn(oAuthSettingsBuilderMock);
        when(oAuthSettingsBuilderMock.flows(any())).thenReturn(oAuthSettingsBuilderMock);
        when(oAuthSettingsBuilderMock.scopes(any())).thenReturn(oAuthSettingsBuilderMock);

        var stringParameterBuilderMock = mock(StringParameter.Builder.class);
        when(stringParameterBuilderMock.parameterName(any()))
            .thenReturn(stringParameterBuilderMock);
        when(stringParameterBuilderMock.stringValue(any()))
            .thenReturn(stringParameterBuilderMock);
        when(stringParameterBuilderMock.build())
            .thenReturn(mock(StringParameter.class));

        var oAuthFlowsBuilderMock = mock(OAuthFlows.Builder.class);
        when(oAuthFlowsBuilderMock.authorizationCodeGrant(any())).thenReturn(oAuthFlowsBuilderMock);
        when(oAuthFlowsBuilderMock.implicitCodeGrant(any())).thenReturn(oAuthFlowsBuilderMock);
        when(oAuthFlowsBuilderMock.clientCredentials(any())).thenReturn(oAuthFlowsBuilderMock);

        mockedDuration.when(() -> Duration.days(any())).thenReturn(mock(Duration.class));
        mockedOAuthSettings.when(OAuthSettings::builder).thenReturn(oAuthSettingsBuilderMock);
        mockedOAuthFlows.when(OAuthFlows::builder).thenReturn(oAuthFlowsBuilderMock);
        mockedAwsCustomResourcePolicy.when(() -> AwsCustomResourcePolicy.fromSdkCalls(any()))
                                     .thenReturn(mock(AwsCustomResourcePolicy.class));
        mockedStringParameterBuilder
            .when(() -> StringParameter.Builder.create(any(), any()))
            .thenReturn(stringParameterBuilderMock);

        var scope = mock(Construct.class);

        var awsEnvironment = mock(Environment.class);
        when(awsEnvironment.getRegion()).thenReturn(randomString());

        var clientParams = mock(CognitoStack.UserPoolClientParameter.class);
        when(clientParams.getApplicationUrl()).thenReturn(randomString());
        when(clientParams.isFlowClientCredentialsEnabled()).thenReturn(false);
        when(clientParams.isFlowImplicitCodeGrantEnabled()).thenReturn(false);
        when(clientParams.isFlowAuthorizationCodeGrantEnabled()).thenReturn(true);
        when(clientParams.isThereAScopeConfigured()).thenReturn(scopesConfigured);
        when(clientParams.isThereAFlowEnabled()).thenReturn(flowsEnabled);
        when(clientParams.isOauthDisabled()).thenReturn(oauthDisabled);
        when(clientParams.isGenerateSecretEnabled()).thenReturn(generateClientSecret);

        var inParams = mock(CognitoStack.InputParameters.class);
        when(inParams.getLoginPageDomainPrefix()).thenReturn(randomString());
        when(inParams.getUserPoolClientConfigurations()).thenReturn(List.of(clientParams));

        var actual = CognitoStack.newInstance(scope, awsEnvironment, randomString(), inParams);
        assertNotNull(actual);
      }
    });
  }

  private static String randomString() {
    return UUID.randomUUID().toString();
  }

  @Test
  void getOutputParameters() {
    var stringParamMock = mock(IStringParameter.class);
    String expected = randomString();
    when(stringParamMock.getStringValue()).thenReturn(expected);

    try (var mockedStringParameter = mockStatic(StringParameter.class)) {
      mockedStringParameter
          .when(() -> StringParameter.fromStringParameterName(any(), any(), any()))
          .thenReturn(stringParamMock);
      var actual = CognitoStack.getOutputParameters(mock(Stack.class), randomString());
      assertNotNull(actual);
      assertEquals(expected, actual.getLogoutUrl());
      assertEquals(expected, actual.getProviderUrl());
    }
  }

  @Test
  void clientInputParameters() {
    var random = new SecureRandom();

    StaticallyMockedCdk.executeTest(() -> {
      var applicationName = randomString();
      var applicationUrl = randomString();
      var userPoolOauthCallBackUrls = List.of(randomString());
      var flowAuthorizationCodeGrantEnabled = random.nextBoolean();
      var flowImplicitCodeGrantEnabled = random.nextBoolean();
      var userPoolSuppoertedIdentityProviders = List.of(mock(UserPoolClientIdentityProvider.class));
      var accessTokenValidity = mock(Duration.class);
      var scopes = List.of(mock(OAuthScope.class));
      var idTokenValidity = mock(Duration.class);
      var tokenRevocationEnabled = random.nextBoolean();
      var returnGenericErrorOnLoginFailed = random.nextBoolean();
      var flowClientCredentialsEnabled = random.nextBoolean();
      var refreshTokenValidity = mock(Duration.class);
      var oauthDisabled = random.nextBoolean();
      var generateSecretEnabled = random.nextBoolean();

      var input = CognitoStack.UserPoolClientParameter
          .builder()
          .applicationName(applicationName)
          .applicationUrl(applicationUrl)
          .userPoolOauthCallBackUrls(userPoolOauthCallBackUrls)
          .flowAuthorizationCodeGrantEnabled(flowAuthorizationCodeGrantEnabled)
          .flowImplicitCodeGrantEnabled(flowImplicitCodeGrantEnabled)
          .flowClientCredentialsEnabled(flowClientCredentialsEnabled)
          .userPoolSuppoertedIdentityProviders(userPoolSuppoertedIdentityProviders)
          .scopes(scopes)
          .accessTokenValidity(accessTokenValidity)
          .idTokenValidity(idTokenValidity)
          .refreshTokenValidity(refreshTokenValidity)
          .tokenRevocationEnabled(tokenRevocationEnabled)
          .returnGenericErrorOnLoginFailed(returnGenericErrorOnLoginFailed)
          .generateSecretEnabled(generateSecretEnabled)
          .oauthDisabled(oauthDisabled)
          .build();

      assertEquals(applicationName, input.getApplicationName());
      assertEquals(applicationUrl, input.getApplicationUrl());
      assertEquals(userPoolOauthCallBackUrls, input.getUserPoolOauthCallBackUrls());
      assertEquals(flowAuthorizationCodeGrantEnabled, input.isFlowAuthorizationCodeGrantEnabled());
      assertEquals(flowImplicitCodeGrantEnabled, input.isFlowImplicitCodeGrantEnabled());
      assertEquals(flowClientCredentialsEnabled, input.isFlowClientCredentialsEnabled());
      assertEquals(userPoolSuppoertedIdentityProviders,
                   input.getUserPoolSuppoertedIdentityProviders());
      assertEquals(scopes, input.getScopes());
      assertEquals(accessTokenValidity, input.getAccessTokenValidity());
      assertEquals(idTokenValidity, input.getIdTokenValidity());
      assertEquals(refreshTokenValidity, input.getRefreshTokenValidity());
      assertEquals(tokenRevocationEnabled, input.isTokenRevocationEnabled());
      assertEquals(returnGenericErrorOnLoginFailed, input.isReturnGenericErrorOnLoginFailed());
      assertEquals(oauthDisabled, input.isOauthDisabled());

      var expectedLoginUrl = String.format(input.getCognitoOauthLoginUrlTemplate(),
                                           input.getApplicationUrl());
      assertEquals(expectedLoginUrl, input.getAppLoginUrl());
      var flowEnabled = flowAuthorizationCodeGrantEnabled || flowClientCredentialsEnabled
                        || flowImplicitCodeGrantEnabled;
      assertEquals(flowEnabled, input.isThereAFlowEnabled());
      assertEquals(generateSecretEnabled, input.isGenerateSecretEnabled());
      assertTrue(input.isThereAScopeConfigured());
    });
  }

  private static Stream<Arguments> clientInputParametersScopesNotConfiguredArgs() {
    return Stream.of(arguments(Collections.emptyList()), arguments((List<OAuthScope>) null));
  }

  @ParameterizedTest
  @MethodSource("clientInputParametersScopesNotConfiguredArgs")
  void clientInputParametersScopesNotConfigured(List<OAuthScope> scopes) {
    StaticallyMockedCdk.executeTest(() -> {
      var input = CognitoStack.UserPoolClientParameter
          .builder()
          .scopes(scopes)
          .build();
      assertFalse(input.isThereAScopeConfigured());
    });
  }

  @Test
  void clientInputParametersScopesConfigured() {
    StaticallyMockedCdk.executeTest(() -> {
      var input = CognitoStack.UserPoolClientParameter.builder()
                                                      .scopes(List.of(mock(OAuthScope.class)))
                                                      .build();
      assertTrue(input.isThereAScopeConfigured());
    });
  }

  static Stream<Arguments> clientInputParametersFlowsEnabledArgs() {
    return Stream.of(arguments(true, false, false),
                     arguments(false, true, false),
                     arguments(false, false, true));
  }

  @ParameterizedTest
  @MethodSource("clientInputParametersFlowsEnabledArgs")
  void clientInputParametersFlowsEnabled(boolean flowClientCredentialsEnabled,
                                         boolean flowImplicitCodeGrantEnabled,
                                         boolean flowAuthorizationCodeGrantEnabled) {
    StaticallyMockedCdk.executeTest(() -> {
      var input = CognitoStack.UserPoolClientParameter
          .builder()
          .flowClientCredentialsEnabled(flowClientCredentialsEnabled)
          .flowImplicitCodeGrantEnabled(flowImplicitCodeGrantEnabled)
          .flowAuthorizationCodeGrantEnabled(flowAuthorizationCodeGrantEnabled)
          .build();
      assertTrue(input.isThereAFlowEnabled());
    });
  }

  @Test
  void clientInputParametersFlowsNotEnabled() {
    StaticallyMockedCdk.executeTest(() -> {
      var input = CognitoStack.UserPoolClientParameter.builder().build();
      assertFalse(input.isThereAFlowEnabled());
    });
  }

  @Test
  void inputParametersWithDefaults() {
    var input = CognitoStack.InputParameters.builder().build();
    assertNull(input.getLoginPageDomainPrefix());
    assertFalse(input.isSelfSignUpEnabled());
    assertEquals(AccountRecovery.EMAIL_ONLY, input.getAccountRecovery());
    assertFalse(input.isSignInAutoVerifyEmail());
    assertFalse(input.isSignInAutoVerifyPhone());
    assertTrue(input.isSignInAliasUsername());
    assertTrue(input.isSignInAliasEmail());
    assertFalse(input.isSignInAliasPhone());
    assertFalse(input.isSignInPhoneRequired());
    assertFalse(input.isSignInPhoneMutable());
    assertTrue(input.isSignInCaseSensitive());
    assertTrue(input.isSignInEmailRequired());
    assertFalse(input.isSignInEmailMutable());
    assertEquals(Mfa.OFF, input.getMfa());
    assertTrue(input.isPasswordRequireLowercase());
    assertTrue(input.isPasswordRequireDigits());
    assertTrue(input.isPasswordRequireSymbols());
    assertTrue(input.isPasswordRequireUppercase());
    assertEquals(8, input.getPasswordMinLength());
    assertEquals(7, input.getTempPasswordValidityInDays());
    assertTrue(input.getUserPoolClientConfigurations().isEmpty());

    assertEquals(CognitoStack.DEFAULT_COGNITO_LOGOUT_URL_TPL,
                 input.getCognitoLogoutUrlTemplate());
    var region = randomString();
    var expectedLogoutUrl = String.format(input.getCognitoLogoutUrlTemplate(),
                                          input.getLoginPageDomainPrefix(),
                                          region);
    assertEquals(expectedLogoutUrl, input.getFullLogoutUrlForRegion(region));
  }

  @Test
  void getUserPoolClientSecret() {
    var envName = randomString();
    var appName = randomString();
    var secretMock = mock(ISecret.class);
    var arn = randomString();

    try (
        var mockedSecret = mockStatic(Secret.class);
        var mockedStringParameter = mockStatic(StringParameter.class)
    ) {
      var stringParam = mock(IStringParameter.class);
      when(stringParam.getStringValue()).thenReturn(arn);
      mockedStringParameter.when(() -> StringParameter.fromStringParameterName(any(), any(), any()))
                           .thenReturn(stringParam);
      mockedSecret.when(() -> Secret.fromSecretCompleteArn(any(), any(), any()))
                  .thenReturn(secretMock);
      var appEnv = mock(ApplicationEnvironment.class);
      when(appEnv.getEnvironmentName()).thenReturn(envName);
      when(appEnv.getApplicationName()).thenReturn(appName);

      assertEquals(secretMock, CognitoStack.getUserPoolClientSecret(mock(Stack.class), appEnv));
    }
  }

  @Test
  void userPoolClientSecretWrapper() {
    StaticallyMockedCdk.executeTest(() -> {
      var userPoolClient = mock(UserPoolClient.class);
      var secretGenerated = new SecureRandom().nextBoolean();
      var actual = new CognitoStack.UserPoolClientWrapper(userPoolClient, secretGenerated);

      assertEquals(userPoolClient, actual.getClient());
      assertEquals(secretGenerated, actual.isSecretGenerated());
    });
  }
}
