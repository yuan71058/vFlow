<p align="center">
  <img src="docs/readme_app_icon.svg" width="112" alt="vFlow App Icon" />
</p>

<h1 align="center">vFlow</h1>

<p align="center">
  Android 可视化自动化工具，让点击、识别、判断与系统操作串成高效、易用的工作流
</p>

<p align="center">
  <a href="README_EN.md">English</a> · 简体中文
</p>

<p align="center">
  <a href="https://github.com/ChaoMixian/vFlow/releases/latest"><img src="https://img.shields.io/github/v/release/ChaoMixian/vFlow?display_name=tag&style=flat-square" alt="Latest Release" /></a>
  <a href="https://github.com/ChaoMixian/vFlow/stargazers"><img src="https://img.shields.io/github/stars/ChaoMixian/vFlow?style=flat-square" alt="GitHub Stars" /></a>
  <a href="https://github.com/ChaoMixian/vFlow/network/members"><img src="https://img.shields.io/github/forks/ChaoMixian/vFlow?style=flat-square" alt="GitHub Forks" /></a>
  <a href="https://github.com/ChaoMixian/vFlow/issues"><img src="https://img.shields.io/github/issues/ChaoMixian/vFlow?style=flat-square" alt="GitHub Issues" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/ChaoMixian/vFlow?style=flat-square" alt="License" /></a>
  <a href="https://android-arsenal.com/api?level=29"><img src="https://img.shields.io/badge/API-29%2B-brightgreen?style=flat-square" alt="Android API 29+" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.3.20-blue?style=flat-square&logo=kotlin" alt="Kotlin" /></a>
</p>

<p align="center">
  <a href="https://discord.gg/7AMqhjdUH6" target="_blank"><img src="https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join Discord" /></a>
  <a href="https://qm.qq.com/q/5OhnIUNHzO" target="_blank"><img src="https://img.shields.io/badge/QQ%20Group-0366CC?style=for-the-badge&logo=qq&logoColor=white" alt="Join QQ Group" /></a>
</p>

<p align="center">
  <a href="https://trendshift.io/repositories/17290" target="_blank"><img src="https://trendshift.io/api/badge/repositories/17290" alt="ChaoMixian%2FvFlow | Trendshift" style="width: 250px; height: 55px;" width="250" height="55" /></a>
</p>


## 📸 应用截图

<table align="center">
  <tr>
    <td><img src="docs/screenshots/home_zh.png" width="200"></td>
    <td><img src="docs/screenshots/editor_zh.png" width="200"></td>
    <td><img src="docs/screenshots/settings_zh.png" width="200"></td>
  </tr>
  <tr>
    <td align="center">首页</td>
    <td align="center">工作流编辑</td>
    <td align="center">设置</td>
  </tr>
</table>

## 🎁 vFlow 是什么

**vFlow 的核心设计理念** 是将复杂的自动化逻辑分解为一个个独立、可复用、易于理解的模块。无论是简单的“每日签到”，还是包含复杂条件判断和循环的“自动化测试流程”，vFlow 都旨在提供一个直观、灵活且强大的平台。

项目完全采用 Kotlin 编写，并遵循现代 Android 开发实践。其核心架构（模块注册表、动态 UI 生成器、类型安全的执行上下文）被精心设计，不仅保证了当前功能的稳定性，也为未来添加更多、更强大的自动化模块提供了无限可能。无论你是希望解放双手的普通用户，还是寻求灵感和实践的开发者，vFlow 都欢迎你的探索和贡献。


## 🚀 主要特性

- **可视化流程编辑器**: 通过拖拽和点击，像搭积木一样构建你的自动化流程。
- **高度模块化**: 每个功能（如点击、查找文本、判断）都是一个独立的模块，易于维护和扩展。
- **动态数据流**: 模块的输出可以作为后续模块的输入（“魔法变量”），实现复杂的逻辑联动。
- **强大的逻辑控制**: 支持“如果/否则”条件判断和“循环”等控制流，让你的工作流更智能。
- **动态参数编辑**: 编辑器 UI 会根据你选择的参数（例如，“如果”模块中变量的类型）动态变化，只显示相关的选项。
- **完善的权限管理**: 在执行前清晰地请求工作流所需的权限，并提供统一的管理入口。
- **现代 UI 设计**: 基于 Material 3 和动态取色，提供美观且个性化的用户界面。
- **导入与导出**: 轻松备份、恢复和分享你的工作流。

## 🛠️ 技术架构概览

vFlow 目前由两个主要部分组成：运行在 Android 应用进程内的工作流引擎，以及按需启动的 `vFlowCore.dex` 后端。前者负责模块注册、编辑、执行与触发器调度，后者负责一部分需要更高权限或独立进程隔离的系统能力。

1.  **模块 (Module)**

    - 所有动作、触发器和控制流节点都基于 `ActionModule` 接口。
    - 大多数模块继承 `BaseModule`；成对或成块的控制流模块通常继承 `BaseBlockModule`。
    - 模块除了执行逻辑外，还会声明 `ActionMetadata`、`InputDefinition`、`OutputDefinition`、权限需求、摘要文本，以及可选的 `ModuleUIProvider` 自定义编辑界面。
    - **示例**: `ClickModule`、`IfModule`、`TimeTriggerModule`。

2.  **模块注册表 (ModuleRegistry)**

    - `app/src/main/java/com/chaomixian/vflow/core/workflow/module/ModuleRegistry.kt` 负责集中注册内建模块。
    - 注册时会给 `BaseModule` 注入 `ApplicationContext`，并按分类向动作选择器、编辑器、执行器等组件暴露模块。
    - 当前内建模块覆盖触发器、交互、逻辑、数据、文件、网络、系统、Core、Shizuku、Snippet 和 UI 组件等类别。

3.  **工作流编辑器 (Workflow Editor)**

    - `WorkflowEditorActivity` 是核心 UI，负责展示和操作 `ActionStep` 列表。
    - `ActionStepAdapter` 将 `ActionStep` 数据渲染为用户可见的卡片列表。
    - `ActionEditorSheet` 会基于模块的 `InputDefinition`、动态输入定义和 `ModuleUIProvider` 组合生成编辑界面，不只是“静态表单”。
    - 编辑器还会处理魔法变量、参数可见性、错误处理策略、块结构编辑以及部分模块的专用配置入口。

4.  **工作流执行器 (WorkflowExecutor)**

    - `WorkflowExecutor` 基于协程执行工作流，维护运行实例、重入策略、日志、超时和停止控制。
    - 执行时会构造 `ExecutionContext`，统一携带变量、魔法变量、触发器数据、服务实例、步骤输出和临时工作目录。
    - 它不仅顺序执行步骤，也负责条件分支、循环、子工作流、失败策略和通知状态同步。

5.  **触发器与后台服务**

    - `TriggerService`、`VoiceTriggerService`、`TimeTriggerReceiver` 等组件负责监听系统事件并拉起工作流执行。
    - 这部分让工作流既可以手动运行，也可以由时间、通知、剪贴板、位置、语音等事件驱动。

6.  **vFlow Core**

    - `core/` 模块会构建出 `app/src/main/assets/vFlowCore.dex`，由应用侧通过 `CoreLauncher` / `VFlowCoreBridge` 启动和连接。
    - 当前 Core 是 Master-Worker 架构。Master 负责接收请求并路由到 Shell 或 Root Worker。
    - App 与 Core 之间通过本地 Socket 通信，支持 TCP 和 Unix Domain Socket 两种传输方式；部分高权限系统能力通过 Core 模块暴露给工作流。

<p align="center">
  <img src="docs/vFlow_Core_Architecture.png" alt="vFlow Core Architecture" width="780" />
</p>

## 📦 如何构建

1.  克隆仓库:
    ```bash
    git clone https://github.com/ChaoMixian/vflow.git
    ```
2.  使用 Android Studio 打开项目。
3.  等待 Gradle 同步完成。
4.  直接运行项目到你的设备或模拟器上。

## 🤝 如何贡献

我们非常欢迎各种形式的贡献！无论是提交 Issue、修复 Bug、添加新功能模块还是改进文档，都对项目意义重大。

1.  **Fork** 本仓库。
2.  创建你的功能分支 (`git checkout -b feature/AmazingFeature`)。
3.  提交你的改动 (`git commit -m 'Add some AmazingFeature'`)。
4.  推送到你的分支 (`git push origin feature/AmazingFeature`)。
5.  创建一个 **Pull Request**。

### 💻 开发一个新模块

开发一个模块通常就是几步固定动作：选好分类、写好输入输出和执行逻辑、注册到模块表，再补上需要的资源与验证。

1.  在 `app/src/main/java/com/chaomixian/vflow/core/workflow/module/` 下选择合适分类创建模块类，例如 `interaction/`、`logic/`、`triggers/`。
2.  普通模块通常继承 `BaseModule`；如果是 `If / Loop / UI 容器` 这类成块结构，优先参考 `BaseBlockModule` 和现有块模块实现。
3.  为模块定义稳定的 `id`、`metadata`、输入输出定义，以及 `execute()` 逻辑。新增枚举型参数时，请保存稳定常量值，不要直接保存本地化文案。
4.  如果参数界面比较特殊，可以提供 `uiProvider`；如果通用表单足够，编辑器会直接根据 `InputDefinition` 自动生成 UI。
5.  在 `app/src/main/java/com/chaomixian/vflow/core/workflow/module/ModuleRegistry.kt` 的 `initialize()` 中注册模块，让它出现在选择器和执行器里。
6.  如果模块需要权限、触发器联动、Core/Shizuku 能力、字符串资源或图标资源，还需要同步补齐对应实现，而不只是注册类本身。
7.  如果你替换了历史上已经发布过的参数值，记得在定义层添加兼容映射（如 `legacyValueMap`）；全新模块不要预先添加无依据的兼容逻辑。
8.  最后补上测试。涉及解析、执行、类型或兼容行为变化时，优先在 `app/src/test/java` 添加对应单元测试。

[开发指南](docs/CONTRIBUTION.md)

## 🌟来颗 Star

[![Star History Chart](https://api.star-history.com/svg?repos=ChaoMixian/vFlow&type=date&legend=top-left)](https://www.star-history.com/#ChaoMixian/vFlow&type=date&legend=top-left)

## 📄 许可证

本项目采用 [GNU General Public License v2.0 or later (GPL-2.0-or-later)](LICENSE) 许可证。

## 💰 关于赞助

vFlow 还在成长，相比成熟软件，功能存在较大差距。当前保持为爱发电，**不接受赞助**，感谢。

<details>
<summary>赞助名单（非通过 vFlow 项目，按时间顺序）</summary>

```text
鲨鱼辣椒      RMB 18.88    2026/01/29
罗密欧的沉默   RMB 26.66    2026/02/10
起飞          RMB 200.00   2026/04/08
起飞          RMB 500.00   2026/04/15
起飞          RMB 800.00   2026/05/02
起飞          RMB 500.00   2026/05/16
```

</details>

---

> [!WARNING]
> **免责声明**: 使用本软件即表示您已充分理解并接受所有条款。本软件涉及自动化操作、Root权限执行等高风险功能，可能导致设备损坏、数据丢失、账号封禁等风险。**所有风险由用户自行承担，开发者不承担任何责任。** 请在使用前仔细阅读 [完整免责声明](DISCLAIMER.md)。
