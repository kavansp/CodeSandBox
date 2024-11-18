package chan.project.codesandbox.TemplateMethod;

import chan.project.codesandbox.model.ExecuteMessage;
import cn.hutool.core.util.ArrayUtil;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DockerJavaSandBoxTemplate extends JavaCodeSandBoxTemplate {
    //判断是否拉取镜像
    public static Boolean IS_INIT = true;

    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        //3.创建docker容器，将编译的代码传入到容器内
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        //由于需要执行java代码，所以需要拉取java镜像
        //拉取镜像(由于每一次执行代码都会执行一次这个方法，所以就可以只有第一次的时候拉取镜像)
        String image = "openjdk:8-alpine";//镜像名称(在hubdocker.com上获取)
        ListImagesCmd listImagesCmd = dockerClient.listImagesCmd();
        List<Image> imageList = listImagesCmd.exec();
        for (Image hadImage : imageList) {
            String[] repoTags = hadImage.getRepoTags();
            for (String repoTag : repoTags) {
                if(repoTag.equals(image)){
                    IS_INIT = false;
                    break;
                }
            }
        }

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
        System.out.println("父目录 "+userCodeParentPath);
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
            System.out.println("执行目录 "+ Arrays.toString(cmdArray));
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
            long Time;
            final Long[] memory = {0L};
            final Boolean[] time = {true};
            String execId = execCreateCmdResponse.getId();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        ErrorMessage[0] = new String(frame.getPayload());
                        System.out.print("输出错误结果：" + ErrorMessage[0]);
                    } else {
                        Message[0] = new String(frame.getPayload());
                        System.out.print("输出结果：" + Message[0]);
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
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    if(memory[0] != 0) return;
                    memory[0] = Math.max(statistics.getMemoryStats().getUsage(),memory[0]);
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
            statsCmd.exec(statisticsResultCallback);
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
            //防止线程未结束就执行返回
            long timeOut = 0;
            System.out.println("输出信息"+ Message[0] + "内存" + memory[0]);
            while(Message[0] == null || memory[0] == 0){
                try {
                    Thread.sleep(500);
                    timeOut += 500;
                    //重试达到三秒就直接返回
                    if(timeOut >= 4000){
                        break;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println("输出信息"+ Message[0] + "内存" + memory[0]);
            runExecuteMessage.setTime(Time);
            runExecuteMessage.setErrorMessage(ErrorMessage[0]);
            runExecuteMessage.setMessage(Message[0]);
            runExecuteMessage.setMemory(memory[0]);
            executeMessageList.add(runExecuteMessage);
        }
        return executeMessageList;
    }
}
