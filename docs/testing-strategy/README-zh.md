# 微服务测试策略：全景指南

> 本文档由 Toby Clemson 在 Martin Fowler 网站上的经典 Infodeck 整理而成。包含了全部 25 页 Slide 的原始截图与核心内容整理。

---

## 一、 绪论与议程 (Slide 1-2)

微服务架构带来了独立部署和扩展的便利，但也让测试变得复杂。网络分区要求我们重新审视单体应用的测试策略。

![Slide 1: Title](./images/slide_01_1773300966630.png)

![Slide 2: Agenda](./images/slide_02_1773300972889.png)

**核心要点：**
- 微服务允许跨团队并行开发。
- 测试必须适应"网络隔离"和"分布式协同"的新环境。

---

## 二、 什么是微服务与其内部解剖 (Slide 3-5)

微服务是协作服务的集合，通常通过 REST over HTTP 集成。了解其内部组件对制定测试方案至关重要。

![Slide 3: Anatomy](./images/slide_03_1773301034248.png)

![Slide 4: Module Breakdown](./images/slide_04_v2_1773301040439.png)

![Slide 5: Connections](./images/slide_05_final_1773301090097.png)

**模块构成：**
- **Resources**: 处理协议映射（如 HTTP 到 Domain）。
- **Service/Domain**: 承载核心业务逻辑。
- **Gateways**: 封装与外部系统的消息传递。
- **Repositories**: 与持久化层交互。

---

## 三、 单元测试 (Unit Testing) (Slide 6-9)

单元测试在类级别验证逻辑。在微服务中，我们需要在"社交型"和"孤立型"之间做出选择。

![Slide 6: Context](./images/slide_06_1773301189076.png)

![Slide 7: Intro](./images/slide_07_1773301206003.png)

![Slide 8: Styles](./images/slide_08_1773301212270.png)

![Slide 9: Limits](./images/slide_09_1773301269517.png)

**策略建议：**
- **社交型 (Sociable)**: 适用于复杂的领域逻辑，允许与真实类交互。
- **孤立型 (Solitary)**: 适用于协调逻辑，使用 Test Doubles 隔离依赖。
- **限制**: 单元测试无法保证跨模块交互的正确性，需要更高层级的测试。

---

## 四、 集成测试 (Integration Testing) (Slide 10-11)

验证通信路径和接口契约，特别是在与数据库和其他服务交互时。

![Slide 10: Definition](./images/slide_10_1773301283553.png)

![Slide 11: Feedback](./images/slide_11_1773301385103.png)

**核心提示：**
- 关注集成模块的行为。
- 由于依赖外部组件，这类测试可能较慢且不稳定（Flaky）。
- 建议仅编写少量精选的集成测试。

---

## 五、 组件测试 (Component Testing) (Slide 12-16)

将整个微服务作为一个独立的组件进行测试，通常在进程内或通过外部 Stub 实现。

![Slide 12: Need](./images/slide_12_1773301401016.png)

![Slide 13: Definition](./images/slide_13_1773301449981.png)

![Slide 14: In-process](./images/slide_14_1773301471020.png)

![Slide 15: Resources](./images/slide_15_1773301546751.png)

![Slide 16: Out-of-process](./images/slide_16_1773301741483.png)

**测试方式：**
- **进程内 (In-process)**: 使用内存数据库和内存 Stub，测试速度快。
- **进程外 (Out-of-process)**: 部署真实的工件，使用外部 Stub 服务（如 moco, mountebank），更接近真实环境。

---

## 六、 契约测试 (Contract Testing) (Slide 17-19)

确保服务提供者满足消费者的期望，而无需部署整个系统。

![Slide 17: Combination](./images/slide_17_1773301763279.png)

![Slide 18: Intro](./images/slide_18_1773301772636.png)

![Slide 19: Sum](./images/slide_19_1773301806553.png)

**核心理念：**
- 消费者定义对 Provider 的需求（Request/Response 结构）。
- Provider 在构建过程中持续验证这些契约，确保不会发布破坏性更改。

---

## 七、 端到端测试 (End-to-End Testing) (Slide 20-22)

通过公共接口（如 GUI）验证整个系统的业务流程。

![Slide 20: Definition](./images/slide_20_1773301882960.png)

![Slide 21: Boundary](./images/slide_21_1773302095963.png)

![Slide 22: Tips](./images/slide_22_final_1773302152612.png)

**黄金法则：**
- 尽量少写 E2E 测试。
- 关注关键用户路径（Persona）。
- 确保测试数据独立，避免相互干扰。

---

## 八、 总结与测试金字塔 (Slide 23-25)

微服务架构提供了更多测试选项，关键在于保持平衡。

![Slide 23: Options](./images/slide_23_1773302188592.png)

![Slide 24: Pyramid](./images/slide_24_final_1773302284085.png)

![Slide 25: Summary](./images/slide_25_1773302301081.png)

**最终建议：**
- 建立稳固的单元测试基础。
- 结合集成、组件和契约测试来管理服务间的复杂性。
- 用少量的 E2E 测试作为最后防线。
