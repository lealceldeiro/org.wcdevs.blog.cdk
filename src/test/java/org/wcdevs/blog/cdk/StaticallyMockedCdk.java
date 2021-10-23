package org.wcdevs.blog.cdk;

import com.fasterxml.jackson.databind.node.TextNode;
import software.amazon.jsii.JsiiClient;
import software.amazon.jsii.JsiiEngine;
import software.amazon.jsii.JsiiObjectMapper;
import software.amazon.jsii.Kernel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

final class StaticallyMockedCdk {
  private StaticallyMockedCdk() {
  }

  @FunctionalInterface
  interface TestExecution {
    void run();
  }

  static void executeTest(TestExecution testExecution) {
    try (
        var kernel = mockStatic(Kernel.class);
        var jsiiEngine = mockStatic(JsiiEngine.class);
        var jsiiObjMapper = mockStatic(JsiiObjectMapper.class)
    ) {
      kernel.when(() -> Kernel.get(any(), any(), any())).then(TestsUtil::kernelAnswer);

      var jsiiClientMock = mock(JsiiClient.class);
      when(jsiiClientMock.getStaticPropertyValue(any(), any())).thenReturn(new TextNode("mocked"));
      var jsiiEngineMock = mock(JsiiEngine.class);
      when(jsiiEngineMock.getClient()).thenReturn(jsiiClientMock);
      jsiiEngine.when(JsiiEngine::getInstance).thenReturn(jsiiEngineMock);

      jsiiObjMapper.when(() -> JsiiObjectMapper.treeToValue(any(), any()))
                   .then(TestsUtil::jsiiObjectMapperAnswer);

      testExecution.run();
    }
  }
}
