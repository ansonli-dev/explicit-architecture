# 显式架构通用开发与测试指南

> 适用范围：采用 DDD、Hexagonal Architecture、Onion Architecture、Clean Architecture、CQRS 等显式架构风格的服务端项目。
>
> 本文是一份可迁移指南，技术基线指定为 **Java 17 + Spring Boot 3**。它不绑定特定业务领域、中间件或部署形态；具体项目可在此基础上补充自己的命名约定、技术选型和目录结构。
>
> 测试分层参考：[Toby Clemson / Martin Fowler, Testing Strategies in a Microservice Architecture](https://martinfowler.com/articles/microservice-testing/)。

---

## 一、如何开发

### 0. 技术基线

本文默认使用 Java 17 与 Spring Boot 3。代码示例采用 Java 17 语法和 Spring Boot 3 常见组件，但示例中的业务名都是占位概念，可替换为具体项目语言。

| 关注点 | 建议 |
|---|---|
| Java 版本 | Java 17 |
| 应用框架 | Spring Boot 3 |
| DTO / Command / Query / Result | 优先使用 Java `record` |
| 依赖注入 | 构造器注入；避免字段注入 |
| Handler | 可使用 `@Service` 作为 Spring Bean |
| Controller / Consumer / Adapter | 放在外层包，使用 Spring Web、Messaging、Persistence 等框架能力 |
| 事务 | 默认放在基础设施持久化 adapter；不要放在领域模型或领域服务 |
| 测试 | JUnit 5、AssertJ、Mockito；Spring slice test 和组件测试按需使用 |

Spring 注解使用原则：

| 可以出现的位置 | 示例 |
|---|---|
| `interfaces/` | `@RestController`、`@RequestMapping`、`@PostMapping` |
| `application/` | `@Service` 用于 handler 注册 |
| `infrastructure/` | `@Repository`、`@Component`、`@ConfigurationProperties`、`@Transactional` |
| `domain/` | 避免行为性框架注解；领域对象应能脱离 Spring 直接测试 |

### 1. 先判断需求类型

开发前先判断需求属于哪一类。需求类型决定代码应该落在哪一层，也决定后面要补哪类测试。

| 需求类型 | 典型问题 | 主要代码位置 |
|---|---|---|
| 领域行为 | 业务对象如何创建、变更、拒绝非法操作 | `domain/model/`、`domain/service/`、`domain/event/` |
| 写用例 | 一个外部请求或消息触发状态变更 | `application/command/`、`domain/ports/`、`infrastructure/`、`interfaces/` |
| 读用例 | 返回详情、列表、统计、搜索结果 | `application/query/`、`application/port/outbound/`、读侧 adapter、`interfaces/` |
| 外部依赖 | 调第三方系统、远程服务、缓存、搜索、文件、消息系统 | `application/port/outbound/` + `infrastructure/` |
| 跨边界事件 | 一个上下文发生的事实需要通知其他上下文 | `domain/event/`、事件映射 adapter、事件 schema、消息 consumer |
| 数据结构变化 | 新表、新字段、新索引、新投影 | 持久化 adapter、迁移脚本、读模型 |
| API 合同变化 | 新 endpoint、新请求/响应字段、字段语义变化 | `interfaces/`、请求/响应 DTO、契约测试 |

核心判断：

| 问题 | 放置位置 |
|---|---|
| 这是业务不变量吗？ | Domain |
| 这是用例编排吗？ | Application |
| 这是技术连接吗？ | Infrastructure |
| 这是外部协议入口吗？ | Interfaces |
| 这是跨系统合同吗？ | Schema / Contract |

### 2. 领域层：写业务语言，不写技术语言

领域层是最内圈。它表达业务概念、业务规则和状态变化，不表达数据库、HTTP、消息队列、缓存、框架生命周期等技术细节。

需要写的代码：

| 代码 | 位置 | 什么时候写 | 规则 |
|---|---|---|---|
| 聚合根 | `domain/model/` | 一个业务对象需要维护一致性边界 | 聚合方法承载状态变化和不变量 |
| 实体 | `domain/model/` | 聚合内部有身份和值变化 | 不暴露技术持久化模型 |
| 值对象 | `domain/model/` | 概念由值定义，如金额、数量、范围、地址 | 不可变，构造时校验自身不变量 |
| 领域异常 | `domain/model/` 或 `domain/exception/` | 业务规则被违反 | 用业务语言命名 |
| 领域事件 | `domain/event/` | 一个业务事实已经发生 | 只描述事实，不描述处理方式 |
| 写侧仓储端口 | `domain/ports/` | 命令侧需要保存或加载聚合 | 接口使用领域类型，不使用 ORM entity 或 API DTO |
| 领域服务 | `domain/service/` | 逻辑跨多个聚合或不自然属于某个聚合 | 纯逻辑；无 I/O；无 repository/client 注入 |

不要做：

| 不要 | 原因 |
|---|---|
| 在领域对象上使用改变运行时行为的框架注解 | 领域模型会被框架语义污染，难以脱离框架测试 |
| 在领域服务里访问数据库、远程服务、缓存或消息系统 | 领域服务应是纯函数，数据由应用层准备好后传入 |
| 在领域事件里引用跨系统传输格式 | 领域事件属于本地模型，集成事件属于边界合同 |
| 把业务判断留在应用层 handler | Handler 负责编排，业务规则应由聚合或领域服务决定 |

领域层代码的好坏，可以用一句话检查：不用启动框架、不连接任何外部资源，能否直接 new 出对象并验证业务行为？

领域聚合示例：

```java
package example.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReviewRequest {
    private final ReviewRequestId id;
    private ReviewStatus status;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private ReviewRequest(ReviewRequestId id, ReviewStatus status) {
        this.id = id;
        this.status = status;
    }

    public static ReviewRequest submit() {
        var request = new ReviewRequest(new ReviewRequestId(UUID.randomUUID()), ReviewStatus.SUBMITTED);
        request.registerEvent(new ReviewRequestSubmitted(request.id.value(), Instant.now()));
        return request;
    }

    public void approve(ReviewerId reviewerId) {
        if (status != ReviewStatus.SUBMITTED) {
            throw new ReviewStateException("Only submitted requests can be approved");
        }
        status = ReviewStatus.APPROVED;
        registerEvent(new ReviewRequestApproved(id.value(), reviewerId.value(), Instant.now()));
    }

    public ReviewRequestId id() {
        return id;
    }

    public ReviewStatus status() {
        return status;
    }

    public List<DomainEvent> pullDomainEvents() {
        var events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }
}
```

值对象和领域事件示例：

```java
package example.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ReviewRequestId(UUID value) {
    public ReviewRequestId {
        if (value == null) {
            throw new IllegalArgumentException("id must not be null");
        }
    }
}

public record ReviewerId(UUID value) {}

public enum ReviewStatus {
    SUBMITTED, APPROVED, REJECTED
}

public interface DomainEvent {}

public record ReviewRequestApproved(
    UUID requestId,
    UUID reviewerId,
    Instant occurredAt
) implements DomainEvent {}
```

### 3. 应用层：写用例编排，不写业务规则

应用层回答“这个用例如何组织起来”。它把外部输入变成领域操作，把领域结果变成用例结果。

写用例通常包含：

| 代码 | 位置 | 说明 |
|---|---|---|
| Command | `application/command/{feature}/` | 表达一次状态变更请求 |
| Command Result | `application/command/{feature}/` | 表达命令完成后的用例结果 |
| Command Handler | `application/command/{feature}/` | 编排加载、领域调用、保存、返回 |

Command Handler 的标准流程：

1. 读取 command。
2. 通过端口加载需要的聚合、策略、外部信息或上下文。
3. 调用聚合方法或领域服务。
4. 通过写侧端口保存聚合。
5. 从内存中的领域状态组装 command result。
6. 触发必要的后续机制，但不直接写技术细节。

读用例通常包含：

| 代码 | 位置 | 说明 |
|---|---|---|
| Query | `application/query/{feature}/` | 表达一次读取请求 |
| Query Result | `application/query/{feature}/` | 表达读模型结果 |
| Query Handler | `application/query/{feature}/` | 编排读取、过滤、权限检查、fallback |
| 读侧端口 | `application/port/outbound/` | 抽象读模型、搜索、报表或投影读取 |

读路径不一定经过领域模型。对于 CQRS 或读优化场景，Query Handler 可以直接通过读侧端口返回扁平 DTO 或 read model result。业务不变量仍由写路径保证。

应用层可以依赖领域层和应用端口，但不应依赖具体基础设施。它可以知道“我要保存聚合”“我要读取投影”“我要调用某个外部能力”，但不应知道这些能力由什么数据库、协议、SDK 或中间件实现。

Command、Result、Handler 示例：

```java
package example.application.port.inbound;

public interface CommandBus {
    <R> R dispatch(Object command);
}
```

```java
package example.application.command.review;

import example.domain.model.ReviewRequestId;
import example.domain.model.ReviewerId;
import example.domain.ports.ReviewRequestRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

public record ApproveReviewRequestCommand(UUID requestId, UUID reviewerId) {}

public record ApproveReviewRequestResult(UUID requestId, String status) {}

@Service
public class ApproveReviewRequestHandler {
    private final ReviewRequestRepository repository;

    public ApproveReviewRequestHandler(ReviewRequestRepository repository) {
        this.repository = repository;
    }

    public ApproveReviewRequestResult handle(ApproveReviewRequestCommand command) {
        var request = repository.getById(new ReviewRequestId(command.requestId()));

        request.approve(new ReviewerId(command.reviewerId()));

        repository.save(request);

        return new ApproveReviewRequestResult(
            request.id().value(),
            request.status().name()
        );
    }
}
```

读用例示例：

```java
package example.application.query.review;

import example.application.port.outbound.ReviewRequestReadRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

public record GetReviewRequestQuery(UUID requestId) {}

public record ReviewRequestDetailResult(
    UUID requestId,
    String status,
    Instant submittedAt
) {}

@Service
public class GetReviewRequestHandler {
    private final ReviewRequestReadRepository readRepository;

    public GetReviewRequestHandler(ReviewRequestReadRepository readRepository) {
        this.readRepository = readRepository;
    }

    public ReviewRequestDetailResult handle(GetReviewRequestQuery query) {
        return readRepository.findDetailById(query.requestId())
            .orElseThrow(() -> new ReviewRequestNotFoundException(query.requestId()));
    }
}
```

### 4. 基础设施层：实现技术细节

基础设施层是 driven adapter。它实现领域层或应用层定义的端口，把抽象能力接到真实技术。

常见代码：

| 代码 | 位置 | 说明 |
|---|---|---|
| 持久化模型 | `infrastructure/persistence/` | 技术存储结构，不向内层泄漏 |
| 持久化仓储实现 | `infrastructure/persistence/` | 实现领域写侧仓储端口 |
| 数据映射器 | `infrastructure/persistence/` | 显式映射持久化模型与领域模型 |
| 读模型 adapter | `infrastructure/readmodel/` | 实现查询端口，返回 query result |
| 外部服务 client | `infrastructure/client/` | 实现应用层 client port，封装协议细节 |
| 缓存 adapter | `infrastructure/cache/` | 实现缓存端口，处理 key、TTL、序列化 |
| 消息发布 adapter | `infrastructure/messaging/` | 把本地事件或集成事件发布到外部消息系统 |
| 事件映射器 | `infrastructure/messaging/` | 领域事件映射为跨边界消息 |
| 配置绑定 | `infrastructure/config/` | 将环境配置转成类型安全配置对象 |
| 数据迁移 | `resources/migration/` 或项目约定位置 | 管理 schema 演进 |

事务边界属于基础设施和用例编排的交界。默认原则是：事务范围越小越好，外部 I/O 不应被包进数据库事务。如果一个用例必须原子更新多个聚合，需要把这个例外记录为架构决策。

基础设施层可以依赖框架、SDK、数据库驱动、消息库和序列化库；但这些依赖不能反向进入 Domain 或 Application。

写侧端口和持久化 adapter 示例：

```java
package example.domain.ports;

import example.domain.model.ReviewRequest;
import example.domain.model.ReviewRequestId;

public interface ReviewRequestRepository {
    ReviewRequest getById(ReviewRequestId id);

    void save(ReviewRequest request);
}
```

```java
package example.infrastructure.persistence;

import example.domain.model.ReviewRequest;
import example.domain.model.ReviewRequestId;
import example.domain.ports.ReviewRequestRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ReviewRequestPersistenceAdapter implements ReviewRequestRepository {
    private final ReviewRequestJpaRepository jpaRepository;
    private final ReviewRequestMapper mapper;

    public ReviewRequestPersistenceAdapter(
        ReviewRequestJpaRepository jpaRepository,
        ReviewRequestMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public ReviewRequest getById(ReviewRequestId id) {
        return jpaRepository.findById(id.value())
            .map(mapper::toDomain)
            .orElseThrow(() -> new ReviewRequestNotFoundException(id.value()));
    }

    @Override
    @Transactional
    public void save(ReviewRequest request) {
        var entity = mapper.toEntity(request);
        jpaRepository.save(entity);
    }
}
```

### 5. 接口层：写驱动入口，不写业务逻辑

接口层是 driving adapter。它把外部协议翻译成应用层 command/query。

常见入口：

| 入口类型 | 位置 | 应写内容 |
|---|---|---|
| HTTP / REST / RPC Controller | `interfaces/http/` 或 `interfaces/rest/` | request -> command/query -> dispatch -> response |
| 消息 Consumer | `interfaces/messaging/consumer/` | message -> command -> dispatch |
| CLI / Job / Scheduler | `interfaces/job/` 或 `interfaces/cli/` | runtime input -> command/query -> dispatch |
| 同进程事件监听器 | `interfaces/event/` | local event -> command -> dispatch |
| 请求 DTO | `interfaces/*/request/` | 外部输入合同 |
| 响应 DTO | `interfaces/*/response/` | 外部输出合同 |

接口层可以做：

| 可以 | 说明 |
|---|---|
| 协议字段解析 | path、query、header、body、message headers |
| 基础输入校验 | 格式、必填、长度、类型 |
| 认证上下文提取 | 把身份信息转成应用层可理解的上下文 |
| DTO 映射 | 外部 request/response 与 command/query/result 映射 |
| 状态码或错误码映射 | 把应用异常转成协议响应 |

接口层不应该做：

| 不应该 | 原因 |
|---|---|
| 直接调用 repository 或 client adapter | 绕过应用层，破坏用例边界 |
| 注入具体 handler | 入口应依赖统一的应用入口或端口 |
| 包含业务 if/else | 业务规则应在领域层 |
| 拼接持久化查询 | 查询细节属于读侧端口和 adapter |

REST Controller 示例：

```java
package example.interfaces.rest;

import example.application.command.review.ApproveReviewRequestCommand;
import example.application.command.review.ApproveReviewRequestResult;
import example.application.port.inbound.CommandBus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/review-requests")
public class ReviewRequestCommandController {
    private final CommandBus commandBus;

    public ReviewRequestCommandController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PostMapping("/{requestId}/approval")
    public ResponseEntity<ApproveReviewRequestResponse> approve(
        @PathVariable UUID requestId,
        @Valid @RequestBody ApproveReviewRequestRequest body
    ) {
        ApproveReviewRequestResult result = commandBus.dispatch(
            new ApproveReviewRequestCommand(requestId, body.reviewerId())
        );
        return ResponseEntity.ok(new ApproveReviewRequestResponse(result.requestId(), result.status()));
    }
}

record ApproveReviewRequestRequest(@NotNull UUID reviewerId) {}

record ApproveReviewRequestResponse(UUID requestId, String status) {}
```

### 6. 跨边界事件开发顺序

当一个业务事实需要通知其他上下文或服务时，按这个顺序开发：

1. 在生产方领域层定义领域事件。
2. 在聚合行为中注册或返回领域事件。
3. 定义跨边界消息合同：schema、topic/event name、version、字段语义。
4. 在生产方基础设施层实现事件映射：domain event -> integration message。
5. 实现可靠发布机制：outbox、事务后发布、事件存储或项目选定方案。
6. 在消费方接口层实现 consumer：反序列化、幂等、错误处理、转 command。
7. 在消费方应用层实现 command handler。
8. 在消费方领域层表达真正的业务响应。

关键原则：

| 原则 | 说明 |
|---|---|
| 领域事件不等于集成事件 | 前者属于本地模型，后者属于跨边界合同 |
| Consumer 不写业务逻辑 | Consumer 翻译消息，业务由 command handler 和领域模型完成 |
| 消费必须幂等 | 消息系统通常至少一次投递 |
| 合同向后兼容 | 删除、重命名、改变字段语义都可能破坏消费者 |
| 跨边界状态变更避免同步原子性幻想 | 用事件、补偿、重试、对账处理最终一致性 |

### 7. 新功能通用代码清单

多数新增功能可以按这个顺序落地：

1. 领域模型：聚合方法、值对象、领域异常。
2. 领域事件：需要被响应的业务事实。
3. 写侧端口：保存或加载聚合的领域接口。
4. Command、Command Result、Command Handler。
5. Query、Query Result、Query Handler；没有读需求就不写。
6. 应用层 outbound port：外部能力、读模型、缓存、搜索、通知等。
7. 基础设施 adapter：持久化、client、cache、messaging、read model。
8. 接口 adapter：HTTP、消息、Job、CLI 等驱动入口。
9. 数据迁移：schema 或 projection 变化。
10. 契约或 schema：跨系统边界变化。

---

## 二、如何测试

### 1. 测试分层

Martin Fowler 网站上的微服务测试文章把测试分为 Unit、Integration、Component、Contract、End-to-End。放到显式架构里，可以这样理解：

| 测试类型 | 边界 | 验证内容 | 数量策略 |
|---|---|---|---|
| Unit | 一个类或一小组内存对象 | 领域规则、用例编排、DTO 映射、事件翻译 | 最多 |
| Integration | 一个 adapter + 一个真实外部组件或协议替身 | 技术边界是否接对 | 少量精选 |
| Component | 一个完整服务或组件，外部依赖替换为 stub/容器 | 单个服务是否满足业务需求 | 关键用例覆盖 |
| Contract | 服务或上下文边界 | 消费者期望与提供者合同是否兼容 | 每个跨边界交互 |
| End-to-End | 整个系统或关键业务链路 | 多组件协作是否达成用户目标 | 最少 |

目标不是测试越多越好，而是每类测试都指向一个清晰失败原因。

### 2. 按代码类型选择测试

| 代码类型 | 推荐测试类型 | 怎么测 |
|---|---|---|
| 聚合、实体、值对象 | Unit, sociable | 直接构造真实领域对象，验证状态变化、不变量和异常 |
| 领域服务 | Unit, sociable | 传入真实领域对象和值对象，不 mock |
| Command Handler | Unit, solitary | mock 或 fake 端口，验证编排、保存、返回 result |
| Query Handler | Unit, solitary | mock 或 fake 读侧端口，验证查询参数、fallback、空结果 |
| HTTP/RPC Controller | Unit 或 framework slice | mock 应用入口，验证协议合同和 DTO 映射 |
| Message Consumer | Unit + Integration | unit 测消息到 command；integration 测反序列化、ack、retry、幂等 |
| Job/Scheduler/CLI | Unit + Component | unit 测输入翻译；component 测真实调度入口或命令入口 |
| Persistence Adapter | Integration | 用真实数据库或等价测试容器验证 schema、映射、事务 |
| Read Model Adapter | Integration | 验证查询条件、投影字段、排序分页、fallback |
| External Client Adapter | Integration + Contract | 用协议 stub 验证请求响应；用契约测试保护跨系统期望 |
| Cache Adapter | Integration | 验证 key、TTL、序列化、miss/hit、失效 |
| Messaging Adapter | Integration | 验证 topic/event name、headers、payload、错误处理 |
| Event Mapper | Unit | 输入领域事件，断言跨边界消息字段 |
| 完整服务 | Component | 通过公开入口驱动服务，外部依赖使用 stub 或容器 |
| 多服务旅程 | End-to-End | 只覆盖最高价值业务路径和部署集成风险 |

### 3. 领域测试：最多、最快、零外部依赖

领域测试应该完全脱离框架、数据库、网络和消息系统。

应覆盖：

| 场景 | 断言 |
|---|---|
| 合法状态转换 | 状态、字段、派生值符合预期 |
| 非法状态转换 | 抛出领域异常，状态不被破坏 |
| 值对象不变量 | 构造非法值失败，合法值可比较 |
| 领域事件 | 关键行为产生正确事实 |
| 纯计算 | 金额、数量、评分、配额、策略等计算正确 |

领域测试不需要 mock。若领域测试需要 mock repository 或 client，通常说明 I/O 泄漏进了领域层。

领域测试示例：

```java
package example.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewRequestTest {
    @Test
    void submitted_request_can_be_approved() {
        var request = ReviewRequest.submit();

        request.approve(new ReviewerId(UUID.randomUUID()));

        assertThat(request.status()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(request.pullDomainEvents())
            .anyMatch(ReviewRequestApproved.class::isInstance);
    }

    @Test
    void approved_request_cannot_be_approved_again() {
        var request = ReviewRequest.submit();
        request.approve(new ReviewerId(UUID.randomUUID()));

        assertThatThrownBy(() -> request.approve(new ReviewerId(UUID.randomUUID())))
            .isInstanceOf(ReviewStateException.class);
    }
}
```

### 4. Handler 测试：验证编排，不重复领域测试

Handler 测试使用 test double 替代端口，目标是验证用例编排，而不是重复验证领域内部细节。

Command Handler 应覆盖：

| 场景 | 断言 |
|---|---|
| 主路径 | 加载正确依赖，调用领域行为，保存聚合，返回 command result |
| 聚合不存在 | 抛出应用层 not found 或业务定义的错误 |
| 外部依赖失败 | 异常被转换或传播为用例语义 |
| 领域拒绝操作 | 领域异常被保留或映射正确 |
| 结果组装 | 不额外查询读模型，不返回 query result 或外部 response DTO |

Query Handler 应覆盖：

| 场景 | 断言 |
|---|---|
| 主路径 | 调用正确读侧端口并返回 query result |
| 空结果 | 返回空集合、null object 或 not found，按项目约定 |
| fallback | 主读模型不可用时走备用路径，若项目支持 |
| 参数传递 | filter、sort、pagination、scope 不丢失 |
| 权限读取 | 查询前的访问控制或 scope 限制生效，若项目需要 |

Handler 测试示例：

```java
package example.application.command.review;

import example.domain.model.ReviewRequest;
import example.domain.model.ReviewRequestId;
import example.domain.ports.ReviewRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApproveReviewRequestHandlerTest {
    @Mock
    ReviewRequestRepository repository;

    @Test
    void approves_request_and_saves_aggregate() {
        var requestId = UUID.randomUUID();
        var reviewerId = UUID.randomUUID();
        var request = ReviewRequest.submit();

        when(repository.getById(new ReviewRequestId(requestId))).thenReturn(request);

        var handler = new ApproveReviewRequestHandler(repository);
        var result = handler.handle(new ApproveReviewRequestCommand(requestId, reviewerId));

        var saved = ArgumentCaptor.forClass(ReviewRequest.class);
        verify(repository).save(saved.capture());

        assertThat(saved.getValue().status()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(result.status()).isEqualTo("APPROVED");
    }
}
```

### 5. Adapter 集成测试：只测通信边界

Integration Test 的目标是验证“我们对外部组件或协议的假设是否正确”。它不应该膨胀成完整业务验收。

Persistence Adapter 应覆盖：

| 场景 | 断言 |
|---|---|
| 保存再读取 | 持久化模型与领域模型可往返 |
| schema 对齐 | 迁移脚本后的真实 schema 支持 adapter 操作 |
| 事务行为 | 成功提交、失败回滚、必要的锁或版本控制 |
| 查询约束 | 唯一键、外键、索引相关行为符合预期 |
| 事件桥接 | 如使用 outbox/domain event bridge，验证同事务写入 |

External Client Adapter 应覆盖：

| 场景 | 断言 |
|---|---|
| 正常响应 | method、path、headers、body、反序列化正确 |
| 4xx / 5xx | 转换为应用层可理解的错误 |
| timeout / retry | 超时、重试、熔断等策略符合项目约定 |
| malformed response | 协议异常不会伪装成业务成功 |

Messaging Adapter 应覆盖：

| 场景 | 断言 |
|---|---|
| publish | event name、headers、payload、partition/key 等符合合同 |
| consume | 反序列化、幂等、ack/nack、retry/dead-letter 行为正确 |
| duplicate | 重复消息不会导致重复业务效果 |
| poison message | 坏消息不会无限阻塞消费 |

Cache/Search/File 等 adapter 也按同一原则：验证边界和映射，不验证完整业务旅程。

持久化 adapter 集成测试示例：

```java
package example.infrastructure.persistence;

import example.domain.model.ReviewRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({ReviewRequestPersistenceAdapter.class, ReviewRequestMapper.class})
class ReviewRequestPersistenceAdapterTest {
    @Autowired
    ReviewRequestPersistenceAdapter adapter;

    @Test
    void saves_and_loads_aggregate() {
        var request = ReviewRequest.submit();

        adapter.save(request);

        var loaded = adapter.getById(request.id());

        assertThat(loaded.id()).isEqualTo(request.id());
        assertThat(loaded.status()).isEqualTo(request.status());
    }
}
```

### 6. 接口测试：薄入口也要保护合同

接口层虽然薄，但它承载外部合同，必须测试。

HTTP/RPC Controller 应测：

| 应测 | 不应测 |
|---|---|
| route、method、status code | 领域规则 |
| request DTO -> command/query | 数据库是否保存成功 |
| result -> response DTO | 远程服务是否真的可用 |
| validation error | handler 内部复杂分支 |
| exception -> error response | 技术 adapter 细节 |

Message Consumer 应测：

| 应测 | 不应测 |
|---|---|
| message -> command | 领域内部状态机 |
| event id / correlation id 传递 | 其他服务是否真实发布 |
| 重复消息处理 | 全链路业务完成 |
| 失败进入 retry 或 dead-letter | provider 的内部实现 |

Controller 测试示例：

```java
package example.interfaces.rest;

import example.application.port.inbound.CommandBus;
import example.application.command.review.ApproveReviewRequestResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewRequestCommandController.class)
class ReviewRequestCommandControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockBean
    CommandBus commandBus;

    @Test
    void maps_http_request_to_command_and_response() throws Exception {
        var requestId = UUID.randomUUID();
        var reviewerId = UUID.randomUUID();

        when(commandBus.dispatch(any()))
            .thenReturn(new ApproveReviewRequestResult(requestId, "APPROVED"));

        mockMvc.perform(post("/api/review-requests/{requestId}/approval", requestId)
                .contentType("application/json")
                .content("""
                    {"reviewerId":"%s"}
                    """.formatted(reviewerId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value(requestId.toString()))
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }
}
```

### 7. 组件测试：把单个服务当作黑盒

组件测试验证单个服务或组件作为整体是否满足业务需求。它通常通过公开入口驱动系统，并把外部依赖替换成 stub、fake、container 或测试环境服务。

组件测试适合覆盖：

| 场景 | 说明 |
|---|---|
| 关键写用例 happy path | 从外部入口到持久化结果 |
| 关键读用例 | 从外部入口到读模型结果 |
| 重要错误路径 | 输入非法、资源不存在、业务拒绝 |
| 异步副作用 | 事件产生、任务入队、状态最终更新 |
| 配置装配 | handler、adapter、transaction、serialization 等能协同工作 |

组件测试不应覆盖每个业务分支。业务分支主要由领域测试和 handler 测试覆盖。

### 8. 契约测试：保护跨边界协作

契约测试解决的问题是：消费者依赖的字段、状态码、消息字段或语义，提供者是否仍然满足。

适合契约测试的边界：

| 边界 | 契约内容 |
|---|---|
| HTTP/RPC API | path、method、request、response、status code、错误格式 |
| 消息事件 | event name、version、headers、payload schema、字段语义 |
| 文件/批处理接口 | 文件名规则、列、类型、编码、错误文件 |
| 公共 SDK | 方法签名、返回类型、错误模型 |

API 或事件变化时，先问：

1. 是否有消费者依赖这个字段或语义？
2. 能否新增可选字段，而不是删除或重命名？
3. 是否需要 parallel change：先添加新合同，迁移消费者，再移除旧合同？
4. 是否需要新版本，而不是原地破坏兼容性？

契约测试不验证完整业务流程；它验证边界处的相互期望。

### 9. E2E 测试：少而关键

E2E 测试验证多个组件协作后是否达成用户目标。它失败原因多、运行慢、维护成本高，所以应该少而精。

适合 E2E 的场景：

| 场景 | 为什么需要 E2E |
|---|---|
| 核心用户旅程 | 多个组件必须协作才有业务价值 |
| 部署/路由/认证集成 | 低层测试无法覆盖真实环境组合 |
| 异步最终一致链路 | 需要确认事件、任务、读模型最终收敛 |
| 大规模架构调整后回归 | 服务拆分、合并、通信方式变化 |

E2E 规则：

| 规则 | 说明 |
|---|---|
| 数量少 | 新功能优先补 unit、integration、component、contract |
| 数据独立 | 每个测试创建自己的数据，不依赖共享状态 |
| 等待最终一致 | 用 bounded polling，不用固定 sleep |
| 走公共接口 | 不直接改数据库来推进流程，除非是测试环境准备步骤 |
| 失败可诊断 | 断言说明哪个组件、哪个状态没有达到 |

### 10. 每类变更的测试清单

| 变更类型 | 必测 | 通常不需要 |
|---|---|---|
| 新领域规则 | Domain unit；相关 handler unit | E2E |
| 新写用例 | Domain unit、CommandHandler unit、接口测试、组件测试 | 契约测试，除非外部 API 变化 |
| 新读用例 | QueryHandler unit、接口测试、读侧 adapter integration | Domain unit，除非新增业务规则 |
| 新持久化结构 | Persistence adapter integration、迁移验证、相关组件测试 | E2E，除非影响核心链路 |
| 新外部 client | Client integration、handler unit、契约测试 | 多条 E2E |
| 新消息事件 | Event mapper unit、schema compatibility、consumer unit、组件测试 | HTTP contract |
| 新缓存/搜索/文件行为 | Adapter integration、handler/query unit、组件测试 | Domain unit |
| 新跨组件业务旅程 | 低层测试 + 少量 E2E happy path | 大量 E2E 分支 |

### 11. 完成定义

一个功能完成时，应满足：

1. 业务规则在领域层表达，并有领域测试。
2. Handler 只编排，不含业务判断，并有 handler 测试。
3. 技术 adapter 有集成测试覆盖协议、映射和错误路径。
4. 接口入口有测试覆盖外部合同到 command/query 的映射。
5. 跨边界交互有契约或 schema compatibility 保障。
6. 关键业务路径有组件测试；只有系统级价值才加 E2E。
7. 每个测试失败都能指向清晰边界：领域规则错、用例编排错、adapter 错、接口合同错、跨边界合同错，还是系统协作错。

这就是分层测试的目的：不是追求测试数量，而是让反馈快速、稳定、可定位。
