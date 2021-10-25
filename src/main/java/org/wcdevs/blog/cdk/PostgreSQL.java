package org.wcdevs.blog.cdk;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.services.ec2.CfnSecurityGroup;
import software.amazon.awscdk.services.rds.CfnDBInstance;
import software.amazon.awscdk.services.rds.CfnDBSubnetGroup;
import software.amazon.awscdk.services.secretsmanager.CfnSecretTargetAttachment;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;
import software.amazon.awscdk.services.ssm.StringParameter;

import java.util.List;
import java.util.Objects;

import static org.wcdevs.blog.cdk.Util.joinedString;

/**
 * Represents a constructs to create PostgreSQL database in an isolated subnet of a given Vpc. The
 * following parameters need to exist in the AWS parameter store (SSM) associated to the same
 * environment the PostgreSQL DB instance is created for this construct to successfully deploy:
 * <ul>
 *   <li>A {@link Network} vpc id </li>
 *   <li>A {@link Network} isolated subnets (2 at least)</li>
 *   <li>A {@link Network} availability zone</li>
 * </ul>
 */
@Setter(AccessLevel.PRIVATE)
@Getter(AccessLevel.PACKAGE)
public final class PostgreSQL extends Construct {
  private static final String ENGINE = "postgres";
  // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-rds-database-instance.html
  private static final String TARGET_TYPE_AWS_RDS_DBINSTANCE = "AWS::RDS::DBInstance";
  private static final String USERNAME_SECRET_HOLDER = "username";
  private static final String PASSWORD_SECRET_HOLDER = "password";
  private static final String CONSTRUCT_NAME = "PostgreSql";
  private static final String DASH_JOINER = "-";
  private static final String PARAM_ENDPOINT_ADDRESS = "endpointAddress";
  private static final String PARAM_ENDPOINT_PORT = "endpointPort";
  private static final String PARAM_DATABASE_NAME = "databaseName";
  private static final String PARAM_SECURITY_GROUP_ID = "securityGroupId";
  private static final String PARAM_SECRET_ARN = "secretArn";

  private CfnSecurityGroup dbSecurityGroup;
  private Secret dbSecret;
  private CfnDBInstance dbInstance;

  private PostgreSQL(Construct scope, String id) {
    super(scope, id);
  }

  /**
   * Creates a new {@link PostgreSQL} from a given scope, construct id, an
   * {@link ApplicationEnvironment} and some input parameters to configure the DB instance.
   *
   * @param scope                  Construct scope.
   * @param id                     Construct id.
   * @param applicationEnvironment {@link ApplicationEnvironment} in which the DB instance will be
   *                               deployed.
   * @param inputParameters        {@link InputParameters} with configured values to creaet the DB
   *                               instance.
   *
   * @return A new {@link PostgreSQL} instance.
   */
  public static PostgreSQL newInstance(Construct scope, String id,
                                       ApplicationEnvironment applicationEnvironment,
                                       InputParameters inputParameters) {
    var inParams = Objects.requireNonNull(inputParameters);
    var postgreSql = new PostgreSQL(Objects.requireNonNull(scope), Objects.requireNonNull(id));

    // retrieve network output params from SSM
    // IVpc#fromLookup is broken (https://github.com/aws/aws-cdk/issues/3600)
    var netOutParams = Network.outputParametersFrom(postgreSql,
                                                    applicationEnvironment.getEnvironmentName());
    var availabilityZones = netOutParams.getAvailabilityZones();
    if (availabilityZones == null || availabilityZones.isEmpty()) {
      throw new IllegalArgumentException("No availability zones in network");
    }

    var vpcId = netOutParams.getVpcId();
    if (vpcId == null) {
      throw new IllegalArgumentException("No VPC in network");
    }
    var secGroup = cfnSecurityGroup(postgreSql, vpcId,
                                    applicationEnvironment.prefixed("postgresSqlSecurityGroup"));
    postgreSql.setDbSecurityGroup(secGroup);

    var username = Util.dbSanitized(applicationEnvironment.prefixed("pgsqluser"));
    var dbSecret = dbSecret(postgreSql, applicationEnvironment.prefixed("postgresqlSecret"),
                            username);
    postgreSql.setDbSecret(dbSecret);

    var subnetGroupName = applicationEnvironment.prefixed("postgreSqlSubnetGroup");
    var subnetGroup = cfnDBSubnetGroup(postgreSql, subnetGroupName,
                                       netOutParams.getIsolatedSubnets());

    subnetGroupName = subnetGroup.getDbSubnetGroupName();
    var dbName = Util.dbSanitized(applicationEnvironment.prefixed("database"));
    var dbPassword = dbSecret.secretValueFromJson(PASSWORD_SECRET_HOLDER).toString();
    var dbInstance = dbInstance(postgreSql, inParams, availabilityZones.get(0), subnetGroupName,
                                dbName, username, dbPassword, secGroup.getAttrGroupId(), false);
    postgreSql.setDbInstance(dbInstance);

    cfnSecretTargetAttachment(postgreSql, dbSecret.getSecretArn(), dbInstance.getRef());

    savePostgreSqlInfoToParameterStore(postgreSql, applicationEnvironment);
    applicationEnvironment.tag(postgreSql);

    return postgreSql;
  }

  // region helpers
  private static CfnSecurityGroup cfnSecurityGroup(Construct scope, String vpcId,
                                                   String securityGroupName) {
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-ec2-security-group.html
    return CfnSecurityGroup.Builder.create(scope, "postgreSqlSecurityGroup")
                                   .vpcId(vpcId)
                                   .groupDescription("PostgreSql database security group")
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
    return Secret.Builder.create(scope, "postgreSqlSecret")
                         .secretName(secretName)
                         .description("Credentials to be used by the RDB (PostgreSQL) instance")
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
    return CfnDBSubnetGroup.Builder.create(scope, "postgreSqlSubnetGroup")
                                   .dbSubnetGroupDescription("RDB (PostgreSql) subnet group")
                                   .dbSubnetGroupName(subnetGroupName)
                                   .subnetIds(subnetIds)
                                   .build();
  }

  private static CfnDBInstance dbInstance(Construct scope, InputParameters inputParameters,
                                          String availabilityZone, String subnetGroupName,
                                          String dbName, String dbUsername, String dbPassword,
                                          String securityGroupId, boolean publiclyAccessible) {
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-rds-database-instance.html
    return CfnDBInstance.Builder.create(scope, "postgreSqlInstance")
                                .allocatedStorage(inputParameters.getStorageCapacityInGBString())
                                .availabilityZone(availabilityZone)
                                .dbInstanceClass(inputParameters.getInstanceClass())
                                .dbName(dbName)
                                .dbSubnetGroupName(subnetGroupName)
                                .engine(ENGINE)
                                .engineVersion(inputParameters.getPostgresVersion())
                                .masterUsername(dbUsername)
                                .masterUserPassword(dbPassword)
                                .publiclyAccessible(publiclyAccessible)
                                .vpcSecurityGroups(List.of(securityGroupId))
                                .build();
  }

  private static CfnSecretTargetAttachment cfnSecretTargetAttachment(Construct scope,
                                                                     String dbSecretArn,
                                                                     String dbRef) {
    // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-secretsmanager-secrettargetattachment.html
    return CfnSecretTargetAttachment.Builder.create(scope, "postgreSqlSecretTargetAttachment")
                                            .secretId(dbSecretArn)
                                            .targetId(dbRef)
                                            .targetType(TARGET_TYPE_AWS_RDS_DBINSTANCE)
                                            .build();
  }

  private static void savePostgreSqlInfoToParameterStore(PostgreSQL postgreSQL,
                                                         ApplicationEnvironment appEnvironment) {
    createStringParameter(postgreSQL, appEnvironment, PARAM_ENDPOINT_ADDRESS,
                          postgreSQL.getDbInstance().getAttrEndpointAddress());
    createStringParameter(postgreSQL, appEnvironment, PARAM_ENDPOINT_PORT,
                          postgreSQL.getDbInstance().getAttrEndpointPort());
    createStringParameter(postgreSQL, appEnvironment, PARAM_DATABASE_NAME,
                          postgreSQL.getDbInstance().getDbName());
    createStringParameter(postgreSQL, appEnvironment, PARAM_SECURITY_GROUP_ID,
                          postgreSQL.getDbSecurityGroup().getAttrGroupId());
    createStringParameter(postgreSQL, appEnvironment, PARAM_SECRET_ARN,
                          postgreSQL.getDbSecret().getSecretArn());
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
   * Returns a {@link PostgreSQL} output parameters generated by a previously constructed
   * {@link PostgreSQL} instance.
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

  public static InputParameters newInputParameters() {
    return new InputParameters();
  }

  public static InputParameters newInputParameters(int storageCapacityInGB,
                                                   String instanceClass, String postgresVersion) {
    return new InputParameters(storageCapacityInGB, instanceClass, postgresVersion);
  }

  /**
   * Holds the input parameters to build a new {@link PostgreSQL}.
   */
  @Setter
  @Getter(AccessLevel.PACKAGE)
  @AllArgsConstructor(access = AccessLevel.PACKAGE)
  @NoArgsConstructor(access = AccessLevel.PACKAGE)
  @EqualsAndHashCode
  public static class InputParameters {
    private int storageCapacityInGB = 10;
    /**
     * RDB instance type.
     *
     * @see <a href="https://aws.amazon.com/rds/instance-types/">RDB Instance Types</a>
     */
    private String instanceClass = "db.t2.micro";
    private String postgresVersion = "13.4";

    String getStorageCapacityInGBString() {
      return String.valueOf(storageCapacityInGB);
    }
  }

  /**
   * Holds the output parameters generated by a previously created {@link PostgreSQL} construct.
   */
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
