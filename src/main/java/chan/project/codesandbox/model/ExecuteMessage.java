package chan.project.codesandbox.model;

import lombok.Data;

import java.util.List;

/**
 * 编译返回信息类
 */
@Data
public class ExecuteMessage {
    /**
     * 错误码(不要使用int，int默认值是0，如果为空的话也会判定为正常退出)
     */
    private Integer errorValue;
    /**
     *正常返回信息
     */
    private String message;
    /**
     * 错误返回信息
     */
    private String errorMessage;
    /**
     * 执行时间
     */
    private Long time;
    /**
     * 执行内存
     */
    private Long memory;
}
