package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.SecretValue;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.ssm.IStringParameter;
import software.amazon.awscdk.services.ssm.StringParameter;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PostgreSQLTest {
  private static String randomString() {
    return UUID.randomUUID().toString();
  }

  @Test
  void newInstance() {
    var vpc = randomString();
    testNewInstance(List.of(randomString(), randomString()),
                    List.of(randomString(), randomString()), vpc);
  }

  @Test
  void newInstanceThrowsIllegalArgumentExceptionIfThereAreNoAZInNetwork() {
    var vpc = randomString();
    var subnets = List.of(randomString(), randomString());
    assertThrows(IllegalArgumentException.class, () -> testNewInstance(null, subnets, vpc));
  }

  @Test
  void newInstanceThrowsIllegalArgumentExceptionIfAZInNetworkAreEmpty() {
    List<String> AZs = emptyList();
    var vpc = randomString();
    var subnets = List.of(randomString(), randomString());
    assertThrows(IllegalArgumentException.class, () -> testNewInstance(AZs, subnets, vpc));
  }

  @Test
  void newInstanceThrowsNPEExceptionIfNoSubnetsInNetwork() {
    var vpc = randomString();
    var AZs = List.of(randomString(), randomString());
    assertThrows(NullPointerException.class, () -> testNewInstance(AZs, null, vpc));
  }

  @Test
  void newInstanceThrowsIllegalArgumentExceptionIfSubnetsAreEmptyInNetwork() {
    var vpc = randomString();
    var AZs = List.of(randomString(), randomString());
    List<String> subnets = emptyList();
    assertThrows(IllegalArgumentException.class, () -> testNewInstance(AZs, subnets, vpc));
  }

  @Test
  void newInstanceThrowsIllegalArgumentExceptionIfSubnetsAreLessThanTwo() {
    var vpc = randomString();
    var AZs = List.of(randomString(), randomString());
    var subnets = List.of(randomString());
    assertThrows(IllegalArgumentException.class, () -> testNewInstance(AZs, subnets, vpc));
  }

  @Test
  void newInstanceThrowsIllegalArgumentExceptionIfNoVpcInNetwork() {
    var AZs = List.of(randomString(), randomString());
    var subnets = List.of(randomString(), randomString());
    assertThrows(IllegalArgumentException.class, () -> testNewInstance(AZs, subnets, null));
  }

  void testNewInstance(List<String> availabilityZones, List<String> isolatedSubnets, String vpc) {
    StaticallyMockedCdk.executeTest(() -> {
      var scope = mock(Construct.class);
      var inputParam = mock(PostgreSQL.InputParameters.class);
      when(inputParam.getInstanceClass()).thenReturn("postgres");
      var appEnvironment = mock(ApplicationEnvironment.class);
      when(appEnvironment.prefixed(any())).thenReturn(randomString());
      var netOutputParamsMock = mock(Network.OutputParameters.class);
      when(netOutputParamsMock.getAvailabilityZones()).thenReturn(availabilityZones);
      when(netOutputParamsMock.getIsolatedSubnets()).thenReturn(isolatedSubnets);
      when(netOutputParamsMock.getVpcId()).thenReturn(vpc);

      var secreValueMock = mock(SecretValue.class);
      when(secreValueMock.toString()).thenReturn(randomString());
      var secretMock = mock(Secret.class);
      when(secretMock.secretValueFromJson(any())).thenReturn(secreValueMock);
      when(secretMock.getSecretArn()).thenReturn(randomString());
      var secretBuilderMock = mock(Secret.Builder.class);
      when(secretBuilderMock.secretName(any())).thenReturn(secretBuilderMock);
      when(secretBuilderMock.description(any())).thenReturn(secretBuilderMock);
      when(secretBuilderMock.generateSecretString(any())).thenReturn(secretBuilderMock);
      when(secretBuilderMock.build()).thenReturn(secretMock);

      try (
          var mockedNetwork = mockStatic(Network.class);
          var mockedSecretStringGenerator = mockStatic(Secret.Builder.class)
      ) {
        mockedNetwork.when(() -> Network.outputParametersFrom(any(), any()))
                     .thenReturn(netOutputParamsMock);
        mockedSecretStringGenerator.when(() -> Secret.Builder.create(any(), any()))
                                   .thenReturn(secretBuilderMock);

        assertNotNull(PostgreSQL.newInstance(scope, randomString(), appEnvironment, inputParam));
      }
    });
  }

  @Test
  void getParameter() {
    var expected = randomString();
    var stringParamMock = mock(IStringParameter.class);
    when(stringParamMock.getStringValue()).thenReturn(expected);

    try (var mockedStringParameter = mockStatic(StringParameter.class)) {
      mockedStringParameter.when(() -> StringParameter.fromStringParameterName(any(), any(), any()))
                           .thenReturn(stringParamMock);
      assertEquals(expected, PostgreSQL.getParameter(mock(Construct.class),
                                                     mock(ApplicationEnvironment.class),
                                                     randomString()));
    }
  }

  void testGetParameter(BiFunction<? super Construct, ? super ApplicationEnvironment, String> getParameterFn) {
    StaticallyMockedCdk.executeTest(() -> {
      var expected = randomString();
      var iStringParameter = mock(IStringParameter.class);
      when(iStringParameter.getStringValue()).thenReturn(expected);
      try (var mockedStringParameter = mockStatic(StringParameter.class)) {
        mockedStringParameter.when(() -> StringParameter.fromStringParameterName(any(), any(), any()))
                             .thenReturn(iStringParameter);
        assertEquals(expected,
                     getParameterFn.apply(mock(Construct.class), mock(ApplicationEnvironment.class)));
      }
    });
  }

  @Test
  void getDbEndpointAddress() {
    testGetParameter(PostgreSQL::getDbEndpointAddress);
  }

  @Test
  void getDbEndpointPort() {
    testGetParameter(PostgreSQL::getDbEndpointPort);
  }

  @Test
  void getDbName() {
    testGetParameter(PostgreSQL::getDbName);
  }

  @Test
  void getDbSecretArn() {
    testGetParameter(PostgreSQL::getDbSecretArn);
  }

  @Test
  void getDbSecurityGroupId() {
    testGetParameter(PostgreSQL::getDbSecurityGroupId);
  }

  @Test
  void outputParametersFrom() {

  }

  @Test
  void newInputParametersNoArgs() {
  }

  @Test
  void newInputParametersArgs() {
  }

  @Test
  void getDbSecurityGroup() {
  }

  @Test
  void getDbSecret() {
  }

  @Test
  void getDbInstance() {
  }
}
