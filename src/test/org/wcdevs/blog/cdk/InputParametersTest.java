package org.wcdevs.blog.cdk;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.services.ecr.TagMutability;
import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InputParametersTest {
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
  void allArgsConstructor() {
    Random random = new SecureRandom();
    String repositoryName = randomString();
    String accountId = randomString();
    int maxImageCount = random.nextInt();
    boolean retainRegistry = random.nextBoolean();
    boolean inmutableTags = random.nextBoolean();

    DockerRepository.InputParameters actual
        = new DockerRepository.InputParameters(repositoryName, accountId, maxImageCount,
                                               retainRegistry, inmutableTags);
    Assertions.assertNotNull(actual);
    assertEquals(repositoryName, actual.getRepositoryName());
    assertEquals(accountId, actual.getAccountId());
    assertEquals(maxImageCount, actual.getMaxImageCount());
    assertEquals(retainRegistry, actual.isRetainRegistryOnDelete());
    assertEquals(inmutableTags, actual.isInmutableTags());
    assertEquals(inmutableTags ? TagMutability.IMMUTABLE : TagMutability.MUTABLE,
                 actual.tagMutability());
    assertEquals(retainRegistry ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY,
                 actual.removalPolicy());
  }
}
