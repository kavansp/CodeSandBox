package chan.project.codesandbox;

import chan.project.codesandbox.model.*;
import chan.project.codesandbox.utils.ProcessUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DockerJavaCodeSandBox implements CodeSandBox {
    //保存文件的位置
    public static final String GLOBAL_CODE_DIR_NAME = "src/main/resources/ExecuteCode";
    //文件名称，不应该让用户自定义文件名称
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    //程序运行超时时间
    public static final Long TIME_OUT = 100000L;
    //判断是否拉取镜像
    public static final boolean IS_INIT = false;

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
            //返回编译信息
            ExecuteMessage executeMessage = ProcessUtil.getProcess(process);
            System.out.println("编译信息："+executeMessage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //3.创建docker容器，将编译的代码传入到容器内
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        //由于需要执行java代码，所以需要拉取java镜像
        //拉取镜像(由于每一次执行代码都会执行一次这个方法，所以就可以只有第一次的时候拉取镜像)
        String image = "openjdk:8-alpine";//镜像名称(在hubdocker.com上获取)
        //openjdk:8-alpine
        if(IS_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                //执行拉取
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
            System.out.println("下载完成");
        }
        //创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        //自定义根目录
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        hostConfig.withMemory(100*1000*1000L);//设置最大执行内存
        CreateContainerResponse createContainerResponse = containerCmd
                //四个true开启支持容器进行交互式调用，可以传入也可以传出信息
                .withNetworkDisabled(true)//关闭网络调用
                .withHostConfig(hostConfig)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        //4.启动容器，执行代码
        //启动容器
        dockerClient.startContainerCmd(containerId).exec();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        //执行命令
        for (String s : inputList) {
            StopWatch stopWatch = new StopWatch();
            //创建执行命令
            //注意这里的参数不能使用一个字符串之既然分割，而是需要进行分组处理
            //比如当前的input是1 3，你就需要将它变为数组[1.3],因为参数会吧一个字符串当成一个参数来使用；
            String[] input = s.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, input);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withCmd(cmdArray)
                    .exec();
            System.out.println("创建执行命令"+execCreateCmdResponse);
            //执行命令
            ExecuteMessage runExecuteMessage = new ExecuteMessage();
            final String[] Message = {null};
            final String[] ErrorMessage = {null};
            Long Time = 0L;
            final Long[] memory = {null};
            final Boolean[] time = {true};
            String execId = execCreateCmdResponse.getId();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        ErrorMessage[0] = new String(frame.getPayload());;
                        System.out.println("输出错误结果：" + ErrorMessage[0]);
                    } else {
                        Message[0] = new String(frame.getPayload());
                        System.out.println("输出结果：" + Message[0]);
                    }
                    super.onNext(frame);
                }

                @Override
                public void onComplete() {
                    time[0] = false;
                    super.onComplete();
                }
            };
            //获取执行内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> exec = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    memory[0] = Math.max(statistics.getMemoryStats().getUsage(),memory[0]);
                    System.out.println("内存"+memory[0]);
                }
                @Override
                public void onComplete() {

                }
                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void close() throws IOException {

                }
            });
            statsCmd.exec(exec);
            try {
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion();
                stopWatch.stop();
                Time = stopWatch.getLastTaskTimeMillis();
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            //过滤信息，包含
            runExecuteMessage.setTime(Time);
            runExecuteMessage.setErrorMessage(ErrorMessage[0]);
            runExecuteMessage.setMessage(Message[0]);
            runExecuteMessage.setMemory(memory[0]);
            executeMessageList.add(runExecuteMessage);
        }
        //4.整理所有的输出结果
        CodeResponse codeResponse = new CodeResponse();
        List<String> outputList = new ArrayList<>();
        List<JudgeInfo> judgeInfoList = new ArrayList<>();
        for (ExecuteMessage message : executeMessageList) {
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
        //6.返回执行信息
        return codeResponse;
    }
}
