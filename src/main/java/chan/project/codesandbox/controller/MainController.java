package chan.project.codesandbox.controller;

import chan.project.codesandbox.TemplateMethod.JavaNativeCodeSandBox;
import chan.project.codesandbox.docker.containerPool.CodeSandBoxByContainerPools;
import chan.project.codesandbox.model.CodeRequest;
import chan.project.codesandbox.model.CodeResponse;
import chan.project.codesandbox.model.ExecuteMessage;
import chan.project.codesandbox.utils.TestDemo;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController("/")
public class MainController {

    private final StringHttpMessageConverter stringHttpMessageConverter;

    public MainController(StringHttpMessageConverter stringHttpMessageConverter) {
        this.stringHttpMessageConverter = stringHttpMessageConverter;
    }

    @GetMapping("/test")
    public List<ExecuteMessage> healthCheck() {
        return TestDemo.doJava();
    }

    @GetMapping("/doJavaByNative")
    public CodeResponse doJava(){
        JavaNativeCodeSandBox javaNativeCodeSandBox = new JavaNativeCodeSandBox();

        CodeRequest codeRequest = new CodeRequest();
        ArrayList<String> strings = new ArrayList<>();
        strings.add("1 4");
        strings.add("10085 1");
        codeRequest.setInputList(strings);
        codeRequest.setCode("public class Main {" +
                "    public static void main(String[] args) {" +
                "        int a = Integer.parseInt(args[0]);" +
                "        int b = Integer.parseInt(args[1]);" +
                "        System.out.println(a + b);" +
                "    }" +
                "}");
        codeRequest.setLanguage("java");
        CodeResponse codeResponse = javaNativeCodeSandBox.executeCode(codeRequest);
        return codeResponse;
    }

    @GetMapping("doJavaByDocker")
    public CodeResponse doCode(){
//        DockerJavaSandBoxTemplate dockerJavaSandBoxTemplate = new DockerJavaSandBoxTemplate();
        CodeSandBoxByContainerPools containerPools = new CodeSandBoxByContainerPools();
        CodeRequest codeRequest = new CodeRequest();
        ArrayList<String> strings = new ArrayList<>();
        strings.add("1 4");
        strings.add("10085 1");
        codeRequest.setInputList(strings);
        codeRequest.setCode("public class Main {" +
                "    public static void main(String[] args) {" +
                "        int a = Integer.parseInt(args[0]);" +
                "        int b = Integer.parseInt(args[1]);" +
                "        System.out.println(\"结果:\" + (a + b));" +
                "    }" +
                "}");
        codeRequest.setLanguage("java");
//        CodeResponse codeResponse = dockerJavaSandBoxTemplate.executeCode(codeRequest);
        CodeResponse codeResponse = containerPools.executeCode(codeRequest);
        return codeResponse;
    }
}
