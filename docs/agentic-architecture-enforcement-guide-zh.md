# Plugin、Git Hooks 与自动化测试集协同指南

> 目标：把显式架构规范从“文档建议”升级为“生成前有指导、提交前有提醒、合并前有硬门禁”的工程体系。
>
> 技术基线：Java 17 + Spring Boot 3。本文不绑定具体业务领域或中间件。

---

## 一、整体分工

三类机制不要互相替代，而要形成递进关系：

| 机制 | 发生时机 | 作用 | 约束强度 | 典型问题 |
|---|---|---|---|---|
| Plugin / Skill | 需求澄清、SDD 计划、TDD、代码生成、review | 让 agent 知道该怎么写 | 软约束 | “应该生成什么结构？” |
| Git Hooks | 本地编辑后、commit 前、push 前 | 快速发现明显违规 | 中约束 | “这次改动有没有明显越界？” |
| 自动化测试集 | 本地 test、CI、合并前 | 可重复验证架构和行为 | 硬约束 | “这套代码是否真的符合规则？” |

推荐原则：

1. **Plugin 指导生成**：减少错误发生。
2. **Hooks 快速反馈**：减少错误进入提交。
3. **自动化测试集最终裁决**：保证任何人、任何 agent 的改动都必须过门禁。

---

## 二、推荐目录结构

可以把 agent 规范资产和工程校验资产分开放：

```text
plugins/
└── explicit-architecture-sdd/
    ├── .codex-plugin/
    │   └── plugin.json
    ├── skills/
    │   └── explicit-architecture-java-sdd/
    │       ├── SKILL.md
    │       └── references/
    │           ├── architecture-rules.md
    │           └── testing-rules.md
    ├── assets/
    │   └── templates/
    └── agents/
        └── explicit-architecture-reviewer.md

scripts/
└── architecture/
    ├── check-domain-purity.py
    ├── check-application-boundaries.py
    ├── check-interface-boundaries.py
    ├── check-command-query-separation.py
    ├── check-test-presence.py
    └── check-explicit-architecture.sh

.githooks/
├── pre-commit
├── pre-push
└── commit-msg

src/test/java/.../architecture/
├── DomainPurityTest.java
├── ApplicationBoundaryTest.java
├── InterfaceBoundaryTest.java
├── CommandQuerySeparationTest.java
├── TransactionPlacementTest.java
└── NamingConventionTest.java
```

如果是多服务仓库，每个服务保留自己的 `src/test/.../architecture/`，根目录 `scripts/architecture/` 放共享脚本。

---

## 三、Plugin 层：让 agent 生成正确代码

Plugin 不负责“拦截”，它负责让 Codex/Superpowers 在写计划、写测试、写代码、做 review 时加载正确规则。

### 1. Skill

Skill 应包含：

| 内容 | 位置 | 作用 |
|---|---|---|
| 触发条件 | `SKILL.md` frontmatter | Java/Spring、显式架构、SDD、TDD、review 时自动触发 |
| 必须工作流 | `SKILL.md` | 先读项目规范，再读 skill references，再生成代码 |
| 非协商规则 | `SKILL.md` | Domain purity、handler orchestration、DTO 分离等 |
| 架构规则 | `references/architecture-rules.md` | 分层、包结构、事件、事务、反模式 |
| 测试规则 | `references/testing-rules.md` | Unit/Integration/Component/Contract/E2E 映射 |

Skill 要解决的问题：

- SDD 计划是否按 Domain/Application/Infrastructure/Interfaces 拆任务。
- TDD 是否先写正确层级的测试。
- 生成代码是否遵守 package 和依赖方向。
- review 是否检查分层、事务、DTO、事件、测试缺口。

### 2. Templates

模板让 agent 从正确形状开始写代码。

建议模板：

```text
assets/templates/
├── command/
│   ├── Command.java
│   ├── CommandHandler.java
│   └── CommandResult.java
├── query/
│   ├── Query.java
│   ├── QueryHandler.java
│   └── QueryResult.java
├── domain/
│   ├── Aggregate.java
│   ├── ValueObject.java
│   └── DomainEvent.java
├── infrastructure/
│   ├── PersistenceAdapter.java
│   └── EntityMapper.java
├── interfaces/
│   └── RestController.java
└── tests/
    ├── DomainTest.java
    ├── HandlerTest.java
    ├── ControllerTest.java
    └── AdapterIntegrationTest.java
```

模板规则：

- 模板只提供形状，不包含具体业务。
- 生成计划时写明使用了哪些模板。
- 模板必须和自动化测试规则一致。

### 3. Reviewer Agent

Reviewer agent 用于 Superpowers review 阶段，专职审查架构。

职责：

| 检查项 | 说明 |
|---|---|
| Domain purity | domain 是否依赖外层或框架行为注解 |
| Application boundary | handler 是否只编排，不写业务规则 |
| Infrastructure isolation | persistence/client/cache/message 是否隔离在 adapter |
| Interfaces thinness | controller/consumer 是否只做协议翻译 |
| CQRS separation | command/query/result/response 是否混用 |
| Transaction placement | 事务是否包住外部 I/O，是否进入 domain |
| Test strategy | 改动是否有匹配的测试类型 |

建议输出：

```text
Findings
- [P1] ...
- [P2] ...

Missing Tests
- ...

Architecture Risks
- ...

Verdict
- PASS / BLOCKED
```

---

## 四、Git Hooks 层：本地快速反馈

Hooks 要快，避免误报高、耗时长、依赖环境重的检查。

### 1. pre-commit

适合做：

| 检查 | 建议行为 |
|---|---|
| Java 格式化或 lint | fail |
| forbidden imports | fail |
| forbidden annotations | fail |
| controller 直接引用 repository | fail |
| command handler 返回 `*Response` | fail |
| 修改生产代码但没有测试变更 | warn 或 fail，视团队习惯 |
| 命名规范 | warn 起步，稳定后 fail |

示例：

```bash
#!/usr/bin/env bash
set -euo pipefail

changed_java="$(git diff --cached --name-only --diff-filter=ACMR | grep '\.java$' || true)"

if [ -z "$changed_java" ]; then
  exit 0
fi

python3 scripts/architecture/check-domain-purity.py $changed_java
python3 scripts/architecture/check-application-boundaries.py $changed_java
python3 scripts/architecture/check-interface-boundaries.py $changed_java
python3 scripts/architecture/check-command-query-separation.py $changed_java
python3 scripts/architecture/check-test-presence.py $changed_java
```

### 2. pre-push

适合跑稍慢但仍可接受的测试：

```bash
#!/usr/bin/env bash
set -euo pipefail

./gradlew test --tests "*ArchitectureTest"
./gradlew test --tests "*Domain*Test"
./gradlew test --tests "*CommandHandlerTest"
./gradlew test --tests "*QueryHandlerTest"
```

多服务仓库可以只跑受影响服务，后续再做 affected-module 检测。

### 3. commit-msg

可选，用来保证提交信息可追踪：

| 规则 | 示例 |
|---|---|
| feature commit | `feat: approve review request` |
| architecture rule | `arch: add domain purity check` |
| test-only | `test: cover approval handler` |
| docs | `docs: add enforcement guide` |

不建议一开始把 commit-msg 做得太重。

---

## 五、自动化测试集：最终硬门禁

自动化测试集是规范的最终来源。Git hooks 可以跳过，Plugin 只是建议，CI 测试才是硬约束。

### 1. Architecture Tests

推荐用 ArchUnit 表达结构规则。

要覆盖：

| 测试类 | 规则 |
|---|---|
| `DomainPurityTest` | domain 不依赖 application/infrastructure/interfaces/framework behavior |
| `ApplicationBoundaryTest` | application 不依赖 infrastructure/interfaces/技术 SDK |
| `InterfaceBoundaryTest` | interfaces 不直接依赖 persistence adapter/repository |
| `CommandQuerySeparationTest` | command/query 包不互相依赖，command 不返回 response |
| `TransactionPlacementTest` | `@Transactional` 不出现在 domain，handler 例外需要白名单 |
| `NamingConventionTest` | Command、Handler、Result、Response、Adapter 命名 |

示例：

```java
package example.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class DomainPurityTest {
    private static final String BASE_PACKAGE = "example..";

    @Test
    void domain_should_not_depend_on_outer_layers_or_frameworks() {
        var classes = new ClassFileImporter().importPackages(BASE_PACKAGE);

        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..application..",
                "..infrastructure..",
                "..interfaces..",
                "org.springframework..",
                "jakarta.persistence.."
            )
            .check(classes);
    }
}
```

### 2. Unit Tests

必须大量覆盖：

| 层 | 测试 |
|---|---|
| Domain model | 无 Spring、无 Mockito、无外部资源 |
| Domain service | 传入内存领域对象 |
| Command handler | mock/fake ports |
| Query handler | mock/fake read ports |

### 3. Adapter Integration Tests

覆盖技术边界：

| Adapter | 测试重点 |
|---|---|
| Persistence | schema、mapping、transaction、constraint |
| External client | request/response/error/timeout |
| Messaging | payload、headers、idempotency、retry |
| Cache/search/file | key/index/path、serialization、miss/error |

### 4. Component Tests

通过公开入口驱动单个服务：

- 关键写用例 happy path。
- 关键读用例 happy path。
- 少量重要错误路径。
- 必要异步副作用。

### 5. Contract Tests

覆盖跨边界合同：

- HTTP/RPC API。
- Message schema。
- 文件/批处理接口。
- Public SDK。

### 6. E2E Tests

只覆盖核心多组件旅程：

- 数量少。
- 数据独立。
- 使用 bounded polling 等待最终一致。
- 失败信息能定位到服务或边界。

---

## 六、规则映射表

同一条规则最好同时有三种表达：Plugin 指导、Hook 快扫、测试硬门禁。

| 规则 | Plugin / Skill | Git Hook | 自动化测试 |
|---|---|---|---|
| Domain 不依赖外层 | architecture-rules | import scan | ArchUnit |
| Domain 无框架行为注解 | architecture-rules | annotation scan | ArchUnit |
| Handler 只编排 | architecture-rules + reviewer agent | 简单关键词/返回类型扫描 | handler unit + review |
| Controller 不调用 repository | architecture-rules | import scan | ArchUnit |
| Persistence entity 不泄漏 | architecture-rules | import scan | ArchUnit |
| Command 不返回 Response | architecture-rules | signature scan | ArchUnit/custom test |
| Command/query 不互相依赖 | architecture-rules | import scan | ArchUnit |
| `@Transactional` 不在 domain | architecture-rules | annotation scan | ArchUnit |
| 外部 I/O 不进事务 | architecture-rules + reviewer agent | 难以静态准确判断 | integration/review |
| 修改代码要有对应测试 | testing-rules | changed-file scan | CI test coverage/check |
| 跨边界 schema 兼容 | testing-rules | schema diff warning | contract/schema tests |

原则：能静态判断的放 hook 和 ArchUnit；需要语义判断的放 reviewer agent 和 code review；运行时行为放自动化测试。

---

## 七、落地顺序

建议分 6 步做，避免一次性上太重。

### Step 1：整理规则源

产出：

- Plugin skill references 中的 `architecture-rules.md`
- Plugin skill references 中的 `testing-rules.md`
- 本指南作为治理总览

完成标准：

- 每条核心规则能在文档中找到。
- SDD plan 可以引用这些规则生成任务。

### Step 2：补模板资产

产出：

- Command/Query/Domain/Adapter/Controller/Test 模板。

完成标准：

- 新功能可以从模板开始。
- 模板和架构规则一致。

### Step 3：加 Reviewer Agent

产出：

- `explicit-architecture-reviewer`

完成标准：

- Superpowers review 阶段能输出分层问题、缺失测试、架构风险。

### Step 4：写轻量 Hook Scripts

先实现：

1. `check-domain-purity.py`
2. `check-application-boundaries.py`
3. `check-interface-boundaries.py`
4. `check-command-query-separation.py`
5. `check-test-presence.py`

完成标准：

- 对 staged Java 文件运行。
- 1 秒级反馈。
- 误报少。

### Step 5：加 ArchUnit 架构测试

先实现：

1. `DomainPurityTest`
2. `ApplicationBoundaryTest`
3. `InterfaceBoundaryTest`
4. `CommandQuerySeparationTest`
5. `TransactionPlacementTest`

完成标准：

- 本地和 CI 都能跑。
- 规则失败信息能指导修复。

### Step 6：接入 CI

建议流水线：

```text
format/lint
architecture tests
unit tests
adapter integration tests
component tests
contract tests
e2e tests
```

完成标准：

- PR 必须通过 architecture + unit。
- integration/component/contract/e2e 按成本分层运行。
- 核心分支启用完整门禁。

---

## 八、推荐最小闭环

如果只做第一版，建议做这 4 件：

1. **Plugin skill**：指导 SDD/TDD 生成正确结构。
2. **Reviewer agent**：实现后检查架构和测试缺口。
3. **pre-commit hook**：扫 domain/application/interfaces 的明显 import 违规。
4. **ArchUnit architecture tests**：CI 中硬性阻断分层破坏。

这四件事形成闭环：

```text
Skill 指导生成
  ↓
Reviewer Agent 审查语义
  ↓
Git Hook 本地快拦截
  ↓
Architecture Tests 合并前硬门禁
```

后续再逐步加入模板、scaffold、contract/e2e 策略和更细的自动化检查。

