package org.wcdevs.blog.cdk;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import software.amazon.awscdk.services.ec2.CfnSecurityGroup;
import software.amazon.awscdk.services.rds.CfnDBInstance;
import software.amazon.awscdk.services.rds.CfnDBSubnetGroup;
import software.amazon.awscdk.services.rds.StorageType;
import software.amazon.awscdk.services.secretsmanager.CfnSecretTargetAttachment;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.List;
import java.util.Objects;

import static org.wcdevs.blog.cdk.Util.joinedString;

/**
 * Represents a constructs to create a Database in an isolated subnet of a given Vpc. The
 * following parameters need to exist in the AWS parameter store (SSM) associated to the same
 * environment the Database instance is created for this construct to successfully deploy:
 * <ul>
 *   <li>A {@link Network} vpc id </li>
 *   <li>A {@link Network} isolated subnets (2 at least)</li>
 *   <li>A {@link Network} availability zone</li>
 * </ul>
 */
@Setter(AccessLevel.PRIVATE)
@Getter(AccessLevel.PACKAGE)
public final class Database extends Construct {
  // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-rds-database-instance.html
  private static final String TARGET_TYPE_AWS_RDS_DB_INSTANCE = "AWS::RDS::DBInstance";
  private static final String CONSTRUCT_NAME = "Database";
  private static final String DASH_JOINER = "-";
  private static final String PARAM_ENDPOINT_ADDRESS = "endpointAddress";
  private static final String PARAM_ENDPOINT_PORT = "endpointPort";
  private static final String PARAM_DATABASE_NAME = "databaseName";
  private static final String PARAM_SECURITY_GROUP_ID = "securityGroupId";
  private static final String PARAM_SECRET_ARN = "secretArn";
  private static final String DATABASE_SECRET = "databaseSecret";

  public static final String USERNAME_SECRET_HOLDER = "username";
  public static final String PASSWORD_SECRET_HOLDER = "password";

  private CfnSecurityGroup dbSecurityGroup;
  private Secret dbSecret;
  private CfnDBInstance dbInstance;

  private Database(Construct scope, String id) {
    super(scope, id);
  }

  /**
   * Creates a new {@link Database} from a given scope, construct id, an
   * {@link ApplicationEnvironment} and some input parameters to configure the DB instance.
   *
   * @param scope                  Construct scope.
   * @param id                     Construct id.
   * @param applicationEnvironment {@link ApplicationEnvironment} in which the DB instance will be
   *                               deployed.
   * @param inputParameters        {@link InputParameters} with configured values to creaet the DB
   *                               instance.
   *
   * @return A new {@link Database} instance.
   */
  public static Database newInstance(Construct scope, String id,
                                     ApplicationEnvironment applicationEnvironment,
                                     InputParameters inputParameters) {
    var inParams = Objects.requireNonNull(inputParameters);
    var database = new Database(Objects.requireNonNull(scope), Objects.requireNonNull(id));

    // retrieve network output params from SSM
    // IVpc#fromLookup is broken (https://github.com/aws/aws-cdk/issues/3600)
    var netOutParams = Network.outputParametersFrom(database, applicationEnvironment);
    var availabilityZones = netOutParams.getAvailabilityZones();
    if (availabilityZones == null || availabilityZones.isEmpty()) {
      throw new IllegalArgumentException("No availability zones in network");
    }

    var vpcId = netOutParams.getVpcId();
    if (vpcId == null) {
      throw new IllegalArgumentException("No VPC in network");
    }
    var secGroup = cfnSecurityGroup(database, vpcId,
                                    applicationEnvironment.prefixed("databaseSecurityGroup"));
    database.setDbSecurityGroup(secGroup);

    var username = Util.dbSanitized(applicationEnvironment.prefixed("dbuser"));
    var dbSecret = dbSecret(database, applicationEnvironment.prefixed(DATABASE_SECRET),
                            username);
    database.setDbSecret(dbSecret);

    var subnetGroupName = applicationEnvironment.prefixed("databaseSubnetGroup");
    var subnetGroup = cfnDBSubnetGroup(database, subnetGroupName,
                                       netOutParams.getIsolatedSubnets());

    subnetGroupName = subnetGroup.getDbSubnetGroupName();
    var dbName = Util.dbSanitized(applicationEnvironment.prefixed("database"));
    var dbPassword = dbSecret.secretValueFromJson(PASSWORD_SECRET_HOLDER).toString();
    var dbInstance = dbInstance(database, inParams, availabilityZones.get(0), subnetGroupName,
                                dbName, username, dbPassword, secGroup.getAttrGroupId());
    database.setDbInstance(dbInstance);

    cfnSecretTargetAttachment(database, dbSecret.getSecretArn(), dbInstance.getRef());

    saveDatabaseInfoToParameterStore(database, applicationEnvironment);
    applicationEnvironment.tag(database);

    return database;
  }

  // region helpers
  private static CfnSecurityGroup cfnSecurityGroup(Construct scope, String vpcId,
                                                   String securityGroupName) {
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-ec2-security-group.html
    return CfnSecurityGroup.Builder.create(scope, "databaseSecurityGroup")
                                   .vpcId(vpcId)
                                   .groupDescription("Database security group")
                                   .groupName(securityGroupName)
                                   .build();
  }

  private static Secret dbSecret(Construct scope, String secretName, String username) {
    var secretTemplate = String.format("{\"%s\":\"%s\"}", USERNAME_SECRET_HOLDER, username);
    var secretString = SecretStringGenerator.builder()
                                            .secretStringTemplate(secretTemplate)
                                            .generateStringKey(PASSWORD_SECRET_HOLDER)
                                            .passwordLength(37)
                                            .excludeCharacters("@/\\\" ")
                                            .build();
    return Secret.Builder.create(scope, DATABASE_SECRET)
                         .secretName(secretName)
                         .description("Credentials to be used by the RDB (Database) instance")
                         .generateSecretString(secretString)
                         .build();
  }

  private static CfnDBSubnetGroup cfnDBSubnetGroup(Construct scope, String subnetGroupName,
                                                   List<String> subnetIds) {
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-rds-dbsubnet-group.html
    if (subnetIds.size() < 2) {
      throw new IllegalArgumentException("Subnet groups must contain at least two subnets in two "
                                         + "different Availability Zones in the same region. More "
                                         + "info at https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_VPC.WorkingWithRDSInstanceinaVPC.html#USER_VPC.Subnets");
    }
    return CfnDBSubnetGroup.Builder.create(scope, "databaseSubnetGroup")
                                   .dbSubnetGroupDescription("RDB subnet group")
                                   .dbSubnetGroupName(subnetGroupName)
                                   .subnetIds(subnetIds)
                                   .build();
  }

  private static CfnDBInstance dbInstance(Construct scope, InputParameters inParams,
                                          String availabilityZone, String subnetGroupName,
                                          String dbName, String dbUsername, String dbPassword,
                                          String securityGroupId) {
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-rds-database-instance.html
    return CfnDBInstance.Builder.create(scope, "databaseInstance")
                                .allocatedStorage(inParams.getStorageCapacityInGBString())
                                .availabilityZone(availabilityZone)
                                .dbInstanceClass(inParams.getInstanceClass())
                                .dbName(dbName)
                                .dbSubnetGroupName(subnetGroupName)
                                .engine(inParams.getEngine())
                                .engineVersion(inParams.getEngineVersion())
                                .masterUsername(dbUsername)
                                .masterUserPassword(dbPassword)
                                .publiclyAccessible(inParams.isPubliclyAccessible())
                                .vpcSecurityGroups(List.of(securityGroupId))
                                .deletionProtection(inParams.isProtectedAgainstDeletion())
                                .autoMinorVersionUpgrade(inParams.isMinorVersionAutoUpgradeEnabled())
                                .enablePerformanceInsights(inParams.isPerformanceInsightsEnabled())
                                .storageEncrypted(inParams.isEncryptionEnabled())
                                .port(inParams.getPort())
                                .storageType(inParams.getStorageType().name())
                                .backupRetentionPeriod(inParams.getBackUpRetentionPeriodInDays())
                                .deleteAutomatedBackups(inParams.isDeleteAutomatedBackupsEnabled())
                                .build();
  }

  private static CfnSecretTargetAttachment cfnSecretTargetAttachment(Construct scope,
                                                                     String dbSecretArn,
                                                                     String dbRef) {
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-secretsmanager-secrettargetattachment.html
    return CfnSecretTargetAttachment.Builder.create(scope, "databaseSecretTargetAttachment")
                                            .secretId(dbSecretArn)
                                            .targetId(dbRef)
                                            .targetType(TARGET_TYPE_AWS_RDS_DB_INSTANCE)
                                            .build();
  }

  private static void saveDatabaseInfoToParameterStore(Database database,
                                                       ApplicationEnvironment appEnvironment) {
    createStringParameter(database, appEnvironment, PARAM_ENDPOINT_ADDRESS,
                          database.getDbInstance().getAttrEndpointAddress());
    createStringParameter(database, appEnvironment, PARAM_ENDPOINT_PORT,
                          database.getDbInstance().getAttrEndpointPort());
    createStringParameter(database, appEnvironment, PARAM_DATABASE_NAME,
                          database.getDbInstance().getDbName());
    createStringParameter(database, appEnvironment, PARAM_SECURITY_GROUP_ID,
                          database.getDbSecurityGroup().getAttrGroupId());
    createStringParameter(database, appEnvironment, PARAM_SECRET_ARN,
                          database.getDbSecret().getSecretArn());
  }

  private static void createStringParameter(Construct scope, ApplicationEnvironment appEnvironment,
                                            String id, String stringValue) {
    StringParameter.Builder.create(scope, id)
                           .parameterName(parameterName(appEnvironment, id))
                           .stringValue(stringValue)
                           .build();
  }

  private static String parameterName(ApplicationEnvironment appEnvironment, String parameterName) {
    return joinedString(DASH_JOINER, appEnvironment.getEnvironmentName(),
                        appEnvironment.getApplicationName(), CONSTRUCT_NAME, parameterName);
  }
  // endregion


  // region output parameters

  public static ISecret getDataBaseSecret(Construct scope, OutputParameters outParams) {
    return Secret.fromSecretCompleteArn(scope, DATABASE_SECRET,
                                        Objects.requireNonNull(outParams.getDbSecretArn()));
  }

  public static String getParameter(Construct scope, ApplicationEnvironment appEnvironment,
                                    String id) {
    var parameterName = parameterName(appEnvironment, id);
    return StringParameter.fromStringParameterName(scope, id, parameterName).getStringValue();
  }

  public static String getDbEndpointAddress(Construct scope,
                                            ApplicationEnvironment appEnvironment) {
    return getParameter(scope, appEnvironment, PARAM_ENDPOINT_ADDRESS);
  }

  public static String getDbEndpointPort(Construct scope,
                                         ApplicationEnvironment appEnvironment) {
    return getParameter(scope, appEnvironment, PARAM_ENDPOINT_PORT);
  }

  public static String getDbName(Construct scope, ApplicationEnvironment appEnvironment) {
    return getParameter(scope, appEnvironment, PARAM_DATABASE_NAME);
  }

  public static String getDbSecretArn(Construct scope, ApplicationEnvironment appEnvironment) {
    return getParameter(scope, appEnvironment, PARAM_SECRET_ARN);
  }

  public static String getDbSecurityGroupId(Construct scope,
                                            ApplicationEnvironment appEnvironment) {
    return getParameter(scope, appEnvironment, PARAM_SECURITY_GROUP_ID);
  }

  /**
   * Returns a {@link Database} output parameters generated by a previously constructed
   * {@link Database} instance.
   *
   * @param scope          Scope construct to be provided to the SSM to retrieve the parameters.
   * @param appEnvironment {@link ApplicationEnvironment} to determine the application and
   *                       environment to which the output parameters
   *                       are associated to.
   *
   * @return An {@link OutputParameters} instance containing the parameters from the SSM.
   */
  public static OutputParameters outputParametersFrom(Construct scope,
                                                      ApplicationEnvironment appEnvironment) {
    return new OutputParameters(getDbEndpointAddress(scope, appEnvironment),
                                getDbEndpointPort(scope, appEnvironment),
                                getDbName(scope, appEnvironment),
                                getDbSecretArn(scope, appEnvironment),
                                getDbSecurityGroupId(scope, appEnvironment));
  }
  // endregion

  /**
   * Holds the input parameters to build a new {@link Database}.
   */
  @Getter(AccessLevel.PACKAGE)
  @lombok.Builder
  public static final class InputParameters {
    // see https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Concepts.DBInstanceClass.html#Concepts.DBInstanceClass.Support
    public static final String ENGINE_POSTGRES = "postgres";
    // see https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_PostgreSQL.html#PostgreSQL.Concepts.General.DBVersions
    public static final String DEFAULT_ENGINE_VERSION = "12.8";
    public static final String DEFAULT_INSTANCE_CLASS = "db.t2.micro";
    // see https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_Storage.html
    public static final StorageType SSD = StorageType.GP2;
    public static final StorageType MAGNETIC = StorageType.STANDARD;
    public static final StorageType PROVISIONED_IOPS = StorageType.IO1;

    private static final StorageType DEFAULT_STORAGE_TYPE = SSD;

    @lombok.Builder.Default
    private int storageCapacityInGB = 10;
    /**
     * RDB instance type.
     *
     * @see <a href="https://aws.amazon.com/rds/instance-types/">RDB Instance Types</a>
     */
    @lombok.Builder.Default
    private String instanceClass = DEFAULT_INSTANCE_CLASS;
    @lombok.Builder.Default
    private String engine = ENGINE_POSTGRES;
    @lombok.Builder.Default
    private String engineVersion = DEFAULT_ENGINE_VERSION;
    private boolean publiclyAccessible;
    private boolean protectedAgainstDeletion;
    @lombok.Builder.Default
    private boolean minorVersionAutoUpgradeEnabled = true;
    private boolean performanceInsightsEnabled;
    private boolean encryptionEnabled;
    @lombok.Builder.Default
    private int portNumber = 5432;
    @lombok.Builder.Default
    private StorageType storageType = DEFAULT_STORAGE_TYPE;
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-rds-database-instance.html#cfn-rds-dbinstance-backupretentionperiod
    @lombok.Builder.Default
    private Number backUpRetentionPeriodInDays = 1;
    // https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.html
    @lombok.Builder.Default
    private boolean deleteAutomatedBackupsEnabled = true;

    /**
     * Gets the initial storage capacity of the DB.
     *
     * @return A string representing the number in GB for the storage capacity to use for the DB.
     *
     * @see <a href="https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-rds-database-instance.html#cfn-rds-dbinstance-allocatedstorage">AWS::RDS::DBInstance.AllocatedStorage</a>
     */
    String getStorageCapacityInGBString() {
      return String.valueOf(storageCapacityInGB);
    }

    String getPort() {
      return String.valueOf(portNumber);
    }
  }

  /**
   * Holds the output parameters generated by a previously created {@link Database} construct.
   */
  @Getter
  @AllArgsConstructor(access = AccessLevel.PACKAGE)
  @EqualsAndHashCode
  public static final class OutputParameters {
    private final String endpointAddress;
    private final String endpointPort;
    private final String dbName;
    private final String dbSecretArn;
    private final String dbSecurityGroupId;
  }
}
