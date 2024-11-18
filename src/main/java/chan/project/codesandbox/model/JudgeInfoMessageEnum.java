package chan.project.codesandbox.model;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum JudgeInfoMessageEnum {

    SUCCESS("成功", "success"),
    WRONG_ANSWER("答案错误", "wrong answer"),
    COMPILE_ERROR("编译错误", "compile error"),
    MEMORY_LIMIT_EXCEEDED("内存溢出", "memory limit exceeded"),
    TIME_LIMIT_EXCEEDED("运行超时", "time limit exceeded"),
    PRESENTATION_ERROR("格式错误", "presentation error"),
    OUTPUT_LIMIT_EXCEEDED("输出超限", "output limit exceeded"),
    WAITING("等待", "waiting"),
    DANGEROUS_OPERATION("危险操作", "dangerous operation"),
    RUNTIME_ERROR("运行时错误", "runtime error"),
    SYSTEM_ERROR("系统错误", "system error");

    private final String text;

    private final String value;

    JudgeInfoMessageEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 获取值列表
     *
     * @return
     */
    public static List<String> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value
     * @return
     */
    public static JudgeInfoMessageEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (JudgeInfoMessageEnum anEnum : JudgeInfoMessageEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    public String getValue() {
        return value;
    }
    public String getText() {return text;}
}
