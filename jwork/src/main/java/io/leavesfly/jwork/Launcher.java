package io.leavesfly.jwork;

/**
 * JavaFX 启动包装类
 * 用于解决 "缺少 JavaFX 运行时组件" 的错误。
 * 通过一个不继承 Application 的类来启动，可以绕过 Java 模块系统的某些启动检查。
 */
public class Launcher {
    public static void main(String[] args) {
        JWorkApplication.main(args);
    }
}
