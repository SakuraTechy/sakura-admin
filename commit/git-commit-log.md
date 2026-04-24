# Git Commit Log

## 2026-04-24

### fix(project/automation): 无关联配置修改不再误判失败

- 修复自动化管理环境同步返回语义：`updateProjectConfig`、`updateJenkinsConfig`、`updateNodeConfig`、`updateBrowserConfig` 在“无关联命中”场景返回成功，仅在真实异常时返回失败，避免修改无关联配置时报错。
- 同步修复项目管理环境同步返回语义：`updateVersionConfig`、`updateServerConfig`、`updateDataBaseConfig` 同步采用一致策略，防止后续调用方将“无关联”误判为失败。
- 统一了关联配置同步行为：有关联时自动同步，无关联时不阻塞主配置更新，保留异常场景的失败返回以便告警与排查。

