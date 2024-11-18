package chan.project.codesandbox;

import chan.project.codesandbox.model.*;
import chan.project.codesandbox.utils.ProcessUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.util.StopWatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JavaCodeSandBox implements CodeSandBox{
    //保存文件的位置
    public static final String GLOBAL_CODE_DIR_NAME = "src/main/resources/ExecuteCode";
    //文件名称，不应该让用户自定义文件名称
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    //程序运行超时时间
    public static final Long TIME_OUT = 5000L;

    @Override
    public CodeResponse executeCode(CodeRequest codeRequest) {
        List<String> inputList = codeRequest.getInputList();
        String code = codeRequest.getCode();
        String language = codeRequest.getLanguage();

        //1.将用户输入的代码保存为文件
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断全局代码目录是否存在，没有则新建
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        // 使用UUID生成一个专属于用户的文件名
        // 这里需要注意的是在使用“/”表示文件目录时java不能直接使用+"/"+，而是要使用方法File.separator
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        //写入文件
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        //2.编译文件
        //使用Runtime.getRuntime().exec()来执行javac命令
        //修改文件编译格式
        String changeEncoding = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        Process process = null;
        try {
            //编译代码
            process = Runtime.getRuntime().exec(changeEncoding);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //返回编译信息
        ExecuteMessage executeMessage = ProcessUtil.getProcess(process);
        //3.执行文件
        List<ExecuteMessage> executeMessages = new ArrayList<>();
        //获取程序执行时间
        StopWatch stopWatch = new StopWatch();
        for (String inputArgs : inputList) {
            //执行命令中控制代码的运行内存，防止内存溢出
            String runCmd = String.format("java -Xms256m -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                stopWatch.start();
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                //设置守护线程，判断用户执行的代码是否会超过执行时间
                new Thread(()->{
                    try {
                        Thread.sleep(TIME_OUT);
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
                ExecuteMessage runExecuteMessage = ProcessUtil.getProcess(process);
                stopWatch.stop();
                runExecuteMessage.setTime(stopWatch.getLastTaskTimeMillis());
                executeMessages.add(runExecuteMessage);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        //4.整理所有的输出结果
        // 保存所有的运行内存暂时未实现
        CodeResponse codeResponse = new CodeResponse();
        List<String> outputList = new ArrayList<>();
        List<JudgeInfo> judgeInfoList = new ArrayList<>();
        for (ExecuteMessage message : executeMessages) {
            //如果程序执行中有错误就不需要再返回执行信息了，只需要返回错误信息
            String errorMessage = message.getErrorMessage();
            JudgeInfo judgeInfo = new JudgeInfo();
            if(ObjectUtil.isNotEmpty(errorMessage)){
                codeResponse.setMessage(errorMessage);
                codeResponse.setStatus(3);
                //判断为编译错误，因为编译错误才会有错误输出
                judgeInfo.setMessage(JudgeInfoMessageEnum.COMPILE_ERROR.getText());
                judgeInfoList.add(judgeInfo);
                break;
            }
            outputList.add(message.getMessage().trim());
            judgeInfo.setExeTime(message.getTime());
            judgeInfo.setExeMemory(message.getMemory());
            judgeInfo.setMessage(JudgeInfoMessageEnum.SUCCESS.getText());
            judgeInfoList.add(judgeInfo);
        }
        codeResponse.setOutput(outputList)
                .setStatus(1)
                .setJudgeInfoList(judgeInfoList);
        //5.清理执行完成的文件
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }
        System.out.println("输出结果"+codeResponse);
        //6.返回执行信息
        return codeResponse;
    }
}
