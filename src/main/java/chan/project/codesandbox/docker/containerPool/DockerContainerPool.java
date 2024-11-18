package chan.project.codesandbox.docker.containerPool;

import chan.project.codesandbox.model.containerPool.CmdResponse;
import chan.project.codesandbox.model.containerPool.ContainerStatus;
import chan.project.codesandbox.model.ExecuteMessage;
import chan.project.codesandbox.utils.DockerUtils;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.Getter;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 代码沙箱专属容器池管理
 */
public class DockerContainerPool {
    
    private String image = "openjdk:8-alpine";

    @Getter
    private final DockerClient dockerClient;

    //核心容器数
    private final int coreContainerNumber = 10;

    //最大容器数
    private final int maxContainerNumber = 20;

    /**
     * 初始化进行jdk8镜像的拉取
     */
    DockerContainerPool(){
        dockerClient = DockerClientBuilder.getInstance().build();
        List<Image> images = dockerClient.listImagesCmd().exec();
        for (Image image : images) {
            String[] repoTags = image.getRepoTags();
            for (String repoTag : repoTags) {
                //已经存在镜像，不进行拉取
                if("openjdk:8-alpine".equals(repoTag)){
                    return;
                }
            }
        }
        //拉取镜像
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
            throw new RuntimeException(e);
        }
        System.out.println("下载完成");
    }

    /**
     * 创建并启动容器
     * @param CodePath
     * 传入的参数是编译文件存在的父目录
     */
    public String addContainerPool(String CodePath){
        //创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        //自定义根目录
        //CodePath: /home/kavansp/CodeSandBox/src/main/resources/ExecuteCode
        hostConfig.setBinds(new Bind(CodePath, new Volume("/app")));
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
        //启动容器
        dockerClient.startContainerCmd(containerId).exec();
        return containerId;
    }

    /**
     * 创建一个执行命令
     * @param containerId
     * @param cmdArray
     * @return
     */
    public ExecCreateCmdResponse createContainer(String containerId,String[] cmdArray){
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withCmd(cmdArray)
                .exec();
        return execCreateCmdResponse;
    }

    /**
     * 容器工厂，传入任务后选择性创建容器
     */
    public String ContainerFactory(){
        // 根据任务数量自动分配容器Id
        List<Container> containerList = DockerUtils.getContainer(dockerClient);
        List<String> runContainerIdList = DockerUtils.getRunContainer(containerList);
        List<String> exitContainerIdList = DockerUtils.getExitContainer(containerList);

        int runContainerIdListSize = runContainerIdList.size();
        //1.获取启动的容器,根据容器的运行CPU与内存来判断是否要使用该容器或者新建容器
        Map<String, ContainerStatus> containerStatusMap = DockerUtils.getContainerStatusMap();
        Set<String> containerStatusMapKey = containerStatusMap.keySet();
        for (String containerId : containerStatusMapKey) {
            ContainerStatus containerStatus = containerStatusMap.get(containerId);
            if(containerStatus.getMemory()<70.0 && containerStatus.getCpu()<60.0){
                return containerId;
            }
        }
        //2.全部正在运行状态的容器都不满足执行要求，启动未启动的容器
        if(CollectionUtil.isNotEmpty(exitContainerIdList)){
            //启动容器
            String exitContainerId = exitContainerIdList.get(0);
            dockerClient.startContainerCmd(exitContainerId).exec();
            return exitContainerId;
        }
        //如果小于最大容器数就执行创建容器
        if(runContainerIdListSize < maxContainerNumber){
            //最大容器数未满,直接创建容器
            String containerId = addContainerPool("/home/kavansp/CodeSandBox/src/main/resources/ExecuteCode");
            return containerId;
        }else{
            System.out.println("容器数达到最大容器数，请稍后再试");
            throw new RuntimeException();
        }
    }
    

    /**
     * 自动创建容器并创建执行任务
     * @param inputString
     * @param codePath 传入的是编译代码的父目录名称(类似05fc4aa6-7eca-48f2-8899-60cf185451f8)
     * @return
     */
    public CmdResponse getExecCreateCmdResponse(String inputString, String codePath) {
        //创建执行命令
        String[] input = inputString.split(" ");
        String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app" + File.separator + codePath, "Main"}, input);
        //工厂判断创建容器
        String containerId = ContainerFactory();
        //代表最大容器数已满，任务进入等待状态
        if(ObjectUtil.isEmpty(containerId)){
            //循环执行获取容器Id，等待阻塞队列寻找到一个能执行的容器
            while(ObjectUtil.isEmpty(containerId)){
                containerId = ContainerFactory();
            }
        }
        //获得交互式执行命令
        ExecCreateCmdResponse execCreateCmdResponse = createContainer(containerId, cmdArray);
        CmdResponse cmdResponse = new CmdResponse();
        cmdResponse.setContainerId(containerId);
        cmdResponse.setExecCreateCmdResponse(execCreateCmdResponse);
        return cmdResponse;
    }
}
