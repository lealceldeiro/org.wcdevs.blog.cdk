package org.wcdevs.blog.cdk;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import software.amazon.awscdk.core.IConstruct;
import software.amazon.awscdk.core.Tags;

@Getter
@RequiredArgsConstructor
public final class ApplicationEnvironment {
  private final String applicationName;
  private final String environmentName;

  @Override
  public String toString() {
    return Util.sanitize(Util.joinedString(Util.DASH_JOINER, environmentName, applicationName));
  }

  public String prefixed(String string) {
    return Util.joinedString(Util.DASH_JOINER, this, string);
  }

  public void tag(IConstruct construct) {
    Tags.of(construct).add("environment", environmentName);
    Tags.of(construct).add("application", applicationName);
  }
}
