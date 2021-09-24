package org.wcdevs.blog.cdk;

import java.util.Collections;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.LifecycleRule;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.TagMutability;
import software.amazon.awscdk.services.iam.AccountPrincipal;

import static java.lang.String.format;
import static org.wcdevs.blog.cdk.Util.*;

/**
 * Holds the constructs to deploy an ECR repository.
 */
public final class DockerRepository extends Construct {
  /**
   * Default LifeCycle Rule evaluation priority. Rules are evaluated (low to high). The first rule
   * that matches is applied to an image.
   */
  private static final int LCR_PRIORITY = 1;

  /**
   * ECR Repository.
   */
  @Getter
  private IRepository ecRepository;

  private DockerRepository(Construct scope, String id) {
    super(Objects.requireNonNull(scope), Objects.requireNonNull(id));
  }

  /**
   * Creates a new {@link DockerRepository}.
   *
   * @param scopeArg Scope in which the ECR will be defined.
   * @param idArg    Scoped id of the ECR.
   */
  public static DockerRepository newInstance(Construct scopeArg, String idArg,
                                             InputParameter inputParameter) {
    Construct scope = Objects.requireNonNull(scopeArg);
    String id = Objects.requireNonNull(idArg);
    InputParameter inParameter = Objects.requireNonNull(inputParameter);

    DockerRepository dockerRepository = new DockerRepository(scope, id);
    LifecycleRule lifecycleRule = LifecycleRule.builder()
                                               .rulePriority(LCR_PRIORITY)
                                               .description(descriptionFrom(inParameter))
                                               .maxImageCount(inParameter.maxImageCount)
                                               .build();
    dockerRepository.ecRepository = Repository.Builder
        .create(dockerRepository, string("ecRepository-", id))
        .imageTagMutability(inParameter.tagMutability())
        .repositoryName(inParameter.repositoryName)
        .removalPolicy(inParameter.removalPolicy())
        .lifecycleRules(Collections.singletonList(lifecycleRule))
        .build();
    dockerRepository.ecRepository.grantPullPush(new AccountPrincipal(inParameter.accountId));

    return dockerRepository;
  }

  private static String descriptionFrom(InputParameter inParameter) {
    String description = "Docker ECR '%s' will hold a maximum of % images. It will %s retained on "
                         + "deletion and tags are %s";
    return format(description, inParameter.repositoryName, inParameter.maxImageCount,
                  inParameter.retainRegistryOnDelete ? "be" : "not be",
                  inParameter.inmutableTags ? "inmutables" : "mutables");
  }

  /**
   * Holds the input parameters to build a new {@link DockerRepository}.
   */
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class InputParameter {
    /**
     * Name of the docker repository to be created.
     */
    private final String repositoryName;
    /**
     * AWS account id with push/pull permission over the Docker repository.
     */
    private final String accountId;
    /**
     * Max number of images to keep in the repo.
     */
    private int maxImageCount = 10;
    /**
     * Whether the container registry should be destroyed or retained on deletion.
     */
    private boolean retainRegistryOnDelete = true;
    /**
     * Whether tags shall be mutable or not.
     */
    private boolean inmutableTags = true;

    private RemovalPolicy removalPolicy() {
      return retainRegistryOnDelete ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY;
    }

    private TagMutability tagMutability() {
      return inmutableTags ? TagMutability.IMMUTABLE : TagMutability.MUTABLE;
    }
  }
}
