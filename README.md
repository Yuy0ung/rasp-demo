# RASP (Runtime Application Self-Protection) Demo Project

这是一个基于 Java Agent 和 Javassist 技术实现的 RASP（应用运行时自我保护）演示项目。它能够在运行时动态注入安全逻辑到目标应用（Backend）中，对高危操作（如命令执行、JNDI 查询）进行实时监控和拦截。

项目包含三个核心部分：
1.  **RASP Agent**: 核心 Java Agent，负责字节码修改和拦截逻辑。
2.  **RASP Backend**: 一个故意包含命令注入漏洞的 Spring Boot 应用，用于演示防御效果。
3.  **RASP Frontend**: 基于 Vue 3 + Ant Design Vue 的监控大屏，提供防御开关控制、攻击模拟和实时审计日志展示。

---

## 效果预览

### 1.正常请求

正常的IP参数可以自动放行

![QQ_1768965780112](https://yuy0ung.oss-cn-chengdu.aliyuncs.com/QQ_1768965780112.png)

### 2.防御状态

在开启了右上角防御状态后，只要存在恶意的命令注入行为，则会触发阻断+告警机制：
![QQ_1768966105045](https://yuy0ung.oss-cn-chengdu.aliyuncs.com/QQ_1768966105045.png)

### 3.污点传输

在关闭防御机制之后，不会开启阻断，但命令注入后会显示污点流经的调用堆栈：
![QQ_1768966223185](https://yuy0ung.oss-cn-chengdu.aliyuncs.com/QQ_1768966223185.png)

## 核心功能

### 1. 命令执行 (RCE) 防御与攻击模拟

- **场景**: 模拟一个“网络连通性测试”功能。
  - 用户输入 IP 地址，后端将其拼接为 `ping -c 1 {IP}` 并执行。
  - 存在命令注入漏洞，例如输入 `127.0.0.1; whoami` 会导致额外命令执行。
- **原理**: Hook `java.lang.ProcessBuilder.start()` 方法。
- **RASP 防御逻辑**:
  - **白名单机制**: 只允许执行以 `ping` 开头的命令。
  - **特殊字符检测**: 如果命令中包含 `;` 或 `|` 等命令连接符，视为恶意攻击。
  - **全参数检测**: Agent 会获取完整的命令行参数（而不仅仅是可执行文件名）进行分析。
- **条件阻断与污点追踪**:
  - **防御开启时**: 识别到恶意命令（如包含 `;`），直接抛出异常阻断执行，并记录 **BLOCKED** 日志。
  - **防御关闭时 (监控模式)**: 允许恶意命令执行，但记录 **MONITORED** 日志，并**抓取当前线程的堆栈信息 (Taint Path)**，在前端展示攻击来源的代码路径（如 `NetToolController.executeCommand` -> `checkConnectivity`）。

### 2. 动态防御开关

- **功能**: 支持通过前端界面实时开启或关闭防御功能，无需重启应用。
- **实现**: 基于内存中的系统属性 (`System.getProperty`) 存储开关状态。

### 3. 全量审计日志与可视化

- **功能**: 记录所有的 Runtime 操作。
- **状态分类**:
  - `BLOCKED`: 恶意操作，且防御已开启（已阻断）。
  - `MONITORED`: 恶意操作，但防御已关闭（仅监控）。此时会附带**污点传播路径 (Stack Trace)**。
  - `PASSED`: 非恶意操作（如正常的 `ping`），正常放行但留存审计记录。

## 技术架构与实现原理

### 1. Java Agent & Instrumentation
利用 Java 提供的 `java.lang.instrument` 包，在应用启动时（`premain`）加载 Agent。Agent 具有对已加载类进行重定义（Redefine）和重转换（Retransform）的能力。

### 2. 字节码修改 (Javassist)
使用 **Javassist** 库在字节码层面修改 Java 类。相比 ASM，Javassist 提供了更高级的 API，可以直接编写 Java 代码片段并注入到目标方法中。

### 3. 关键技术点与难点解决

#### A. Bootstrap ClassLoader 注入
*   **问题**: `java.lang.ProcessBuilder` 是由 **Bootstrap ClassLoader** 加载的核心类，而我们的 `RaspProtector`（防御逻辑类）是由 **AppClassLoader** 加载的。默认情况下，核心类无法访问用户类路径下的类，会导致 `NoClassDefFoundError`。
*   **解决方案**:
    ```java
    // 在 Agent 启动时，将 Agent Jar 添加到 Bootstrap ClassLoader 的搜索路径中
    inst.appendToBootstrapClassLoaderSearch(new JarFile(agentJarFile));
    ```

#### B. 类加载循环依赖 (ClassCircularityError)
*   **问题**: 在转换 `ProcessBuilder` 时，如果使用默认的 `ClassPool` (基于 `Class.forName`)，可能会触发类的循环加载，导致 JVM 抛出 `ClassCircularityError`。
*   **解决方案**: 使用 `ByteArrayClassPath`，直接基于字节流创建 `CtClass`，避免触发类加载器加载。
    ```java
    cp.insertClassPath(new ByteArrayClassPath("java.lang.ProcessBuilder", classfileBuffer));
    ```

#### C. 类冻结问题 (Frozen Class)
*   **问题**: Javassist 的 `CtClass` 对象一旦被修改并转换为字节码后，会被标记为 "frozen"。如果多次尝试修改同一个 `CtClass` 而不重置，会报错。
*   **解决方案**: 在每次转换完成后，调用 `cc.detach()` 将其从 `ClassPool` 中移除，确保下次获取的是新鲜的对象。

#### D. 完整命令参数获取检测
*   **问题**: `ProcessBuilder.command` 是一个 `List<String>`。如果只检查 `list.get(0)`，只能获取到可执行文件名（如 `ping`），无法检测参数中的注入（如 `127.0.0.1; whoami`）。
*   **解决方案**: 在 Hook 逻辑中遍历整个 `command` 列表，拼接成完整的命令行字符串，再传给检测引擎。

---

## 项目结构

```
rasp-project/
├── rasp-agent/           # [Java] RASP Agent 核心代码
│   ├── src/main/java/com/demo/rasp/
│   │   ├── RaspAgent.java          # Agent 入口 (premain)
│   │   ├── hook/                   # Hook 逻辑实现
│   │   │   └── ProcessBuilderHook.java # RCE Hook (处理 ping 拼接逻辑)
│   │   └── protection/
│   │       └── RaspProtector.java      # 防御检测逻辑 (白名单、堆栈抓取)
│   └── pom.xml           # Maven 配置 (maven-shade-plugin 用于打包)
│
├── rasp-backend/         # [Java] 包含漏洞的 Spring Boot 后端
│   ├── src/main/java/com/demo/backend/controller/
│   │   ├── NetToolController.java  # 网络测试工具 (模拟命令注入漏洞)
│   │   ├── AlertController.java    # 告警日志接口
│   │   └── ConfigController.java   # 配置开关接口
│   └── pom.xml
│
└── rasp-frontend/        # [Vue3] 前端监控大屏
    ├── src/
    │   ├── App.vue                 # 主页面 (网络测试 UI、告警展示)
    │   └── main.js                 # Ant Design Vue 配置
    └── package.json
```

## 快速开始

### 1. 编译打包
在项目根目录下执行：
```bash
# 编译 Agent
cd rasp-agent
mvn clean package

# 编译 Backend
cd ../rasp-backend
mvn clean package
```

### 2. 启动应用
使用 Java Agent 模式启动 Backend：
```bash
cd ../rasp-backend
java -javaagent:../rasp-agent/target/rasp-agent-1.0-SNAPSHOT.jar -jar target/rasp-backend-1.0-SNAPSHOT.jar
```

### 3. 运行前端
```bash
cd ../rasp-frontend
npm install
npm run dev
```

### 4. 验证效果
1.  访问前端页面 (默认为 `http://localhost:5173`)。
2.  在 "网络连通性测试" 中输入 `127.0.0.1` -> 点击测试 -> 正常执行。
3.  输入攻击 Payload: `127.0.0.1; whoami` -> 点击测试 -> 显示 "已被 RASP 阻断"。
4.  关闭右上角 "RASP 防御状态" 开关。
5.  再次输入 `127.0.0.1; whoami` -> 点击测试 -> 执行成功（或显示 ping 错误）。
6.  查看下方审计日志，可以看到一条 **MONITORED** 记录，展开可见攻击者的代码调用堆栈 (Taint Path)。

