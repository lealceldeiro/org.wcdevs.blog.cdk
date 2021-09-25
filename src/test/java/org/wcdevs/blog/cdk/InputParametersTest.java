package org.wcdevs.blog.cdk;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.services.ecr.TagMutability;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InputParametersTest {
  private Random random = new SecureRandom();

  private static String randomString() {
    return UUID.randomUUID().toString();
  }

  @Test
  void requiredConstructor() {
    String repositoryName = randomString();
    String accountId = randomString();
    DockerRepository.InputParameters actual = new DockerRepository.InputParameters(repositoryName,
                                                                                   accountId);
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
    String repositoryName = randomString();
    String accountId = randomString();

    DockerRepository.InputParameters actual
        = new DockerRepository.InputParameters(repositoryName, accountId, expectedMaxImageCount,
                                               expectedRetainRegistry, expectedInmutableTags);
    Assertions.assertNotNull(actual);
    assertEquals(repositoryName, actual.getRepositoryName());
    assertEquals(accountId, actual.getAccountId());
    assertEquals(expectedMaxImageCount, actual.getMaxImageCount());
    assertEquals(expectedRetainRegistry, actual.isRetainRegistryOnDelete());
    assertEquals(expectedInmutableTags, actual.isInmutableTags());
    assertEquals(expectedTagMutability, actual.tagMutability());
    assertEquals(expectedRemovalPolicy, actual.removalPolicy());
  }
}
