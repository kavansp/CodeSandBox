package chan.project.codesandbox.utils;

import chan.project.codesandbox.model.ExecuteMessage;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DefaultDockerCmdExecFactory;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestDemo {
    public static List<ExecuteMessage> doJava(){
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        ArrayList<String> inputList = new ArrayList<>();
        inputList.add("1 4");
        inputList.add("10085 1");
        String userCodeParentPath = File.separator+"18c17e28-8e14-4eb4-821f-2240841b0356";
        String containerId = "1606733fe830";

        //1606733fe830
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        //执行命令
        for (String s : inputList) {
            StopWatch stopWatch = new StopWatch();
            //创建执行命令
            //注意这里的参数不能使用一个字符串之既然分割，而是需要进行分组处理
            //比如当前的input是1 3，你就需要将它变为数组[1.3],因为参数会吧一个字符串当成一个参数来使用；
            String[] input = s.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app"+userCodeParentPath, "Main"}, input);
            System.out.println("执行目录 "+ Arrays.toString(cmdArray));
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withCmd(cmdArray)
                    .exec();
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
