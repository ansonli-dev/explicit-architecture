# 公共能力证据手册

## 固定 Capability Current Ref

先按共享 Baseline Ref Profile 解析 `capability.current_ref`，默认值为 `develop`。只解析本地可见的 selected ref full SHA。使用 `git show <full-sha>:<path>` 或基于该 SHA 的独立 worktree 读取证据；不要 checkout、merge、rebase、reset 或自动 fetch。

不读取 selected current ref 之外的 feature/test/release/tag/working tree 来补充 Current。用户提供的未来计划只能作为上下文，不能写入 Capability As-Is。

## Module Evidence

读取：

- build/module/project graph；
- module/artifact name 和有效版本；
- public/internal/test-support boundary；
- dependency 和 optional integration；
- configuration/resource/schema packaging。

不要将 private implementation package 误记为独立 capability。

## Contract and Guarantee Evidence

对每项 capability 追踪：

1. purpose、boundary、input/output 和 failure semantics；
2. public API/SPI/schema/configuration；
3. defaults、precedence 和 extension points；
4. guarantee、scope、effective point 和 non-guarantee；
5. state/schema/worker/recovery（存在时）；
6. 直接验证上述承诺的 tests；
7. reference docs 与 fixed current-ref 实现的一致性。

类名、示例代码或单个测试不能单独证明完整 contract。

## Compatibility and Consumer Evidence

只在 fixed current ref 中有明确依据时记录：

- supported runtime/framework/module version；
- source/binary/schema/configuration compatibility；
- deprecation、replacement 和 migration；
- consumer resolved dependency、配置或受治理 consumer list。

无法穷举消费者时记录发现方式和 confidence，不人工伪造完整清单。

## 退出门禁

- Baseline 使用 `{capability-current-ref}@full-sha` 并记录 Ref Profile；
- 未读取其他分支补充 Current；
- capability 不是 package/class 清单；
- guarantee 与 non-guarantee 均有证据；
- defaults、extension 和 limitations 已覆盖；
- 只为真实状态/worker 记录 lifecycle/operations；
- compatibility 声明不超过证据范围；
- Current 与 Target/roadmap 分离；
- 事实、evidence 和 confidence 可追踪。
