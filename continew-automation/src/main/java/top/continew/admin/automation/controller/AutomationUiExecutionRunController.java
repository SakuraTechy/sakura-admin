package top.continew.admin.automation.controller;

import java.util.Map;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.continew.admin.automation.model.req.AutomationUiExecutionRunCreateReq;
import top.continew.admin.automation.service.AutomationUiExecutionRunService;
import top.continew.admin.automation.service.AutomationPlaywrightCaseService;
import top.continew.admin.automation.service.AutomationPlaywrightRunnerJobService;
import top.continew.starter.web.model.R;

/** 整次调试任务的生命周期；所有写操作需要执行权限及任务归属校验。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/automation/execution-runs")
@SaCheckPermission("automation:automationUiScene:execute")
public class AutomationUiExecutionRunController {
    private final AutomationUiExecutionRunService runService;
    private final AutomationPlaywrightCaseService caseService;
    private final AutomationPlaywrightRunnerJobService jobService;

    @PostMapping
    public R<Map<String, String>> create(@Valid @RequestBody AutomationUiExecutionRunCreateReq req) {
        return R.ok(Map.of("runId", runService.create(req)));
    }

    @PostMapping("/{runId}/finish")
    public R<Void> finish(@PathVariable String runId, @RequestParam(required = false) Long failedSceneId) {
        runService.finish(runId, failedSceneId);
        return R.ok();
    }

    @PostMapping("/{runId}/cancel")
    public R<Void> cancel(@PathVariable String runId) {
        // 先提交任务取消屏障，再调用原子批次取消接口；失败可重试，不能继续创建后续批次。
        for (Map<String, Object> batch : runService.cancel(runId)) {
            String batchId = String.valueOf(batch.get("batch_id"));
            caseService.cancelBatch(String.valueOf(batch.get("scene_id")), batchId);
            jobService.cancelBatch(batchId);
        }
        return R.ok();
    }
}
