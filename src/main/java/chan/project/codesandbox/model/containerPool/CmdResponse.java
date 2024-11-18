package chan.project.codesandbox.model.containerPool;

import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import lombok.Data;

@Data

public class CmdResponse {
    private String containerId;
    private ExecCreateCmdResponse execCreateCmdResponse;
}
