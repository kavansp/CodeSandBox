package chan.project.codesandbox.docker.containerPool.scheduel;

import chan.project.codesandbox.model.containerPool.ContainerStatus;
import chan.project.codesandbox.utils.DockerUtils;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DockerClientBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定时任务，更新容器的内存与cpu状态
 * 根据容器的内存与CPU删除多余容器
 */
@Component
public class ContainerStatusTask {

    @Scheduled(fixedRate = 2000) // 每2秒执行一次
    public void updateContainerStatus() {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        List<Container> ContainersCmd = dockerClient.listContainersCmd().exec();
        for (Container container : ContainersCmd) {
            String containerId = container.getId();
            //执行获取资源使用情况，会自动更新
            DockerUtils.doResource(dockerClient, containerId);
        }
    }

    /**
     * 每一分钟检测一次，当系统空余容器过多就会删除
     */
    @Scheduled(fixedRate = 60000)
    public void delDockerContainer(){
        System.out.println("执行删除任务");
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        Map<String, ContainerStatus> containerStatusMap = DockerUtils.getContainerStatusMap();
        int containerCount = containerStatusMap.size();
        Set<String> keySet = containerStatusMap.keySet();
        for (String containerId : keySet) {
            if(containerCount <= 10){
                return;
            }
            ContainerStatus containerStatus = containerStatusMap.get(containerId);
            Float cpu = containerStatus.getCpu();
            Float memory = containerStatus.getMemory();
            //cpu使用率不足1%且内存使用率不足15%
            if(cpu <= 1.0 && memory <= 15.0){
                containerCount--;
                dockerClient.stopContainerCmd(containerId).exec();
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            }
        }
    }
}