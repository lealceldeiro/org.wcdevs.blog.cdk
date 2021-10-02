package org.wcdevs.blog.cdk;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.LifecycleRule;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.TagMutability;
import software.amazon.awscdk.services.iam.AccountPrincipal;

import java.util.Objects;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.wcdevs.blog.cdk.Util.string;

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
  @Setter(AccessLevel.PRIVATE)
  private IRepository ecRepository;

  private DockerRepository(Construct scope, String id) {
    super(Objects.requireNonNull(scope), Objects.requireNonNull(id));
  }

  /**
   * Creates a new {@link DockerRepository}.
   *
   * @param scope           Scope in which the ECR will be defined.
   * @param id              Scoped id of the ECR.
   * @param inputParameters Input parameters to build the new {@link DockerRepository}.
   *
   * @return The newly created {@link DockerRepository}.
   */
  public static DockerRepository newInstance(Construct scope, String id,
                                             InputParameters inputParameters) {
    var validScope = Objects.requireNonNull(scope);
    var validId = Objects.requireNonNull(id);
    var validInParams = Objects.requireNonNull(inputParameters);

    var dockerRepository = new DockerRepository(validScope, validId);
    var lifecycleRule = LifecycleRule.builder()
                                     .rulePriority(LCR_PRIORITY)
                                     .description(descriptionFrom(validInParams))
                                     .maxImageCount(validInParams.getMaxImageCount())
                                     .build();
    var ecRepository = Repository.Builder.create(dockerRepository, string("ecRepository", validId))
                                         .imageTagMutability(validInParams.tagMutability())
                                         .repositoryName(validInParams.getRepositoryName())
                                         .removalPolicy(validInParams.removalPolicy())
                                         .lifecycleRules(singletonList(lifecycleRule))
                                         .build();
    ecRepository.grantPullPush(new AccountPrincipal(validInParams.getAccountId()));

    dockerRepository.setEcRepository(ecRepository);

    return dockerRepository;
  }

  private static String descriptionFrom(InputParameters inParameter) {
    var description = "Docker ECR '%s' will hold a maximum of %s images. It will %s retained on "
                      + "deletion and tags are %s";
    return format(description, inParameter.getRepositoryName(), inParameter.getMaxImageCount(),
                  (inParameter.isRetainRegistryOnDelete() ? "be" : "not be"),
                  (inParameter.isInmutableTags() ? "inmutables" : "mutables"));
  }

  /**
   * Creates a new {@link InputParameters} from a given repository name and an account id.
   *
   * @param repositoryName Repository name.
   * @param accountId      Account id.
   *
   * @return The newly created {@link InputParameters}.
   */
  public static InputParameters newInputParameters(String repositoryName, String accountId) {
    return new InputParameters(repositoryName, accountId);
  }

  /**
   * Creates a new {@link InputParameters} from a given arguments.
   *
   * @param repositoryName         Repository name.
   * @param accountId              Account id.
   * @param maxImageCount          Max number of images to keep in the repository.
   * @param retainRegistryOnDelete Whether to retain the registry on repository delete or not.
   *                               {@code true}: keep, {@code false}: delete.
   * @param inmutableTags          Whether image tags should be inmutable or not.
   *                               {@code true}: inmutable, {@code false}: mutable.
   *
   * @return The newly created {@link InputParameters}.
   */
  public static InputParameters newInputParameters(String repositoryName, String accountId,
                                                   int maxImageCount,
                                                   boolean retainRegistryOnDelete,
                                                   boolean inmutableTags) {
    return new InputParameters(repositoryName, accountId, maxImageCount, retainRegistryOnDelete,
                               inmutableTags);
  }

  /**
   * Holds the input parameters to build a new {@link DockerRepository}.
   */
  @Getter(AccessLevel.PACKAGE)
  @AllArgsConstructor(access = AccessLevel.PACKAGE)
  @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
  public static final class InputParameters {
    static final int DEFAULT_MAX_IMAGE_COUNT = 10;
    static final boolean DEFAULT_RETAIN_POLICY = true;
    static final boolean DEFAULT_INMUTABLE_TAGS = true;

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
    private int maxImageCount = DEFAULT_MAX_IMAGE_COUNT;
    /**
     * Whether the container registry should be destroyed or retained on deletion.
     */
    private boolean retainRegistryOnDelete = DEFAULT_RETAIN_POLICY;
    /**
     * Whether tags shall be mutable or not.
     */
    private boolean inmutableTags = DEFAULT_INMUTABLE_TAGS;

    RemovalPolicy removalPolicy() {
      return retainRegistryOnDelete ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY;
    }

    TagMutability tagMutability() {
      return inmutableTags ? TagMutability.IMMUTABLE : TagMutability.MUTABLE;
    }
  }
}
