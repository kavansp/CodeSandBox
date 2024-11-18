package chan.project.codesandbox.utils;

import chan.project.codesandbox.model.containerPool.ContainerStatus;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.CpuStatsConfig;
import com.github.dockerjava.api.model.MemoryStatsConfig;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.InvocationBuilder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DockerUtils {

    /**
     * -- GETTER --
     *  获取运行列表与内存列表
     *
     * @return
     */
    @Getter
    private static Map<String,ContainerStatus> containerStatusMap = new HashMap<>();
    /**
     * 获取Docker所有容器的Id
     * @param dockerClient
     * @return
     */
    public static List<Container> getContainer(DockerClient dockerClient){
        List<Container> ContainersCmd = dockerClient.listContainersCmd().withShowAll(true).exec();
        return ContainersCmd;
    }
    /**
     * 获取docker所有正在运行的容器
     * @param dockerClient
     * @return
     */
    public static List<String> getRunContainer(DockerClient dockerClient){
        List<Container> containerList = getContainer(dockerClient);
        List<String> runContainerIdList = getRunContainer(containerList);
        return runContainerIdList;
    }

    public static List<String> getRunContainer(List<Container> containerList){
        List<String> runContainerIdList = new ArrayList<>();
        for (Container container : containerList) {
            if("running".equals(container.getState())){
                runContainerIdList.add(container.getId());
            }
        }
        return runContainerIdList;
    }

    /**
     * 获取docker所有未运行的容器
     * @param dockerClient
     * @return
     */
    public static List<String> getExitContainer(DockerClient dockerClient){
        List<Container> containerList = getContainer(dockerClient);
        List<String> runContainerIdList = getExitContainer(containerList);
        return runContainerIdList;
    }

    public static List<String> getExitContainer(List<Container> containerList){
        List<String> exitContainerList = new ArrayList<>();
        for (Container container : containerList) {
            if("exited".equals(container.getState())){
                exitContainerList.add(container.getId());
            }
        }
        return exitContainerList;
    }

    /**
     * 获取cpu与内存使用率(key与value)
     * @param dockerClient
     * @param containerId
     * @return
     */
    public static ContainerStatus doResource(DockerClient dockerClient, String containerId){
        ContainerStatus containerStatus = new ContainerStatus();
        InvocationBuilder.AsyncResultCallback<Statistics> callback = new InvocationBuilder.AsyncResultCallback<Statistics>(){
            @Override
            public void onNext(Statistics object) {
                CpuStatsConfig cpu = object.getCpuStats();
                CpuStatsConfig preCpu = object.getPreCpuStats();
                Long num1 = (cpu.getCpuUsage().getTotalUsage()-preCpu.getCpuUsage().getTotalUsage());
                Long num2 = cpu.getSystemCpuUsage()-preCpu.getSystemCpuUsage();
                float cpuu = Math.round(num1 * cpu.getCpuUsage().getPercpuUsage().size() * 100 / num2 );
                containerStatus.setCpu(cpuu);
                MemoryStatsConfig memoryStatsConfig = object.getMemoryStats();

                float mem = Math.round((float) (memoryStatsConfig.getUsage() * 100) / memoryStatsConfig.getLimit());
                containerStatus.setMemory(mem);
            }
        };
        dockerClient.statsCmd(containerId).withNoStream(true).exec(callback).awaitResult();
        containerStatusMap.put(containerId,containerStatus);
        return containerStatus;
    }
}
