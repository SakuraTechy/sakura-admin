/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.test.service.impl;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.test.mapper.TestPlanMapper;
import top.continew.admin.test.mapper.TestReportMapper;
import top.continew.admin.test.model.entity.TestPlanDO;
import top.continew.admin.test.model.entity.TestReportDO;
import top.continew.admin.test.model.query.TestReportQuery;
import top.continew.admin.test.model.req.TestReportReq;
import top.continew.admin.test.model.req.TestReportUploadReq;
import top.continew.admin.test.model.resp.TestReportDetailResp;
import top.continew.admin.test.model.resp.TestReportResp;
import top.continew.admin.test.service.TestReportService;
import top.continew.admin.test.service.TestTimedTaskRunService;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.extension.crud.service.BaseServiceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TestReportServiceImpl extends BaseServiceImpl<TestReportMapper, TestReportDO, TestReportResp, TestReportDetailResp, TestReportQuery, TestReportReq> implements TestReportService {

    private final TestPlanMapper testPlanMapper;
    private final TestTimedTaskRunService timedTaskRunService;
    private final TestReportSceneSnapshotService reportSceneSnapshotService;

    @Override
    protected void beforeCreate(TestReportReq req) {
        validateReportScope(req);
    }

    @Override
    protected void beforeUpdate(TestReportReq req, Long id) {
        validateReportScope(req);
    }

    @Override
    public List<TestReportDetailResp> selectByIds(List<Long> ids) {
        return BeanUtil.copyToList(baseMapper.selectBatchIds(ids), TestReportDetailResp.class);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        ids.forEach(id -> baseMapper.lambdaUpdate()
            .eq(TestReportDO::getId, id)
            .set(TestReportDO::getDelFlag, StatusTypeEnum.ABNORMAL)
            .update());
    }

    @Override
    public boolean isExists(String name, Long projectId, Long id) {
        return baseMapper.lambdaQuery()
            .eq(TestReportDO::getProjectId, projectId)
            .eq(TestReportDO::getName, name)
            .eq(TestReportDO::getDelFlag, StatusTypeEnum.NORMAL)
            .ne(id != null, TestReportDO::getId, id)
            .exists();
    }

    @Override
    public void uploadResult(TestReportUploadReq req) {
        if (req.getId() == null) {
            return;
        }
        TestReportDO report = baseMapper.selectById(req.getId());
        if (report == null) {
            return;
        }
        report.setStatus(req.getStatus());
        report.setRunTime(req.getRunTime());
        report.setBuildNumber(req.getBuildNumber());
        report.setConsoleUrl(req.getConsoleUrl());
        report.setReportUrl(req.getReportUrl());
        report.setVideoUrl(req.getVideoUrl());
        report.setStatisticAnalysis(req.getStatisticAnalysis());
        if (report.getStartedAt() == null) {
            report.setStartedAt(report.getCreateTime() == null ? LocalDateTime.now() : report.getCreateTime());
        }
        if (isTerminal(req.getStatus())) {
            report.setFinishedAt(LocalDateTime.now());
        }
        baseMapper.updateById(report);
        timedTaskRunService.completeByReport(report);

        if (report.getTestPlanId() != null) {
            TestPlanDO plan = testPlanMapper.selectById(report.getTestPlanId());
            if (plan != null) {
                boolean terminal = isTerminal(req.getStatus());
                plan.setStatus(terminal ? "COMPLETED" : "RUNNING");
                testPlanMapper.updateById(plan);
            }
        }
    }

    private void validateReportScope(TestReportReq req) {
        if (req.getTestPlanId() != null) {
            TestPlanDO plan = testPlanMapper.selectById(req.getTestPlanId());
            if (plan == null || !StatusTypeEnum.NORMAL.equals(plan.getDelFlag()) || !Objects.equals(plan
                .getProjectId(), req.getProjectId())) {
                throw new BusinessException("测试计划不存在或不属于当前项目");
            }
            if (req.getVersionId() == null) {
                req.setVersionId(plan.getVersionId());
            } else if (plan.getVersionId() != null && !Objects.equals(plan.getVersionId(), req.getVersionId())) {
                throw new BusinessException("测试报告版本与测试计划版本不一致");
            }
        }
        reportSceneSnapshotService.validateVersion(req.getProjectId(), req.getVersionId());
    }

    private boolean isTerminal(String status) {
        return "PASSED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status) || "CANCELLED"
            .equalsIgnoreCase(status);
    }
}
