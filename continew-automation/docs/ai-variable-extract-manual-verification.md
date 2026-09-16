# AI 变量提取功能 - 人工验证指南

## 功能概述

AI 变量提取功能通过后端 API 提供服务，允许用户输入原始值和提取指令，由 AI 生成正则表达式提取规则。

**核心 API**: `POST /automation/ai/variable-extract-rule`

**适用场景**:
- 从订单号中提取时间戳部分
- 从 JSON 响应中提取特定字段值
- 从 HTML 中提取验证码
- 从接口返回值中提取 token

## 前提条件

### 1. 后端应用已启动

确认后端应用运行在 `http://localhost:8000`:

```bash
cd D:\King\sakura\sakura-admin\continew-webapi
java -jar target/app/bin/continew-admin.jar
```

启动成功标志：
```
Started ContiNewAdminApplication in xx.xxx seconds
```

### 2. AI 功能已启用

检查配置文件 `continew-webapi/src/main/resources/config/application.yml` 第 80-93 行：

```yaml
automation:
  ai:
    enabled: ${SAKURA_AI_ENABLED:false}  # 需要设为 true
    provider: ${SAKURA_AI_PROVIDER:openai}
    endpoint: ${SAKURA_AI_ENDPOINT:https://api.openai.com/v1/chat/completions}
    model: ${SAKURA_AI_MODEL:gpt-4o-mini}
    api-key: ${SAKURA_AI_API_KEY:}  # 需要配置有效的 API Key
    structured-output: ${SAKURA_AI_STRUCTURED_OUTPUT:true}
```

**方式一：修改 application.yml**
```yaml
automation:
  ai:
    enabled: true
    api-key: sk-your-actual-api-key-here
```

**方式二：使用环境变量（推荐）**
```bash
export SAKURA_AI_ENABLED=true
export SAKURA_AI_API_KEY=sk-your-actual-api-key-here
java -jar target/app/bin/continew-admin.jar
```

### 3. 用户已登录并具备权限

该接口要求以下权限之一：
- `automation:automationUiScene:create`
- `automation:automationUiScene:update`

## 验证方式

### 方式一：通过 Swagger UI（推荐）

#### 步骤 1: 访问 Swagger 文档

浏览器打开：`http://localhost:8000/doc.html`

#### 步骤 2: 登录认证

1. 点击页面右上角 **Authorize** 或 **认证** 按钮
2. 使用管理员账号登录（或具备场景创建/修改权限的账号）
3. 登录成功后，token 会自动添加到后续请求的 Authorization header

#### 步骤 3: 找到 API 接口

在左侧菜单中展开：
- **自动化管理 AI 变量提取规则 API**
- 点击 **POST /automation/ai/variable-extract-rule**

#### 步骤 4: 填写测试数据

点击 **Try it out** 按钮，在请求体中填写：

**测试场景 1: 从订单号提取时间戳**
```json
{
  "raw_value": "ORDER_20260913123045_ABC123",
  "instruction": "提取订单号中的时间戳部分（yyyyMMddHHmmss 格式）"
}
```

**测试场景 2: 从 JSON 提取字段**
```json
{
  "raw_value": "{\"code\":0,\"data\":{\"token\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9\",\"userId\":12345},\"msg\":\"success\"}",
  "instruction": "提取 data.token 字段的值"
}
```

**测试场景 3: 从 HTML 提取验证码**
```json
{
  "raw_value": "<div class=\"verify-code\">您的验证码是：<span>683921</span>，5分钟内有效</div>",
  "instruction": "提取 6 位数字验证码"
}
```

#### 步骤 5: 执行请求

点击 **Execute** 按钮

#### 步骤 6: 验证响应

**预期成功响应（HTTP 200）**:
```json
{
  "success": true,
  "code": 200,
  "msg": "操作成功",
  "data": {
    "mode": "regex",
    "pattern": "ORDER_(\\d{14})_",
    "group": 1,
    "reason": "该正则从订单号中提取 14 位连续数字的时间戳部分"
  }
}
```

**响应字段说明**:
- `mode`: 提取模式，当前固定为 "regex"
- `pattern`: AI 生成的正则表达式
- `group`: 捕获组索引，0 表示整体匹配，1+ 表示第 N 个捕获组
- `reason`: AI 生成该规则的原因说明

#### 步骤 7: 验证规则有效性

将返回的 `pattern` 和 `group` 应用到原始值上，检查是否能正确提取：

使用在线正则测试工具（如 regex101.com）或编写简单验证代码：

```java
String rawValue = "ORDER_20260913123045_ABC123";
String pattern = "ORDER_(\\d{14})_";
int group = 1;

Pattern p = Pattern.compile(pattern);
Matcher m = p.matcher(rawValue);
if (m.find()) {
    String extracted = m.group(group);
    System.out.println("提取结果: " + extracted);  // 应输出: 20260913123045
}
```

### 方式二：通过 curl 命令行

#### 步骤 1: 获取登录 token

```bash
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "Admin@123"
  }'
```

从响应中提取 token：
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    ...
  }
}
```

#### 步骤 2: 调用变量提取 API

```bash
TOKEN="your-token-from-step1"

curl -X POST http://localhost:8000/automation/ai/variable-extract-rule \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "raw_value": "ORDER_20260913123045_ABC123",
    "instruction": "提取订单号中的时间戳部分（yyyyMMddHHmmss 格式）"
  }'
```

#### 步骤 3: 检查响应

成功响应应包含 `"success": true` 和正则规则字段。

### 方式三：通过自动化测试脚本

使用项目提供的集成测试脚本：

```bash
cd D:\King\sakura\sakura-admin\continew-automation\src\test\scripts
bash test-ai-variable-extract.sh
```

该脚本包含 4 个测试场景，会自动：
1. 登录获取 token
2. 调用 AI 变量提取 API
3. 验证响应格式
4. 输出彩色结果统计

**预期输出**:
```
╔════════════════════════════════════════════════╗
║   Sakura AI 变量提取功能集成测试               ║
╚════════════════════════════════════════════════╝

[登录认证]
✓ 登录成功

[测试场景 1/4] 提取订单号时间戳
✓ 成功

[测试场景 2/4] 提取 JSON 字段
✓ 成功

[测试场景 3/4] 提取 HTML 验证码
✓ 成功

[测试场景 4/4] 不安全正则检测
✓ 成功（预期失败）

╔════════════════════════════════════════════════╗
║                测试结果统计                     ║
╠════════════════════════════════════════════════╣
║  总计: 4    通过: 4    失败: 0                 ║
╚════════════════════════════════════════════════╝
```

## 常见问题排查

### 1. AI 功能未启用

**错误响应**:
```json
{
  "success": false,
  "code": 500,
  "msg": "AI 功能未启用"
}
```

**解决方案**: 检查 `automation.ai.enabled` 配置，确保为 `true`

### 2. API Key 未配置或无效

**错误响应**:
```json
{
  "success": false,
  "code": 500,
  "msg": "AI 服务调用失败"
}
```

**日志输出**:
```
Unauthorized: Invalid API key
```

**解决方案**: 检查 `automation.ai.api-key` 配置，确保使用有效的 OpenAI API Key

### 3. 权限不足

**错误响应**:
```json
{
  "success": false,
  "code": 403,
  "msg": "权限不足"
}
```

**解决方案**: 
- 确认已登录
- 确认当前用户具备 `automation:automationUiScene:create` 或 `automation:automationUiScene:update` 权限
- 联系管理员分配权限

### 4. 请求参数验证失败

**错误响应**:
```json
{
  "success": false,
  "code": 400,
  "msg": "原始值不能为空"
}
```

**解决方案**: 检查请求体，确保 `raw_value` 和 `instruction` 都不为空且符合长度限制

### 5. AI 生成的正则不匹配

**现象**: API 返回成功，但用正则表达式匹配原始值失败

**解决方案**:
- 检查 AI 返回的 `reason` 字段，理解生成逻辑
- 修改 `instruction` 提供更明确的提取说明
- 检查 `group` 索引是否正确（0 表示整体，1+ 表示捕获组）

## 性能监控（可选）

如果配置了 Prometheus 监控，可以查看以下指标：

访问 `http://localhost:8000/actuator/prometheus`，搜索：

```
# AI 变量提取请求总数
sakura_ai_variable_extract_requests_total

# AI 变量提取错误总数
sakura_ai_variable_extract_errors_total

# AI 变量提取请求耗时（P95/P99）
sakura_ai_variable_extract_duration_seconds
```

## 验收标准

完成以下检查项即表示功能验收通过：

- [ ] 后端应用成功启动，无启动错误
- [ ] AI 配置正确（enabled=true, api-key 有效）
- [ ] 通过 Swagger UI 成功调用 API，返回 HTTP 200
- [ ] 测试场景 1（订单号时间戳）返回有效正则
- [ ] 测试场景 2（JSON 字段）返回有效正则
- [ ] 测试场景 3（HTML 验证码）返回有效正则
- [ ] 测试场景 4（不安全正则）正确拒绝并返回错误
- [ ] 生成的正则能正确提取预期值
- [ ] 无权限用户调用 API 返回 403
- [ ] 集成测试脚本全部通过（4/4）

## 相关文档

- Swagger API 文档: `http://localhost:8000/doc.html`
- 集成测试脚本: `continew-automation/src/test/scripts/test-ai-variable-extract.sh`
- Kubernetes 部署配置: `continew-automation/src/test/resources/k8s/sakura-admin-ai-deployment.yaml`
- Prometheus 监控规则: `continew-automation/src/test/resources/prometheus/sakura-ai-alerts.yml`
- 单元测试: `continew-automation/src/test/java/top/continew/admin/automation/service/impl/AutomationVariableExtractRuleServiceImplTest.java`

## 未来前端集成建议

当前功能仅提供后端 API，前端 UI 尚未集成。未来可在以下位置集成：

1. **场景编辑页面** (`src/views/automation/automationUiScene/components/AddOrEditForm.vue`)
   - 在步骤配置中添加"AI 智能提取"按钮
   - 用户输入原始值和提取指令
   - 调用 `/automation/ai/variable-extract-rule` API
   - 将返回的 `pattern` 和 `group` 自动填充到变量提取配置中

2. **API 调用封装** (`src/apis/automation/`)
   - 创建 `automationAi.ts` 模块
   - 封装 `generateVariableExtractRule(req)` 方法
   - 使用统一的请求拦截器处理认证和错误

3. **用户体验优化**
   - 提供常见场景模板（订单号、JSON、HTML、URL 参数等）
   - 实时预览正则匹配结果
   - 显示 AI 生成的 `reason` 帮助用户理解规则
   - 支持编辑和调整 AI 生成的正则表达式

---

**文档版本**: v1.0  
**最后更新**: 2026-09-13  
**作者**: Claude Opus 5
