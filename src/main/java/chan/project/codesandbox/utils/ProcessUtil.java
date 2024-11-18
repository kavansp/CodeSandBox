package chan.project.codesandbox.utils;

import chan.project.codesandbox.model.ExecuteMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 进程工具类
 */
public class ProcessUtil {
    public static ExecuteMessage getProcess(Process process){
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            //编译执行获取错误码，waitFor()方法等待编译完成，完成后返回错误码
            int exeValue = process.waitFor();
            //正常退出
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder stringBuilder = new StringBuilder();
            executeMessage.setErrorValue(exeValue);
            if(exeValue == 0){
                //获得子进程的输出
                String read;
                while((read = bufferedReader.readLine()) != null){
                    stringBuilder.append(read);
                }
                executeMessage.setMessage(stringBuilder.toString());
            }else{
                //获得子进程的异常输出信息(捕获出现了哪种类型的异常信息)
                String read;
                while((read = bufferedReader.readLine()) != null){
                    stringBuilder.append(read);
                }
                executeMessage.setMessage(stringBuilder.toString());
                //输出捕获的异常信息(具体的异常信息)
                BufferedReader bufferedErrorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                stringBuilder = new StringBuilder();
                while((read = bufferedErrorReader.readLine()) != null){
                    stringBuilder.append(read);
                }
                executeMessage.setErrorMessage(stringBuilder.toString());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return executeMessage;
    }
}
