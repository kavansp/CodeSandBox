package chan.project.codesandbox;

import chan.project.codesandbox.utils.DockerUtils;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.SearchImagesCmd;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.InvocationBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Array;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        List<Container> ContainersCmd = dockerClient.listContainersCmd().withShowAll(true).exec();
        for (Container container : ContainersCmd) {
            String containerId = container.getId();
            Map<Float, Float> floatFloatMap = doResource(dockerClient, containerId);
            System.out.println("获取最后的"+floatFloatMap);
        }
    }
    public static Map<Float,Float> doResource(DockerClient dockerClient,String containerId){
        Map<Float,Float> useResource = new HashMap<>();
        InvocationBuilder.AsyncResultCallback<Statistics> callback = new InvocationBuilder.AsyncResultCallback<Statistics>(){
            @Override
            public void onNext(Statistics object) {
                CpuStatsConfig cpu = object.getCpuStats();
                CpuStatsConfig preCpu = object.getPreCpuStats();
                Long num1 = (cpu.getCpuUsage().getTotalUsage()-preCpu.getCpuUsage().getTotalUsage());
                Long num2 = cpu.getSystemCpuUsage()-preCpu.getSystemCpuUsage();
                float cpuu = Math.round(num1 * cpu.getCpuUsage().getPercpuUsage().size() * 100 / num2 );

                MemoryStatsConfig memoryStatsConfig = object.getMemoryStats();

                float mem = Math.round((float) (memoryStatsConfig.getUsage() * 100) / memoryStatsConfig.getLimit());
                useResource.put(cpuu,mem);
            }
        };
        dockerClient.statsCmd(containerId).withNoStream(true).exec(callback).awaitResult();
        return useResource;
    }
}