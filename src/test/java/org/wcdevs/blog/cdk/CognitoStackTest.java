package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.services.cognito.AccountRecovery;
import software.amazon.awscdk.services.cognito.Mfa;
import software.amazon.awscdk.services.cognito.OAuthFlows;
import software.amazon.awscdk.services.cognito.OAuthScope;
import software.amazon.awscdk.services.cognito.OAuthSettings;
import software.amazon.awscdk.services.cognito.UserPoolClientIdentityProvider;
import software.amazon.awscdk.services.ssm.IStringParameter;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CognitoStackTest {
    @Test
    void newInstance() {
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

                mockedDuration.when(() -> Duration.days(any())).thenReturn(mock(Duration.class));
                mockedOAuthSettings.when(OAuthSettings::builder).thenReturn(oAuthSettingsBuilderMock);
                mockedOAuthFlows.when(OAuthFlows::builder).thenReturn(oAuthFlowsBuilderMock);
                mockedAwsCustomResourcePolicy.when(() -> AwsCustomResourcePolicy.fromSdkCalls(any()))
                                             .thenReturn(mock(AwsCustomResourcePolicy.class));
                mockedStringParameterBuilder
                        .when(() -> StringParameter.Builder.create(any(), any()))
                        .thenReturn(stringParameterBuilderMock);

                var scope = mock(Construct.class);
                var applicationEnvironment = mock(ApplicationEnvironment.class);

                var awsEnvironment = mock(Environment.class);
                when(awsEnvironment.getRegion()).thenReturn(randomString());

                var inParams = mock(CognitoStack.InputParameters.class);
                when(inParams.getApplicationUrl()).thenReturn(randomString());
                when(inParams.getLoginPageDomainPrefix()).thenReturn(randomString());

                var actual = CognitoStack.newInstance(scope, randomString(), awsEnvironment,
                                                      applicationEnvironment, inParams);
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
            var actual = CognitoStack.getOutputParameters(mock(Construct.class),
                                                          mock(ApplicationEnvironment.class));
            assertNotNull(actual);
            assertEquals(expected, actual.getLogoutUrl());
            assertEquals(expected, actual.getProviderUrl());
            assertEquals(expected, actual.getUserPoolClientId());
            assertEquals(expected, actual.getUserPoolId());
            assertEquals(expected, actual.getUserPoolClientSecret());
        }
    }

    @Test
    void inputParametersWithDefaults() {
        var input = CognitoStack.InputParameters.builder().build();
        assertNull(input.getLoginPageDomainPrefix());
        assertNull(input.getApplicationName());
        assertNull(input.getApplicationUrl());
        assertFalse(input.isSelfSignUpEnabled());
        assertEquals(AccountRecovery.EMAIL_ONLY, input.getAccountRecovery());
        assertFalse(input.isSignInAutoVerifyEmail());
        assertTrue(input.isSignInAliasUsername());
        assertTrue(input.isSignInAliasEmail());
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
        assertTrue(input.isUserPoolGenerateSecret());
        assertTrue(input.getUserPoolSuppoertedIdentityProviders().isEmpty());
        assertTrue(input.getUserPoolOauthCallBackUrls().isEmpty());
    }
}
