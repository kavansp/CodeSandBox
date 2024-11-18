package chan.project.codesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;

import java.util.List;

public class dockerDemo {
    public static void main(String[] args) {
        //8.130.53.52
        //192.168.233.133
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        //拉取镜像
        String image = "nginx:latest";//镜像名称(在hubdocker.com上获取)
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


        //创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        CreateContainerResponse createContainerResponse = containerCmd
                .withCmd("echo", "Hello Docker")
                .exec();
        System.out.println(createContainerResponse);

        //查看容器
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
        List<Container> containerList = listContainersCmd.withShowAll(true).exec();
        for (Container container : containerList) {
            System.out.println(container);
        }

        //启动容器
//        dockerClient.startContainerCmd(containerId).exec();

        // 查看日志
        LogContainerResultCallback logContainerResultCallback = new LogContainerResultCallback() {
            @Override
            public void onNext(Frame item) {
                System.out.println(item.getStreamType());
                System.out.println("日志：" + new String(item.getPayload()));
                super.onNext(item);
            }
        };

        // 阻塞等待日志输出
//        dockerClient.logContainerCmd(containerId)
//                .withStdErr(true)
//                .withStdOut(true)
//                .exec(logContainerResultCallback)
//                .awaitCompletion();

        //删除容器
//        dockerClient.removeContainerCmd(containerId).withForce(true).exec();

        //删除镜像
        dockerClient.removeImageCmd(image).exec();


    }
}
