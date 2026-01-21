package com.demo.rasp;

import com.demo.rasp.hook.ProcessBuilderHook;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;
import java.io.File;

public class RaspAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("========================================");
        System.out.println("[+] RASP Agent Starting...");
        System.out.println("========================================");
        
        try {
            // 将 Agent Jar 添加到 Bootstrap ClassLoader 搜索路径
            // 这样被 Bootstrap 加载的类 (如 ProcessBuilder) 才能访问 Agent 中的类 (如 RaspProtector)
            File agentJarFile = new File(
                RaspAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            );
            inst.appendToBootstrapClassLoaderSearch(new JarFile(agentJarFile));
            
            // 注册 RCE 拦截器 (支持重转换)
            inst.addTransformer(new ProcessBuilderHook(), true);
            
            // 尝试对已加载的核心类进行重转换
            inst.retransformClasses(java.lang.ProcessBuilder.class);
            
            System.out.println("[+] Agent installed successfully.");
        } catch (Exception e) {
            System.err.println("[-] Failed to install agent: ");
            e.printStackTrace();
        }
    }
}
