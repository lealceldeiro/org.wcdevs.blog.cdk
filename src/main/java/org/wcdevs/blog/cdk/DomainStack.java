package org.wcdevs.blog.cdk;

import lombok.AccessLevel;
import lombok.Getter;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.certificatemanager.DnsValidatedCertificate;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationActionProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListenerLookupOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListenerRule;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListenerRuleProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancerAttributes;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerAction;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCondition;
import software.amazon.awscdk.services.elasticloadbalancingv2.RedirectOptions;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;

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
  private DomainStack(Construct scope, String id, Environment awsEnvironment,
                      ApplicationEnvironment applicationEnvironment) {
    super(scope, id, StackProps.builder()
                               .stackName(applicationEnvironment.prefixed("Domain"))
                               .env(awsEnvironment)
                               .build());
  }

  /**
   * Creates a new Domain Stack with the default input configuration.
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
   *
   * @see InputParameters
   */
  public static DomainStack newInstance(Construct scope, String id, Environment awsEnvironment,
                                        ApplicationEnvironment applicationEnvironment,
                                        String hostedZoneDomainName, String applicationDomainName) {
    return newInstance(scope, id, awsEnvironment, applicationEnvironment, hostedZoneDomainName,
                       applicationDomainName, InputParameters.builder().build());
  }

  /**
   * Creates a new Domain Stack by accepting configuration input parameters.
   *
   * @param scope                  Scope for the Domain Stack construct to be created.
   * @param id                     A unique identifier.
   * @param awsEnvironment         AWS Environment.
   * @param applicationEnvironment Same Application Environment a previously {@link Network} (and
   *                               others) was deployed.
   * @param hostedZoneDomainName   The (sub) domain name of the hosted zone.
   * @param applicationDomainName  The application (sub) domain.
   * @param inputParameters        The {@link InputParameters} with optional configurations.
   *
   * @return The newly created {@link DomainStack}.
   */
  public static DomainStack newInstance(Construct scope, String id, Environment awsEnvironment,
                                        ApplicationEnvironment applicationEnvironment,
                                        String hostedZoneDomainName, String applicationDomainName,
                                        InputParameters inputParameters) {
    var inParams = Objects.requireNonNull(inputParameters);
    var domainStack = new DomainStack(Objects.requireNonNull(scope), Objects.requireNonNull(id),
                                      Objects.requireNonNull(awsEnvironment),
                                      Objects.requireNonNull(applicationEnvironment));

    var hostedZone = hostedZone(domainStack, Objects.requireNonNull(hostedZoneDomainName));
    var networkParams = Network.outputParametersFrom(domainStack,
                                                     applicationEnvironment.getEnvironmentName());
    var albAttrs = ApplicationLoadBalancerAttributes
        .builder()
        .loadBalancerArn(networkParams.getLoadBalancerArn())
        .securityGroupId(networkParams.getLoadbalancerSecurityGroupId())
        .loadBalancerCanonicalHostedZoneId(networkParams.getLoadBalancerCanonicalHostedZoneId())
        .loadBalancerDnsName(networkParams.getLoadBalancerDnsName())
        .build();
    var appLoadBalancer = ApplicationLoadBalancer
        .fromApplicationLoadBalancerAttributes(domainStack, "AppLoadBalancer", albAttrs);

    if (inParams.isSslCertificateActivated()) {
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

  @lombok.Builder
  @Getter(AccessLevel.PACKAGE)
  public static final class InputParameters {
    @lombok.Builder.Default
    private boolean sslCertificateActivated = true;
  }
}
