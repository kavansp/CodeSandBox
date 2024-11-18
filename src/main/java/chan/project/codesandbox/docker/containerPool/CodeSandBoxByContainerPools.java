package chan.project.codesandbox.docker.containerPool;

import chan.project.codesandbox.model.ExecuteMessage;
import chan.project.codesandbox.model.containerPool.CmdResponse;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.springframework.util.StopWatch;
import chan.project.codesandbox.TemplateMethod.JavaCodeSandBoxTemplate;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
//“容器池”改造项目

/**
 * 使用容器池的代码沙箱
 */
public class CodeSandBoxByContainerPools extends JavaCodeSandBoxTemplate {

    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        //获取本级父目录
        String userCodeParentPath = File.separator+userCodeFile.getParentFile().getName();
        DockerContainerPool dockerContainerPool = new DockerContainerPool();
        DockerClient dockerClient = dockerContainerPool.getDockerClient();

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        //执行命令
        for (String input : inputList) {
            StopWatch stopWatch = new StopWatch();
            CmdResponse cmdResponse = dockerContainerPool.getExecCreateCmdResponse(input, userCodeParentPath);
            ExecCreateCmdResponse execCreateCmdResponse = cmdResponse.getExecCreateCmdResponse();
            String containerId = cmdResponse.getContainerId();
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
            //防止容器未结束就执行返回
            long timeOut = 0;
            System.out.println("输出信息"+ Message[0] + "内存" + memory[0]);
            while(Message[0] == null || memory[0] == 0){
                try {
                    Thread.sleep(500);
                    timeOut += 500;
                    //重试达到四秒就直接返回
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