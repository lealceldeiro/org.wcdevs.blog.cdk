package org.wcdevs.blog.cdk;

import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.certificatemanager.DnsValidatedCertificate;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancerAttributes;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;
import software.constructs.Construct;

import java.util.List;
import java.util.Objects;

/**
 * Represents a constructs to create a Domain stack. The following parameters need to exist in the
 * AWS parameter store (SSM) associated to the same environment the Domain instance is created for
 * this construct to successfully deploy:
 * <ul>
 *   <li>An Elastic Load Balancer Arn, in a previously deployed {@link Network}</li>
 *   <li>An Elastic Load Balancer security group ID, in a previously deployed {@link Network}</li>
 *   <li>An Elastic Load Balancer canonical hosted zone ID, in a previously deployed
 *   {@link Network}</li>
 *   <li>An Elastic Load Balancer DNS name, in a previously deployed {@link Network}</li>
 * </ul>
 */
public final class DomainStack extends Stack {
  private static final String CONSTRUCT_NAME = "domain-stack";

  private DomainStack(Construct scope, String id, Environment awsEnvironment,
                      ApplicationEnvironment applicationEnvironment) {
    super(scope, id, StackProps.builder()
                               .stackName(applicationEnvironment.prefixed(CONSTRUCT_NAME))
                               .env(awsEnvironment)
                               .build());
  }

  /**
   * Creates a new Domain Stack.
   *
   * @param scope                  Scope for the Domain Stack construct to be created.
   * @param id                     A unique identifier.
   * @param awsEnvironment         AWS Environment.
   * @param applicationEnvironment Same Application Environment a previously {@link Network} (and
   *                               others) was deployed.
   * @param hostedZoneDomainName   The (sub) domain name of the hosted zone.
   * @param applicationDomainName  The application (sub) domain.
   *
   * @return The newly created {@link DomainStack}.
   */
  public static DomainStack newInstance(Construct scope, String id, Environment awsEnvironment,
                                        ApplicationEnvironment applicationEnvironment,
                                        String hostedZoneDomainName, String applicationDomainName) {
    var domainStack = new DomainStack(Objects.requireNonNull(scope), Objects.requireNonNull(id),
                                      Objects.requireNonNull(awsEnvironment),
                                      Objects.requireNonNull(applicationEnvironment));

    var hostedZone = hostedZone(domainStack, Objects.requireNonNull(hostedZoneDomainName));
    var networkParams = Network.outputParametersFrom(domainStack, applicationEnvironment);
    var albAttrs = ApplicationLoadBalancerAttributes
        .builder()
        .loadBalancerArn(networkParams.getLoadBalancerArn())
        .securityGroupId(networkParams.getLoadbalancerSecurityGroupId())
        .loadBalancerCanonicalHostedZoneId(networkParams.getLoadBalancerCanonicalHostedZoneId())
        .loadBalancerDnsName(networkParams.getLoadBalancerDnsName())
        .build();
    var appLoadBalancer = ApplicationLoadBalancer
        .fromApplicationLoadBalancerAttributes(domainStack, "AppLoadBalancer", albAttrs);

    if (Network.isArnNotNull(networkParams.getSslCertificateArn())) {
      DnsValidatedCertificate.Builder.create(domainStack, "AppCertificate")
                                     .hostedZone(hostedZone)
                                     .region(awsEnvironment.getRegion())
                                     .domainName(applicationDomainName)
                                     .subjectAlternativeNames(List.of(applicationDomainName))
                                     .build();
    }

    ARecord.Builder.create(domainStack, "ARecord")
                   .recordName(applicationDomainName)
                   .zone(hostedZone)
                   .target(RecordTarget.fromAlias(new LoadBalancerTarget(appLoadBalancer)))
                   .build();
    applicationEnvironment.tag(domainStack);

    return domainStack;
  }

  private static IHostedZone hostedZone(Construct scope, String hostedZoneDomainName) {
    var hostedZoneProviderProps = HostedZoneProviderProps.builder()
                                                         .domainName(hostedZoneDomainName)
                                                         .build();
    return HostedZone.fromLookup(scope, "HostedZone", hostedZoneProviderProps);
  }
}
