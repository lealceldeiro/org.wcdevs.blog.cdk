# org.wcdevs.blog.cdk

[![License: Apache](https://img.shields.io/badge/License-Apache%202.0-blue)](https://opensource.org/licenses/Apache-2.0) [![maven-central](https://img.shields.io/maven-central/v/org.wcdevs.blog/cdk?style=flat)](https://mvnrepository.com/artifact/org.wcdevs.blog/cdk) [![Maven Build](https://github.com/lealceldeiro/org.wcdevs.blog.cdk/actions/workflows/maven.yml/badge.svg)](https://github.com/lealceldeiro/org.wcdevs.blog.cdk/actions/workflows/maven.yml) [![CodeQL](https://github.com/lealceldeiro/org.wcdevs.blog.cdk/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/lealceldeiro/org.wcdevs.blog.cdk/actions/workflows/codeql-analysis.yml) [![codecov](https://codecov.io/gh/lealceldeiro/org.wcdevs.blog.cdk/branch/main/graph/badge.svg)](https://codecov.io/gh/lealceldeiro/org.wcdevs.blog.cdk)

Holds CDK constructs with an opinionated AWS CDK configured resources which provides capabilities
to define AWS cloud resources. Many of the configuration can be modified via input arguments.

Users of this library, should use Java 17+ and the following versions of these dependencies as well

- [software.amazon.awscdk:aws-cdk-lib:2.31.1](https://mvnrepository.com/artifact/software.amazon.awscdk/aws-cdk-lib).
- [software.constructs:constructs:10.1.43](https://mvnrepository.com/artifact/software.constructs/constructs)

This is a [Maven](https://maven.apache.org/) based project, so you can open it with any Maven
compatible Java IDE to build and run tests.

To add it as a dependency from Maven Central to your project, add it to your `pom.xml` as follows,
by replacing `${cdk-construct-version}` with the displayed version on the top badge.

```xml
<dependencies>
  <dependency>
    <groupId>org.wcdevs.blog</groupId>
    <artifactId>cdk</artifactId>
    <version>${cdk-construct-version}</version>
  </dependency>
</dependencies>
```
For more options, see https://mvnrepository.com/artifact/org.wcdevs.blog/cdk
