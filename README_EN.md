<p align="center">
  <img src="docs/readme_app_icon.svg" width="112" alt="vFlow App Icon" />
</p>

<h1 align="center">vFlow</h1>

<p align="center">
  Android visual automation that turns tapping, recognition, branching, and system actions into efficient, approachable workflows
</p>

<p align="center">
  English · <a href="README.md">简体中文</a>
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


## 📸 Screenshots

<table align="center">
  <tr>
    <td><img src="docs/screenshots/home_en.png" width="200"></td>
    <td><img src="docs/screenshots/home_editor.png" width="200"></td>
    <td><img src="docs/screenshots/settings_en.png" width="200"></td>
  </tr>
  <tr>
    <td align="center">Home</td>
    <td align="center">Workflow Editor</td>
    <td align="center">Settings</td>
  </tr>
</table>

## 🎁 What Is vFlow

**vFlow's core design philosophy** is to break complex automation logic into independent, reusable, and easy-to-understand modules. Whether you are building a simple daily check-in flow or an automation testing pipeline with conditions and loops, vFlow aims to provide an intuitive, flexible, and powerful platform.

The project is fully written in Kotlin and follows modern Android development practices. Its core architecture, including the module registry, dynamic UI generation, and type-safe execution context, is designed not only to keep current features stable, but also to make room for more capable modules in the future. Whether you want to automate repetitive tasks or explore new implementation ideas as a developer, vFlow welcomes your experimentation and contributions.


## 🚀 Key Features

- **Visual Workflow Editor**: Build automation flows through dragging and tapping, much like assembling building blocks.
- **Highly Modular**: Every capability, such as tapping, text lookup, and branching, is packaged as an independent module for easier maintenance and extension.
- **Dynamic Data Flow**: Module outputs can become inputs for later steps through "magic variables", enabling workflows with real data flow.
- **Powerful Logic Control**: Supports control-flow structures such as `if/else` and loops, allowing workflows to express more advanced behavior.
- **Dynamic Parameter Editing**: The editor adapts to the parameters you choose and only shows the options relevant to the current context.
- **Unified Permission Management**: Required permissions are requested before execution and managed through a single consistent entry point.
- **Modern UI Design**: Built with Material 3 and dynamic color support for a polished and personalized experience.
- **Import and Export**: Easily back up, restore, and share your workflows.

## 🛠️ Technical Architecture Overview

vFlow currently consists of two main parts: the workflow engine running inside the Android app process, and the on-demand `vFlowCore.dex` backend. The former handles module registration, editing, execution, and trigger orchestration, while the latter provides a set of capabilities that benefit from elevated privileges or process isolation.

1.  **Module**

    - All actions, triggers, and control-flow nodes are built on top of the `ActionModule` interface.
    - Most modules extend `BaseModule`; paired or block-structured control-flow modules typically extend `BaseBlockModule`.
    - In addition to execution logic, modules define `ActionMetadata`, `InputDefinition`, `OutputDefinition`, required permissions, summary text, and optionally a `ModuleUIProvider` for custom editing UI.
    - **Examples**: `ClickModule`, `IfModule`, `TimeTriggerModule`.

2.  **ModuleRegistry**

    - `app/src/main/java/com/chaomixian/vflow/core/workflow/module/ModuleRegistry.kt` centrally registers built-in modules.
    - During registration, it injects the `ApplicationContext` into `BaseModule` instances and exposes modules by category to the action picker, editor, executor, and related components.
    - Built-in modules currently span triggers, interaction, logic, data, file, network, system, Core, Shizuku, snippets, and UI components.

3.  **Workflow Editor**

    - `WorkflowEditorActivity` is the core UI responsible for displaying and editing the `ActionStep` list.
    - `ActionStepAdapter` renders `ActionStep` data into the visible cards users interact with.
    - `ActionEditorSheet` builds its editing UI from a combination of `InputDefinition`, dynamic input definitions, and `ModuleUIProvider`, rather than being a purely static form.
    - The editor also handles magic variables, parameter visibility, error-handling policies, block-structure editing, and dedicated configuration entry points for some modules.

4.  **Workflow Executor**

    - `WorkflowExecutor` runs workflows with coroutines and manages execution instances, reentry behavior, logs, timeouts, and stop control.
    - During execution it constructs an `ExecutionContext` that carries variables, magic variables, trigger payloads, service instances, step outputs, and a temporary working directory.
    - It does more than sequential step execution: it also coordinates branching, loops, sub-workflows, failure policies, and execution status notifications.

5.  **Triggers and Background Services**

    - Components such as `TriggerService`, `VoiceTriggerService`, and `TimeTriggerReceiver` listen for system events and start workflow execution.
    - This allows workflows to run both manually and in response to time, notifications, clipboard changes, location, voice, and other events.

6.  **vFlow Core**

    - The `core/` module builds `app/src/main/assets/vFlowCore.dex`, which is launched and connected to from the app side through `CoreLauncher` and `VFlowCoreBridge`.
    - The current Core uses a Master-Worker architecture. The Master receives requests and routes them to Shell or Root workers.
    - Communication between the app and Core uses local sockets, with support for both TCP and Unix Domain Socket transports; some higher-privilege system capabilities are exposed to workflows through Core modules.

<p align="center">
  <img src="docs/vFlow_Core_Architecture.png" alt="vFlow Core Architecture" width="780" />
</p>

## 📦 How to Build

1.  Clone the repository:
    ```bash
    git clone https://github.com/ChaoMixian/vflow.git
    ```
2.  Open the project in Android Studio.
3.  Wait for Gradle sync to finish.
4.  Run the project on your device or emulator.

## 🤝 How to Contribute

Contributions of all kinds are welcome. Whether you are opening issues, fixing bugs, adding new modules, or improving the documentation, every contribution helps move the project forward.

1.  **Fork** this repository.
2.  Create your feature branch (`git checkout -b feature/AmazingFeature`).
3.  Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4.  Push to your branch (`git push origin feature/AmazingFeature`).
5.  Open a **Pull Request**.

### 💻 Developing a New Module

Building a module usually comes down to a few familiar steps: choose the right category, define the inputs, outputs, and execution logic, register it in the module table, then add any resources and validation it needs.

1.  Create the module class under the appropriate category in `app/src/main/java/com/chaomixian/vflow/core/workflow/module/`, such as `interaction/`, `logic/`, or `triggers/`.
2.  Regular modules usually extend `BaseModule`. If you're building something block-structured like `If`, `Loop`, or a UI container, start by studying `BaseBlockModule` and the existing block modules.
3.  Define a stable `id`, `metadata`, input/output definitions, and the `execute()` logic. For enum-like parameters, persist stable internal constants rather than localized display text.
4.  If the parameter UI is unusual, provide a `uiProvider`; otherwise the editor can generate the form directly from `InputDefinition`.
5.  Register the module in the `initialize()` method of `app/src/main/java/com/chaomixian/vflow/core/workflow/module/ModuleRegistry.kt` so it becomes available to the picker and executor.
6.  If the module depends on permissions, trigger integration, Core/Shizuku capabilities, string resources, or icon assets, make sure those pieces are implemented too instead of only registering the class.
7.  If you are replacing parameter values that were already shipped before, add compatibility mappings in the definition layer, such as `legacyValueMap`. For brand-new modules, do not add speculative compatibility logic.
8.  Finish by adding tests. For changes involving parsing, execution, typing, or compatibility behavior, prefer focused unit tests under `app/src/test/java`.

[Development Guide](docs/CONTRIBUTION.md)

## 🌟 Star the Project

[![Star History Chart](https://api.star-history.com/svg?repos=ChaoMixian/vFlow&type=date&legend=top-left)](https://www.star-history.com/#ChaoMixian/vFlow&type=date&legend=top-left)

## 📄 License

This project is licensed under the [GNU General Public License v2.0 or later (GPL-2.0-or-later)](LICENSE).

## 💰 Sponsorship

vFlow is still growing and still has a noticeable gap compared with more mature software. The project is currently maintained as a passion project, and **we do not accept sponsorships**. Thank you.

<details>
<summary>Sponsor List (not through the vFlow project, in chronological order)</summary>

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
> **Disclaimer**: By using this software, you acknowledge that you have fully understood and accepted all terms. This software includes high-risk capabilities such as automated operations and Root-level execution, which may lead to device damage, data loss, account bans, and other consequences. **All risks are borne by the user. The developer assumes no liability.** Please read the [full disclaimer](DISCLAIMER.md) before use.
