package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.SecretValue;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.ssm.IStringParameter;
import software.amazon.awscdk.services.ssm.StringParameter;

import java.security.SecureRandom;
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

class DatabaseTest {
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
      var inputParam = mock(Database.InputParameters.class);
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

        assertNotNull(Database.newInstance(scope, randomString(), appEnvironment, inputParam));
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
      assertEquals(expected, Database.getParameter(mock(Construct.class),
                                                   mock(ApplicationEnvironment.class),
                                                   randomString()));
    }
  }

  void testGetParameter(BiFunction<? super Construct, ? super ApplicationEnvironment, String> fn) {
    StaticallyMockedCdk.executeTest(() -> {
      var expected = randomString();
      var iStringParameter = mock(IStringParameter.class);
      when(iStringParameter.getStringValue()).thenReturn(expected);
      try (var mockedStringParameter = mockStatic(StringParameter.class)) {
        mockedStringParameter.when(() -> StringParameter.fromStringParameterName(any(), any(),
                                                                                 any()))
                             .thenReturn(iStringParameter);
        assertEquals(expected, fn.apply(mock(Construct.class), mock(ApplicationEnvironment.class)));
      }
    });
  }

  @Test
  void getDbEndpointAddress() {
    testGetParameter(Database::getDbEndpointAddress);
  }

  @Test
  void getDbEndpointPort() {
    testGetParameter(Database::getDbEndpointPort);
  }

  @Test
  void getDbName() {
    testGetParameter(Database::getDbName);
  }

  @Test
  void getDbSecretArn() {
    testGetParameter(Database::getDbSecretArn);
  }

  @Test
  void getDbSecurityGroupId() {
    testGetParameter(Database::getDbSecurityGroupId);
  }

  @Test
  void outputParametersFrom() {
    var dbEndpointAddress = randomString();
    var dbEndpointPort = randomString();
    var dbName = randomString();
    var dbSecretArn = randomString();
    var dbSecurityGroupId = randomString();

    var expected = new Database.OutputParameters(dbEndpointAddress, dbEndpointPort, dbName,
                                                 dbSecretArn, dbSecurityGroupId);
    var iStringParameter = mock(IStringParameter.class);
    when(iStringParameter.getStringValue()).thenReturn(dbEndpointAddress)
                                           .thenReturn(dbEndpointPort)
                                           .thenReturn(dbName)
                                           .thenReturn(dbSecretArn)
                                           .thenReturn(dbSecurityGroupId);

    StaticallyMockedCdk.executeTest(() -> {
      try (var mockedStringParameter = mockStatic(StringParameter.class)) {
        mockedStringParameter.when(() -> StringParameter.fromStringParameterName(any(), any(),
                                                                                 any()))
                             .thenReturn(iStringParameter);
        assertEquals(expected, Database.outputParametersFrom(mock(Construct.class),
                                                             mock(ApplicationEnvironment.class)));
      }
    });

  }

  @Test
  void newInputParametersNoArgs() {
    assertEquals(new Database.InputParameters(), Database.newInputParameters());
  }

  @Test
  void newInputParametersArgs() {
    var storageCapacityInGB = new SecureRandom().nextInt();
    var instanceClass = randomString();
    var engine = Database.InputParameters.ENGINE_POSTGRES;
    var version = randomString();
    assertEquals(new Database.InputParameters(storageCapacityInGB, instanceClass, engine, version),
                 Database.newInputParameters(storageCapacityInGB, instanceClass, engine, version));
  }

  @Test
  void testInputParametersSetStorageCapacity() {
    var storageCapacity = new SecureRandom().nextInt();
    var inputParams = new Database.InputParameters();

    inputParams.setStorageCapacityInGB(storageCapacity);

    assertEquals(storageCapacity,
                 TestsReflectionUtil.<Integer>getField(inputParams, "storageCapacityInGB"));
  }

  @Test
  void testInputParametersSetInstanceClass() {
    var instanceClass = randomString();
    var inputParams = new Database.InputParameters();

    inputParams.setInstanceClass(instanceClass);

    assertEquals(instanceClass, TestsReflectionUtil.getField(inputParams, "instanceClass"));
  }

  @Test
  void testInputParametersSetEngineVersion() {
    var version = randomString();
    var inputParams = new Database.InputParameters();

    inputParams.setEngineVersion(version);

    assertEquals(version, TestsReflectionUtil.getField(inputParams, "engineVersion"));
  }

  @Test
  void testInputParametersGetStorageCapacity() {
    var storageCapacity = new SecureRandom().nextInt();
    var inputParams = new Database.InputParameters();

    TestsReflectionUtil.setField(inputParams, "storageCapacityInGB", storageCapacity);

    assertEquals(storageCapacity, inputParams.getStorageCapacityInGB());
    assertEquals(String.valueOf(storageCapacity), inputParams.getStorageCapacityInGBString());
  }

  @Test
  void testInputParametersGetInstanceClass() {
    var instanceClass = randomString();
    var inputParams = new Database.InputParameters();

    TestsReflectionUtil.setField(inputParams, "instanceClass", instanceClass);

    assertEquals(instanceClass, inputParams.getInstanceClass());
  }

  @Test
  void testInputParametersGetEngineVersion() {
    var engineVersion = randomString();
    var inputParams = new Database.InputParameters();

    TestsReflectionUtil.setField(inputParams, "engineVersion", engineVersion);

    assertEquals(engineVersion, inputParams.getEngineVersion());
  }

  @Test
  void getDataBasePasswordFromSecret() {
    testGetDBSecVal(Database::getDataBasePasswordFromSecret);
  }

  void testGetDBSecVal(BiFunction<? super Construct, ? super Database.OutputParameters, String> fn) {
    var expected = randomString();
    var secretValueMock = mock(SecretValue.class);
    when(secretValueMock.toString()).thenReturn(expected);

    var secretMock = mock(ISecret.class);
    when(secretMock.secretValueFromJson(any())).thenReturn(secretValueMock);

    try (var mockedSecret = mockStatic(Secret.class)) {
      mockedSecret.when(() -> Secret.fromSecretCompleteArn(any(), any(), any()))
                  .thenReturn(secretMock);
      assertEquals(expected, fn.apply(mock(Construct.class),
                                      mock(Database.OutputParameters.class)));
    }
  }

  @Test
  void getDataBaseUsernameFromSecret() {
    testGetDBSecVal(Database::getDataBaseUsernameFromSecret);
  }
}
