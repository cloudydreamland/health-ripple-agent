# 涟漪守护 · MCP Server

把「健康事件涟漪守护」能力暴露为 **MCP（Model Context Protocol）标准工具**——
任何支持 MCP 的 AI（Claude、DuMate、自研 Agent）都可以直接调用，不必理解 REST 细节。

## 工具清单

| 工具 | 说明 | 读写 |
|---|---|---|
| `ripple_weather` | 今日健康气象（晴/多云/大雨/暴雨 + 守护指数 + 家属须知） | 读 |
| `ripple_forecast` | 72小时涟漪预报（可选 `adherence` 依从性沙盘） | 读 |
| `ripple_derive` | 涟漪推演：五维影响 + RII + 反事实 + 护栏 + 印鉴链存证 | 动作 |
| `ripple_guard_queue` | 今日守护队列（升级就医 > 未缓解 > 今日到期） | 读 |
| `ripple_community_radar` | 社区涟漪雷达（跨患者群体信号 + 潮汐指数） | 读 |
| `evidence_verify` | 印鉴链全链校验（SHA-256 逐条重算） | 读 |

安全边界：服务器以**医生身份**对接网关；不开方、不下诊断、不改治疗方案。
凭据走环境变量（`DOCTOR_ACCOUNT` / `DOCTOR_PASSWORD` / `RIPPLE_BASE_URL`），生产环境必须覆盖默认值。

## 运行

```bash
# 冒烟测试（需后端网关在线）
node mcp/smoke-test.mjs

# 接入任意 MCP 客户端（stdio 传输）
node mcp/ripple-mcp-server.mjs
```

### Claude Desktop 配置示例（claude_desktop_config.json）

```json
{
  "mcpServers": {
    "health-ripple": {
      "command": "node",
      "args": ["/绝对路径/mcp/ripple-mcp-server.mjs"],
      "env": {
        "RIPPLE_BASE_URL": "http://127.0.0.1:18080",
        "DOCTOR_ACCOUNT": "doctor1",
        "DOCTOR_PASSWORD": "你的密码"
      }
    }
  }
}
```

## 它证明了什么

智能体的能力不应该锁死在自己的 REST API 里。涟漪推演、健康气象、社区雷达
一旦成为 MCP 标准工具，就意味着：**任何第三方 AI 助手都可以在对话中直接发起
"帮我看看患者1今天的守护态势"**——守护能力从"一个产品"升级为"一个可以被
整个 AI 生态调用的公共能力"。这是从 SaaS 到协议的一步。
