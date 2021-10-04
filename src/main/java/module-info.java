module org.wcdevs.blog.cdk {
  requires lombok;
  requires core;
  requires ecr;
  requires iam;
  requires ec2;
  requires ecs;
  requires elasticloadbalancingv2;
  requires ssm;

  exports org.wcdevs.blog.cdk;
}
