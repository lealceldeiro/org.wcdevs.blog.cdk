package org.wcdevs.blog.cdk;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.iam.Grant;
import software.amazon.jsii.JsiiEngine;

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
  void newInstance() {
    try (
        MockedStatic<JsiiEngine> mockedJsiiEngine = mockStatic(JsiiEngine.class);
        MockedStatic<Repository.Builder> mockedBuilder = mockStatic(Repository.Builder.class)
    ) {
      mockedJsiiEngine.when(JsiiEngine::getInstance).thenReturn(mock(JsiiEngine.class));
      mockedBuilder.when(() -> Repository.Builder.create(any(), any())).thenReturn(builderMock);

      DockerRepository actual
          = DockerRepository.newInstance(mock(Construct.class), randomString(),
                                         mock(DockerRepository.InputParameters.class));
      Assertions.assertNotNull(actual);
    }
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
