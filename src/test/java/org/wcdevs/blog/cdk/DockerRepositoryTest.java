package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.TagMutability;
import software.amazon.awscdk.services.iam.Grant;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DockerRepositoryTest {
  private final Random random = new SecureRandom();

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
    StaticallyMockedCdk.executeTest(() -> {
      try (var mockedBuilder = mockStatic(Repository.Builder.class)) {
        mockedBuilder.when(() -> Repository.Builder.create(any(), any())).thenReturn(builderMock);

        var inParams = mock(DockerRepository.InputParameters.class);
        when(inParams.removalPolicy()).thenReturn(removalPolicy);
        when(inParams.tagMutability()).thenReturn(tagMutable);
        var actual = DockerRepository.newInstance(mock(Construct.class), randomString(), inParams);
        Assertions.assertNotNull(actual);
      }
    });
  }

  @Test
  void newInputParameters() {
    Assertions.assertNotNull(DockerRepository.newInputParameters(randomString(), randomString()));
  }

  @Test
  void newInputParametersOverload() {
    var random = new SecureRandom();
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
    StaticallyMockedCdk.executeTest(() -> {
      try (var mockedBuilder = mockStatic(Repository.Builder.class)) {
        mockedBuilder.when(() -> Repository.Builder.create(any(), any())).thenReturn(builderMock);

        var actual
            = DockerRepository.newInstance(mock(Construct.class), randomString(),
                                           mock(DockerRepository.InputParameters.class));
        assertEquals(repositoryMock, actual.getEcRepository());
      }
    });
  }

  @Test
  void requiredConstructor() {
    var repositoryName = randomString();
    var accountId = randomString();
    var actual = new DockerRepository.InputParameters(repositoryName, accountId);
    Assertions.assertNotNull(actual);
    assertEquals(repositoryName, actual.getRepositoryName());
    assertEquals(accountId, actual.getAccountId());
    assertEquals(DockerRepository.InputParameters.DEFAULT_MAX_IMAGE_COUNT,
                 actual.getMaxImageCount());
    assertEquals(DockerRepository.InputParameters.DEFAULT_RETAIN_POLICY,
                 actual.isRetainRegistryOnDelete());
    assertEquals(DockerRepository.InputParameters.DEFAULT_INMUTABLE_TAGS,
                 actual.isInmutableTags());
    assertEquals(TagMutability.IMMUTABLE, actual.tagMutability());
    assertEquals(RemovalPolicy.RETAIN, actual.removalPolicy());
  }

  @Test
  void allArgsConstructorRetainImageInmutableTags() {
    testAllArgsConstructor(random.nextInt(),
                           true, RemovalPolicy.RETAIN,
                           true, TagMutability.IMMUTABLE);
  }

  @Test
  void allArgsConstructorDestroyImageMutableTags() {
    testAllArgsConstructor(random.nextInt(),
                           false, RemovalPolicy.DESTROY,
                           false, TagMutability.MUTABLE);
  }

  void testAllArgsConstructor(int expectedMaxImageCount, boolean expectedRetainRegistry,
                              RemovalPolicy expectedRemovalPolicy, boolean expectedInmutableTags,
                              TagMutability expectedTagMutability) {
    var repoName = randomString();
    var accountId = randomString();

    var actual = new DockerRepository.InputParameters(repoName, accountId, expectedMaxImageCount,
                                                      expectedRetainRegistry,
                                                      expectedInmutableTags);
    Assertions.assertNotNull(actual);
    assertEquals(repoName, actual.getRepositoryName());
    assertEquals(accountId, actual.getAccountId());
    assertEquals(expectedMaxImageCount, actual.getMaxImageCount());
    assertEquals(expectedRetainRegistry, actual.isRetainRegistryOnDelete());
    assertEquals(expectedInmutableTags, actual.isInmutableTags());
    assertEquals(expectedTagMutability, actual.tagMutability());
    assertEquals(expectedRemovalPolicy, actual.removalPolicy());
  }
}
