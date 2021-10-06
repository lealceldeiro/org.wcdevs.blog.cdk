package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.TagMutability;
import software.amazon.awscdk.services.iam.Grant;
import software.amazon.jsii.JsiiEngine;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DockerRepositoryTest {
  private Repository.Builder builderMock;
  private Repository repositoryMock;

  @BeforeEach
  void setUp() {
    repositoryMock = mock(Repository.class);
    when(repositoryMock.grantPullPush(any())).thenReturn(mock(Grant.class));

    builderMock = mock(Repository.Builder.class);
    when(builderMock.imageTagMutability(any())).thenReturn(builderMock);
    when(builderMock.repositoryName(any())).thenReturn(builderMock);
    when(builderMock.removalPolicy(any())).thenReturn(builderMock);
    when(builderMock.lifecycleRules(any())).thenReturn(builderMock);
    when(builderMock.build()).thenReturn(repositoryMock);
  }

  @Test
  void newInstanceWithDefaults() {
    testNewInstanceWithParameters(RemovalPolicy.RETAIN, TagMutability.IMMUTABLE);
  }

  @Test
  void newInstanceWithCustomInputParameters() {
    testNewInstanceWithParameters(RemovalPolicy.DESTROY, TagMutability.MUTABLE);
  }

  void testNewInstanceWithParameters(RemovalPolicy removalPolicy, TagMutability tagMutable) {
    try (
        MockedStatic<JsiiEngine> mockedJsiiEngine = mockStatic(JsiiEngine.class);
        MockedStatic<Repository.Builder> mockedBuilder = mockStatic(Repository.Builder.class)
    ) {
      mockedJsiiEngine.when(JsiiEngine::getInstance).thenReturn(mock(JsiiEngine.class));
      mockedBuilder.when(() -> Repository.Builder.create(any(), any())).thenReturn(builderMock);

      DockerRepository.InputParameters inParameters = mock(DockerRepository.InputParameters.class);
      when(inParameters.removalPolicy()).thenReturn(removalPolicy);
      when(inParameters.tagMutability()).thenReturn(tagMutable);
      DockerRepository actual
          = DockerRepository.newInstance(mock(Construct.class), randomString(), inParameters);
      Assertions.assertNotNull(actual);
    }
  }

  @Test
  void newInputParameters() {
    Assertions.assertNotNull(DockerRepository.newInputParameters(randomString(), randomString()));
  }

  @Test
  void newInputParametersOverload() {
    Random random = new SecureRandom();
    Assertions.assertNotNull(DockerRepository.newInputParameters(randomString(), randomString(),
                                                                 random.nextInt(),
                                                                 random.nextBoolean(),
                                                                 random.nextBoolean()));
  }

  private String randomString() {
    return UUID.randomUUID().toString();
  }

  @Test
  void getEcRepository() {
    try (
        MockedStatic<JsiiEngine> mockedJsiiEngine = mockStatic(JsiiEngine.class);
        MockedStatic<Repository.Builder> mockedBuilder = mockStatic(Repository.Builder.class)
    ) {
      mockedJsiiEngine.when(JsiiEngine::getInstance).thenReturn(mock(JsiiEngine.class));
      mockedBuilder.when(() -> Repository.Builder.create(any(), any())).thenReturn(builderMock);

      DockerRepository actual
          = DockerRepository.newInstance(mock(Construct.class), randomString(),
                                         mock(DockerRepository.InputParameters.class));
      Assertions.assertEquals(repositoryMock, actual.getEcRepository());
    }
  }
}
