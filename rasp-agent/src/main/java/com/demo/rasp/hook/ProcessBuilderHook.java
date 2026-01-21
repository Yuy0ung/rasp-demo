package com.demo.rasp.hook;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.ByteArrayClassPath;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class ProcessBuilderHook implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        
        if ("java/lang/ProcessBuilder".equals(className)) {
            System.out.println("[RASP DEBUG] Transforming java.lang.ProcessBuilder...");
            try {
                System.out.println("[RASP DEBUG] Entering try block...");
                ClassPool cp = ClassPool.getDefault();
                // 使用 ByteArrayClassPath 避免类加载循环
                System.out.println("[RASP DEBUG] Appending ByteArrayClassPath...");
                cp.insertClassPath(new ByteArrayClassPath("java.lang.ProcessBuilder", classfileBuffer));
                
                System.out.println("[RASP DEBUG] Getting CtClass for ProcessBuilder...");
                CtClass cc = cp.get("java.lang.ProcessBuilder");
                System.out.println("[RASP DEBUG] Getting start method...");
                CtMethod m = cc.getDeclaredMethod("start");
                
                System.out.println("[RASP DEBUG] Inserting before code...");
                // 插入检查逻辑：调用 RaspProtector.checkRce
                // 修改：拼接所有参数
                m.insertBefore(
                    "{" +
                    "  java.util.List list = this.command;" +
                    "  if (list != null && list.size() > 0) {" +
                    "      StringBuilder sb = new StringBuilder();" +
                    "      for (int i = 0; i < list.size(); i++) {" +
                    "          sb.append((String)list.get(i)).append(\" \");" +
                    "      }" +
                    "      String fullCmd = sb.toString().trim();" +
                    "      com.demo.rasp.protection.RaspProtector.checkRce(fullCmd);" +
                    "  }" +
                    "}"
                );
                System.out.println("[RASP DEBUG] ProcessBuilder hooked successfully!");
                byte[] bytecode = cc.toBytecode();
                cc.detach();
                return bytecode;
            } catch (Throwable e) {
                System.err.println("[RASP ERROR] Failed to hook ProcessBuilder: ");
                e.printStackTrace();
            }
        }
        return null;
    }
}
