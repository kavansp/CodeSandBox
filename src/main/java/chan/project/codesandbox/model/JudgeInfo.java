package chan.project.codesandbox.model;

import lombok.Data;

/**
 * 代码执行结果信息
 */
@Data
public class JudgeInfo {
    /**
     * 执行信息
     */
    private String message;
    /**
     * 执行时间列表
     */
    private Long ExeTime;
    /**
     * 消耗内存
     */
    private Long ExeMemory;
}
