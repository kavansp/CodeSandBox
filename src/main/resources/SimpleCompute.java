//示例编译允许代码
//编译命令：javac 文件路径(src开头)
//运行命令：javac 编译后的文件地址 文件名称(不需要后缀) main函数中输入的参数
//编译的文件执行时会出现乱码，因为编码格式不同，使用命令可以直接更改文件的编码格式
//javac -encoding utf-8 文件路径

public class SimpleCompute {
    public static void main(String[] args) {
        int a = Integer.parseInt(args[0]);
        int b = Integer.parseInt(args[1]);
        System.out.println("结果:" + (a + b));
    }
}
