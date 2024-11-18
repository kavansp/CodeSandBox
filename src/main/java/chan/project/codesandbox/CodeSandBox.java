package chan.project.codesandbox;

import chan.project.codesandbox.model.CodeRequest;
import chan.project.codesandbox.model.CodeResponse;

/**
 * 代码沙箱的接口
 */
public interface CodeSandBox {

    CodeResponse executeCode(CodeRequest request);

}
