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

package top.continew.admin.automation.service.impl;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReflectUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.automation.mapper.AutomationEnvironmentConfigMapper;
import top.continew.admin.automation.mapper.AutomationProjectConfigMapper;
import top.continew.admin.automation.model.entity.AutomationBrowserConfigDO;
import top.continew.admin.automation.model.entity.AutomationEnvironmentConfigDO;
import top.continew.admin.automation.model.entity.AutomationJenkinsConfigDO;
import top.continew.admin.automation.model.entity.AutomationNodeConfigDO;
import top.continew.admin.automation.model.entity.AutomationProjectConfigDO;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.admin.automation.model.enums.AutomationUiExecutionEngineEnum;
import top.continew.admin.automation.model.req.AutomationUiSceneClearReq;
import top.continew.admin.automation.model.req.AutomationUiSceneExecAllReq;
import top.continew.admin.automation.model.req.AutomationUiSceneExecReq;
import top.continew.admin.automation.model.req.AutomationUiSceneUploadResultReq;
import top.continew.admin.automation.util.AutomationUiSceneStatusCodes;
import top.continew.admin.automation.util.AutomationUiSceneXmlUtils;

import top.continew.starter.extension.crud.model.query.SortQuery;
import static top.continew.admin.automation.util.AutomationUiSceneStatusCodes.RESULT_NOT_EXECUTED;
import static top.continew.admin.automation.util.AutomationUiSceneStatusCodes.STATUS_NOT_STARTED;
import static top.continew.admin.automation.util.AutomationUiSceneStatusCodes.STATUS_RUNNING;
import top.continew.admin.common.regex.RegexUtil;
import top.continew.admin.common.jenkins.JenkinsService;
import top.continew.admin.common.util.StringUtils;
import top.continew.admin.project.mapper.ProjectConfigMapper;
import top.continew.admin.project.mapper.ProjectEnvironmentConfigMapper;
import top.continew.admin.project.mapper.ProjectVersionConfigMapper;
import top.continew.admin.project.model.entity.ProjectConfigDO;
import top.continew.admin.project.model.entity.ProjectDataBaseConfigDO;
import top.continew.admin.project.model.entity.ProjectEnvironmentConfigDO;
import top.continew.admin.project.model.entity.ProjectServerConfigDO;
import top.continew.admin.project.model.entity.ProjectVersionConfigDO;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;
import top.continew.starter.core.exception.BusinessException;
import top.continew.starter.core.validation.CheckUtils;
import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.automation.mapper.AutomationUiSceneMapper;
import top.continew.admin.automation.model.entity.AutomationUiSceneDO;
import top.continew.admin.automation.model.query.AutomationUiSceneQuery;
import top.continew.admin.automation.model.req.AutomationUiSceneReq;
import top.continew.admin.automation.model.resp.AutomationUiSceneDetailResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneExecResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneRevisionResp;
import top.continew.admin.automation.service.AutomationUiSceneService;
import top.continew.admin.automation.service.AutomationUiExecutionRecordService;
import top.continew.admin.automation.support.AutomationStoragePressureGuard;
import top.continew.admin.automation.service.AutomationPlanReportProgressService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 自动化管理-UI 自动化场景管理业务实现。
 *
 * @author hagyao520
 * @since 2025/06/13 11:49
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationUiSceneServiceImpl extends BaseServiceImpl<AutomationUiSceneMapper, AutomationUiSceneDO, AutomationUiSceneResp, AutomationUiSceneDetailResp, AutomationUiSceneQuery, AutomationUiSceneReq> implements AutomationUiSceneService {
    private static final String DEFAULT_STEP_ID_PREFIX = "CASE_STEP_";

    private final AutomationEnvironmentConfigMapper automationEnvironmentConfigMapper;
    private final AutomationProjectConfigMapper automationProjectConfigMapper;
    private final ProjectEnvironmentConfigMapper projectEnvironmentConfigMapper;
    private final ProjectConfigMapper projectConfigMapper;
    private final ProjectVersionConfigMapper projectVersionConfigMapper;
    private final JdbcTemplate jdbcTemplate;
    private final List<AutomationPlanReportProgressService> planReportProgressServices;
    private final AutomationUiExecutionRecordService executionRecordService;
    /** 所有场景树结构写入统一委托，兼容接口不再保留独立排序算法。 */
    private final top.continew.admin.automation.service.AutomationUiCaseTreeService caseTreeService;

    @Resource
    private AutomationStoragePressureGuard storagePressureGuard;

    @Override
    public void beforeCreate(AutomationUiSceneReq req) {
        if (req == null) {
            return;
        }
        if (StringUtils.isBlank(req.getExecuteStatus())) {
            req.setExecuteStatus(STATUS_NOT_STARTED);
        } else {
            req.setExecuteStatus(AutomationUiSceneStatusCodes.normalizeStatus(req.getExecuteStatus()));
        }
        if (StringUtils.isNotBlank(req.getExecuteResult())) {
            req.setExecuteResult(AutomationUiSceneStatusCodes.normalizeResult(req
                .getExecuteResult(), null, null, null, null));
        }
    }

    @Override
    public void afterCreate(AutomationUiSceneReq req, AutomationUiSceneDO entity) {
        // 空场景也持久化为 []，否则首次新增用例会被误判为历史定义损坏。
        baseMapper.initializeEmptyDefinition(entity.getId());
        entity.setCaseList(new ArrayList<>());
        entity.setCaseTotal(0);
        entity.setStepTotal(0);
    }

    @Override
    public void beforeUpdate(AutomationUiSceneReq req, Long id) {
        if (req == null) {
            return;
        }
        if (StringUtils.isNotBlank(req.getExecuteStatus())) {
            req.setExecuteStatus(AutomationUiSceneStatusCodes.normalizeStatus(req.getExecuteStatus()));
        }
        if (StringUtils.isNotBlank(req.getExecuteResult())) {
            req.setExecuteResult(AutomationUiSceneStatusCodes.normalizeResult(req
                .getExecuteResult(), null, null, null, null));
        }
    }

    @Override
    protected QueryWrapper<AutomationUiSceneDO> buildQueryWrapper(AutomationUiSceneQuery query) {
        List<Long> excludeIds = query.getExcludeIds();
        query.setExcludeIds(null);
        QueryWrapper<AutomationUiSceneDO> queryWrapper = super.buildQueryWrapper(query);
        query.setExcludeIds(excludeIds);
        queryWrapper.notIn(CollUtil.isNotEmpty(excludeIds), "id", excludeIds);
        Set<String> excludedColumns = Boolean.TRUE.equals(query.getIncludeDefinition())
            ? Set.of("debug_record", "test_record")
            : Set.of("case_list", "debug_record", "test_record");
        queryWrapper.select(AutomationUiSceneDO.class, field -> !excludedColumns.contains(field.getColumn()));
        return queryWrapper;
    }

    @Override
    public List<AutomationUiSceneResp> list(AutomationUiSceneQuery query, SortQuery sortQuery) {
        String executeStatus = query.getExecuteStatus();
        // 最新状态已迁到窄表，不能再用场景表中的兼容旧值过滤。
        query.setExecuteStatus(null);
        List<AutomationUiSceneResp> result = super.list(query, sortQuery);
        query.setExecuteStatus(executeStatus);
        if (result == null || result.isEmpty()) {
            return result;
        }
        result.forEach(item -> {
            item.setCreateUserString(UserContextHolder.getNickname(item.getCreateUser()));
            item.setUpdateUserString(UserContextHolder.getNickname(item.getUpdateUser()));
            hydrateExecutionData(item, shouldSelectExecutionRecords(query) ? 100 : 1);
        });
        String testPlanId = query.getTestPlanId();
        String testReportId = query.getTestReportId();
        Integer buildNumber = query.getBuildNumber();
        String executeResultType = query.getExecuteResultType();
        String executeResult = query.getExecuteResult();
        if (shouldSelectExecutionRecords(query)) {
            String recordType = "report".equalsIgnoreCase(executeResultType) ? "testRecord" : "debugRecord";
            result
                .forEach(resp -> filterRecord(resp, testPlanId, testReportId, buildNumber, executeResultType, executeResult, executeStatus, recordType));
            if (requiresExecutionRecordMatch(query)) {
                result = result.stream().filter(resp -> {
                    List<Object> records = "testRecord".equals(recordType)
                        ? resp.getTestRecord()
                        : resp.getDebugRecord();
                    return records != null && !records.isEmpty();
                }).collect(java.util.stream.Collectors.toList());
            }
        } else if (StringUtils.isNotBlank(executeStatus)) {
            String expectedStatus = AutomationUiSceneStatusCodes.normalizeStatus(executeStatus);
            result = result.stream()
                .filter(item -> expectedStatus.equals(AutomationUiSceneStatusCodes.normalizeStatus(item
                    .getExecuteStatus())))
                .collect(java.util.stream.Collectors.toList());
        }
        return result;
    }

    @Override
    public PageResp<AutomationUiSceneResp> page(AutomationUiSceneQuery query, PageQuery pageQuery) {
        // 分页接口不需要 caseList，禁止外部 query 参数绕过详情接口的展示脱敏。
        query.setIncludeDefinition(false);
        // 只有明确按执行结果筛选时才先过滤再分页；executeResultType 仅表示展示哪类记录，不能隐藏未执行场景。
        if (requiresExecutionRecordMatch(query)) {
            List<AutomationUiSceneResp> filteredList = this.list(query, pageQuery);
            return PageResp.build(pageQuery.getPage(), pageQuery.getSize(), filteredList);
        }
        IPage<AutomationUiSceneDO> pageDO = baseMapper.selectPage(new Page<>(pageQuery.getPage(), pageQuery
            .getSize()), buildQueryWrapper(query));
        PageResp<AutomationUiSceneResp> pageResp = PageResp.build(pageDO, AutomationUiSceneResp.class);
        pageResp.getList().forEach(item -> {
            item.setCreateUserString(UserContextHolder.getNickname(item.getCreateUser()));
            item.setUpdateUserString(UserContextHolder.getNickname(item.getUpdateUser()));
            hydrateExecutionData(item, shouldSelectExecutionRecords(query) ? 100 : 1);
            if (shouldSelectExecutionRecords(query)) {
                String recordType = "report".equalsIgnoreCase(query.getExecuteResultType())
                    ? "testRecord"
                    : "debugRecord";
                filterRecord(item, query.getTestPlanId(), query.getTestReportId(), query.getBuildNumber(), query
                    .getExecuteResultType(), query.getExecuteResult(), query.getExecuteStatus(), recordType);
            }
        });
        return pageResp;
    }

    static boolean shouldSelectExecutionRecords(AutomationUiSceneQuery query) {
        return query != null && StringUtils.isNotBlank(query.getExecuteResultType()) && (StringUtils.isNotBlank(query
            .getTestPlanId()) || requiresExecutionRecordMatch(query));
    }

    static boolean requiresExecutionRecordMatch(AutomationUiSceneQuery query) {
        return query != null && (StringUtils.isNotBlank(query.getExecuteStatus()) || (StringUtils.isNotBlank(query
            .getExecuteResultType()) && (StringUtils.isNotBlank(query.getTestReportId()) || query
                .getBuildNumber() != null || StringUtils.isNotBlank(query.getExecuteResult()))));
    }

    @Override
    public AutomationUiSceneDetailResp get(Long id) {
        AutomationUiSceneDetailResp detail = super.get(id);
        hydrateExecutionData(detail, 100);
        return detail;
    }

    private void filterRecord(AutomationUiSceneResp resp,
                              String testPlanId,
                              String testReportId,
                              Integer buildNumber,
                              String executeResultType,
                              String executeResult,
                              String executeStatus,
                              String recordType) {
        boolean isTestRecord = "testRecord".equals(recordType);
        List<Object> records = isTestRecord ? resp.getTestRecord() : resp.getDebugRecord();
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Object> filtered = new ArrayList<>();
        for (Object record : records) {
            if (!(record instanceof Map<?, ?> recordMap)) {
                continue;
            }
            boolean match = true;
            if (buildNumber != null) {
                Object recordBuildNumber = recordMap.get("buildNumber");
                if (recordBuildNumber == null || !buildNumber.equals(recordBuildNumber)) {
                    match = false;
                }
            }
            if (match && StringUtils.isNotBlank(testPlanId)) {
                Object recordTestPlanId = recordMap.get("testPlanId");
                if (recordTestPlanId == null || !testPlanId.equals(String.valueOf(recordTestPlanId))) {
                    match = false;
                }
            }
            if (match && StringUtils.isNotBlank(testReportId)) {
                Object recordTestReportId = recordMap.get("testReportId");
                if (recordTestReportId == null || !testReportId.equals(String.valueOf(recordTestReportId))) {
                    match = false;
                }
            }
            if (match && StringUtils.isNotBlank(executeResult)) {
                Object recordExecuteResult = recordMap.get("executeResult");
                String expectedResult = AutomationUiSceneStatusCodes
                    .normalizeResult(executeResult, null, null, null, null);
                String actualResult = recordExecuteResult == null
                    ? null
                    : AutomationUiSceneStatusCodes.normalizeResult(String
                        .valueOf(recordExecuteResult), null, null, null, null);
                if (!expectedResult.equals(actualResult)) {
                    match = false;
                }
            }
            if (match && StringUtils.isNotBlank(executeStatus)) {
                Object recordExecuteStatus = recordMap.get("executeStatus");
                String expectedStatus = AutomationUiSceneStatusCodes.normalizeStatus(executeStatus);
                String actualStatus = recordExecuteStatus == null
                    ? null
                    : AutomationUiSceneStatusCodes.normalizeStatus(String.valueOf(recordExecuteStatus));
                if (!expectedStatus.equals(actualStatus)) {
                    match = false;
                }
            }
            if (match) {
                filtered.add(record);
            }
        }
        if (isTestRecord) {
            resp.setTestRecord(filtered.isEmpty() ? null : filtered);
        } else {
            resp.setDebugRecord(filtered.isEmpty() ? null : filtered);
        }
    }

    @Override
    public List<AutomationUiSceneDetailResp> selectByIds(List<Long> ids) {
        List<AutomationUiSceneDetailResp> list = BeanUtil.copyToList(baseMapper
            .selectByIds(ids), AutomationUiSceneDetailResp.class);
        list.forEach(item -> {
            item.setCreateUserString(UserContextHolder.getNickname(item.getCreateUser()));
            item.setUpdateUserString(UserContextHolder.getNickname(item.getUpdateUser()));
            hydrateExecutionData(item, 100);
        });
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        ids.stream().filter(Objects::nonNull).distinct().forEach(executionRecordService::deleteScene);
        baseMapper.deleteByIds(ids);
    }

    @Override
    public Long copy(Long sourceId, AutomationUiSceneReq req) {
        AutomationUiSceneDO source = baseMapper.selectById(sourceId);
        CheckUtils.throwIfNull(source, "源场景不存在，无法复制 UI 自动化场景");

        AutomationUiSceneDO target = BeanUtil.copyProperties(source, AutomationUiSceneDO.class);
        target.setId(null);

        // 覆盖新场景基础字段，继承来源场景结构数据
        target.setSceneId(req.getSceneId());
        target.setName(req.getName());
        target.setDescription(req.getDescription());
        target.setProjectId(req.getProjectId());
        target.setProjectName(req.getProjectName());
        target.setVersionId(req.getVersionId());
        target.setVersionName(req.getVersionName());
        target.setModuleId(req.getModuleId());
        target.setModulePath(req.getModulePath());
        target.setLevel(req.getLevel());
        target.setStatus(req.getStatus());
        target.setTags(req.getTags());
        List<CaseDO> copiedCases = source.getCaseList() == null ? new ArrayList<>() : source.getCaseList();
        target.setCaseList(copiedCases);
        target.setDefinitionVersion(0L);
        target.setDelFlag(req.getDelFlag());

        // 复制后清理执行态与统计字段，避免历史数据污染新场景
        target.setTestPlanId(null);
        target.setReportId(null);
        target.setDebugRecord(null);
        target.setExecuteStatus(STATUS_NOT_STARTED);
        target.setExecuteResult(RESULT_NOT_EXECUTED);
        target.setTestRecord(null);
        target.setBuildNumber(null);
        target.setConsoleUrl(null);
        target.setTestReportUrl(null);
        target.setCaseTotal(copiedCases.size());
        target.setCasePass(null);
        target.setCaseFail(null);
        target.setCaseSkip(null);
        target.setPassRate(null);
        target.setLastResult(null);
        target.setStepTotal(copiedCases.stream()
            .filter(Objects::nonNull)
            .map(CaseDO::getStepList)
            .filter(Objects::nonNull)
            .mapToInt(List::size)
            .sum());
        target.setStepPass(null);
        target.setStepFail(null);
        target.setStepSkip(null);
        target.setUpdateIp(null);

        List<Object> defaultDebugRecord = new ArrayList<>();
        Map<String, Object> defaultRecord = new HashMap<>(16);
        defaultRecord.put("sceneTotal", 0);
        defaultRecord.put("scenePass", 0);
        defaultRecord.put("sceneFail", 0);
        defaultRecord.put("sceneSkip", 0);
        defaultRecord.put("scenePassRate", "-");
        defaultRecord.put("caseTotal", 0);
        defaultRecord.put("casePass", 0);
        defaultRecord.put("caseFail", 0);
        defaultRecord.put("caseSkip", 0);
        defaultRecord.put("casePassRate", "0%");
        defaultRecord.put("stepTotal", 0);
        defaultRecord.put("stepPass", 0);
        defaultRecord.put("stepFail", 0);
        defaultRecord.put("stepSkip", 0);
        defaultRecord.put("stepPassRate", "0%");
        defaultRecord.put("executeName", "-");
        defaultRecord.put("executeStatus", "10");
        defaultRecord.put("executeResult", "13");
        defaultRecord.put("duration", "-");
        defaultDebugRecord.add(defaultRecord);
        target.setDebugRecord(defaultDebugRecord);

        baseMapper.insert(target);
        return target.getId();
    }

    public String addCase(CaseDO caseDO, Long id) {
        return caseTreeService.addCase(id, caseDO).getSelectedNode().getCaseId();
    }

    public void updateCase(CaseDO caseDO, Long id) {
        caseTreeService.updateCase(id, caseDO);
    }

    public void deleteCase(CaseDO caseDO, Long id) {
        caseTreeService.deleteLegacyCase(id, caseDO);
    }

    @Transactional(rollbackFor = Exception.class)
    public void dragCase(CaseDO caseDO, Long id) {
        caseTreeService.moveLegacyCase(id, caseDO);
    }

    public String addStep(StepDO stepDO, Long id) {
        // 前端必须使用服务端实际分配的步骤 ID 恢复选中，不能返回请求中的 CASE_STEP_ 前缀。
        return caseTreeService.addStep(id, stepDO).getSelectedNode().getStepId();
    }

    private String resolveStepId(String candidate, List<StepDO> stepList, int order) {
        String stepId = candidate == null ? "" : candidate.trim();
        if (stepId.isEmpty()) {
            stepId = DEFAULT_STEP_ID_PREFIX;
        }
        if (stepId.endsWith("_")) {
            stepId = nextStepId(stepId, stepList, order);
        }
        if (!RegexUtil.isClassMethod(stepId)) {
            throw ReflectUtil.newInstance(BusinessException.class, new Object[] {"步骤 ID 格式不合法"});
        }
        if (StringUtils.isNotEmpty(stepList)) {
            for (StepDO item : stepList) {
                if (item != null && stepId.equals(item.getId())) {
                    throw ReflectUtil.newInstance(BusinessException.class, new Object[] {"步骤 ID 已存在，请勿重复添加"});
                }
            }
        }
        return stepId;
    }

    private String nextStepId(String prefix, List<StepDO> stepList, int fallbackOrder) {
        int max = 0;
        if (StringUtils.isNotEmpty(stepList)) {
            for (StepDO item : stepList) {
                if (item == null || item.getId() == null || !item.getId().startsWith(prefix)) {
                    continue;
                }
                String suffix = item.getId().substring(prefix.length());
                if (suffix.matches("\\d+")) {
                    max = Math.max(max, Integer.parseInt(suffix));
                }
            }
        }
        int next = Math.max(max + 1, fallbackOrder);
        return prefix + String.format("%03d", next);
    }

    public void updateStep(StepDO stepDO, Long id) {
        caseTreeService.updateStep(id, stepDO);
    }

    public void deleteStep(StepDO stepDO, Long id) {
        caseTreeService.deleteLegacyStep(id, stepDO);
    }

    @Transactional(rollbackFor = Exception.class)
    public void dragStep1(StepDO stepDO, Long id) {
        caseTreeService.moveLegacyStep(id, stepDO);
    }

    public void dragStep(StepDO stepDO, Long id) {
        caseTreeService.moveLegacyStep(id, stepDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiSceneExecResp exec(AutomationUiSceneExecReq req) {
        if (storagePressureGuard != null) {
            storagePressureGuard.assertExecutionAllowed();
        }
        AutomationUiExecutionEngineEnum engine = resolveExecutionEngine(req.getEngine());
        // 默认仍走 Jenkins；Playwright 测试计划调度器接入前，显式阻断避免误触发旧链路。
        CheckUtils.throwIf(AutomationUiExecutionEngineEnum.PLAYWRIGHT
            .equals(engine), "执行失败：Playwright 执行引擎尚未接入测试计划和报告闭环，请先使用 /automation/playwright/testcases/{caseKey} 阶段 4 接口执行");

        List<AutomationUiSceneDO> sceneList = baseMapper.selectBatchIds(req.getSceneIds());
        CheckUtils.throwIf(sceneList == null || sceneList.isEmpty(), "执行失败：未找到可执行场景");
        CheckUtils.throwIf(sceneList.size() != req.getSceneIds().size(), "执行失败：部分场景不存在");
        sceneList.sort(Comparator.comparing(AutomationUiSceneDO::getSceneId, Comparator.nullsLast(String::compareTo)));

        Long projectId = sceneList.get(0).getProjectId();
        Long versionId = sceneList.get(0).getVersionId();
        boolean sameProjectVersion = sceneList.stream()
            .allMatch(scene -> Objects.equals(projectId, scene.getProjectId()) && Objects.equals(versionId, scene
                .getVersionId()));
        CheckUtils.throwIf(!sameProjectVersion, "执行失败：场景必须属于同一项目和版本");

        ProjectEnvironmentConfigDO projectEnvironment = projectEnvironmentConfigMapper.selectById(req
            .getProjectEnvironmentId());
        CheckUtils.throwIfNull(projectEnvironment, "执行失败：项目环境不存在");
        CheckUtils.throwIf(!Objects.equals(projectId, projectEnvironment.getProjectId()), "执行失败：项目环境与场景所属项目不一致");

        ProjectConfigDO projectConfig = projectConfigMapper.selectById(projectId);
        ProjectVersionConfigDO versionConfig = projectVersionConfigMapper.selectById(versionId);
        CheckUtils.throwIfNull(projectConfig, "执行失败：项目配置不存在");
        CheckUtils.throwIfNull(versionConfig, "执行失败：版本配置不存在");

        ProjectServerConfigDO serverConfig = firstBean(projectEnvironment
            .getServerConfig(), ProjectServerConfigDO.class);
        ProjectDataBaseConfigDO dataBaseConfig = firstBean(projectEnvironment
            .getDataBaseConfig(), ProjectDataBaseConfigDO.class);

        AutomationEnvironmentConfigDO automationEnvironment = automationEnvironmentConfigMapper.selectById(req
            .getAutomationEnvironmentId());
        CheckUtils.throwIfNull(automationEnvironment, "执行失败：自动化环境不存在");
        AutomationProjectConfigDO automationProjectConfig = resolveAutomationProjectConfig(automationEnvironment
            .getProjectConfig(), projectConfig);
        AutomationJenkinsConfigDO jenkinsConfig = firstBean(automationEnvironment
            .getJenkinsConfig(), AutomationJenkinsConfigDO.class);
        AutomationNodeConfigDO nodeConfig = resolveNode(automationEnvironment.getNodeConfig());
        AutomationBrowserConfigDO browserConfig = firstBean(automationEnvironment
            .getBrowserConfig(), AutomationBrowserConfigDO.class);

        CheckUtils.throwIfNull(serverConfig, "执行失败：项目环境服务器配置缺失");
        CheckUtils.throwIfNull(automationProjectConfig, "执行失败：自动化项目配置缺失");
        CheckUtils.throwIfNull(jenkinsConfig, "执行失败：自动化 Jenkins 配置缺失");
        CheckUtils.throwIfNull(nodeConfig, "执行失败：自动化节点配置缺失");
        CheckUtils.throwIfNull(browserConfig, "执行失败：自动化浏览器配置缺失");

        String jobName = resolveJobName(jenkinsConfig);
        CheckUtils.throwIf(StringUtils.isBlank(jobName), "执行失败：Jenkins Job 未配置");

        String date = DateUtil.formatDate(new Date());
        String executeName = StringUtils.isBlank(req.getExecuteName()) ? "system" : req.getExecuteName();
        String executeEmail = StringUtils.isBlank(req.getExecuteEmail()) ? "" : req.getExecuteEmail();
        String frontendPort = resolveServerConfigParam(serverConfig, "前端端口");
        String resolvedFrontendPort = StringUtils.isBlank(frontendPort)
            ? serverConfig.getPort() == null ? "" : String.valueOf(serverConfig.getPort())
            : frontendPort;
        String frontendDomain = resolveServerConfigParam(serverConfig, "前端域名");
        String serverEth = resolveServerConfigParam(serverConfig, "服务器网卡");
        String testReportId = UUID.randomUUID().toString().replace("-", "");
        Path sceneWorkspaceRoot = resolveSceneWorkspaceRoot(automationProjectConfig);

        AutomationUiSceneXmlUtils.BundleContext bundleContext;
        try {
            bundleContext = AutomationUiSceneXmlUtils.createBundle(sceneList, defaultString(projectConfig
                .getName()), defaultString(projectConfig.getAbbreviate()), defaultString(versionConfig
                    .getName()), browserConfig == null
                        ? ""
                        : defaultString(browserConfig.getName()), defaultString(nodeConfig
                            .getName()), frontendDomain, serverEth, sceneWorkspaceRoot);
        } catch (Exception e) {
            throw new BusinessException("执行失败：生成场景 XML 压缩包失败：" + e.getMessage());
        }

        Map<String, String> params = new LinkedHashMap<>(28);
        params.put("Date", date);
        params.put("Name", executeName);
        params.put("Email", executeEmail);
        params.put("Product", defaultString(projectConfig.getName()));
        params.put("Abbreviate", defaultString(projectConfig.getAbbreviate()));
        params.put("Version", defaultString(versionConfig.getName()));
        params.put("Description", defaultString(versionConfig.getDescription()));
        params.put("IP", defaultString(serverConfig.getIp()));
        params.put("Port", resolvedFrontendPort);
        params.put("EDescription", defaultString(projectEnvironment.getDescription()));
        params.put("ServerPort", String.valueOf(serverConfig.getPort()));
        params.put("ServerUserName", defaultString(serverConfig.getUserName()));
        params.put("ServerPassWord", defaultString(serverConfig.getPassWord()));
        params.put("DataBasePort", dataBaseConfig == null || dataBaseConfig.getPort() == null
            ? ""
            : String.valueOf(dataBaseConfig.getPort()));
        params.put("DataBaseName", dataBaseConfig == null ? "" : defaultString(dataBaseConfig.getUserName()));
        params.put("DataBasePassWord", dataBaseConfig == null ? "" : defaultString(dataBaseConfig.getPassWord()));
        params.put("Domain", StringUtils.isBlank(frontendDomain)
            ? defaultString(projectEnvironment.getLastDomain())
            : frontendDomain);
        params.put("Run", defaultString(nodeConfig.getName()));
        params.put("Branch", "ankki");
        params.put("jenkinsUrl", normalizeUrl(jenkinsConfig.getUrl()) + "/job/" + jobName);
        params.put("testPlanId", defaultString(req.getTestPlanId()));
        params.put("testReportId", StringUtils.isBlank(req.getTestReportId()) ? testReportId : req.getTestReportId());
        params.put("sceneIds", joinSceneIds(sceneList));
        params.put("browser", browserConfig == null ? "" : defaultString(browserConfig.getName()));
        params.put("browserType", browserConfig == null ? "" : defaultString(browserConfig.getType()));
        params.put("sceneWorkspace", bundleContext.workspaceRoot().toString());
        params.put("sceneXmlDir", bundleContext.testCaseDir().toString());
        params.put("testngXmlPath", bundleContext.testngXmlPath().toString());
        params.put("extentXmlPath", bundleContext.extentXmlPath().toString());

        Integer buildNumber = JenkinsService.launchJob(jenkinsConfig.getUrl(), jenkinsConfig
            .getUserName(), jenkinsConfig.getPassWord(), jobName, params);
        try {
            JenkinsService.close();
        } catch (Exception e) {
            log.warn("Failed to close Jenkins client: {}", e.getMessage());
        }
        CheckUtils.throwIf(buildNumber == null || buildNumber <= 0, "执行失败：Jenkins 构建号无效");

        String consoleUrl = normalizeUrl(jenkinsConfig.getUrl()) + "/job/" + jobName + "/" + buildNumber + "/console";
        String testReportUrl = normalizeUrl(jenkinsConfig
            .getUrl()) + "/job/" + jobName + "/" + buildNumber + "/artifact/TestOutput/ExtentReport/" + date + "/" + defaultString(projectConfig
                .getAbbreviate()) + "/" + AutomationUiSceneXmlUtils.sanitizePathSegment(defaultString(versionConfig
                    .getName())) + "/index.html";

        for (AutomationUiSceneDO scene : sceneList) {
            AutomationUiSceneDO latest = baseMapper.selectById(scene.getId());
            if (latest == null) {
                continue;
            }
            Map<String, Object> executionRecord = buildExecutingRecord(latest, buildNumber, consoleUrl, testReportUrl, executeName, req
                .getTestPlanId(), req.getTestReportId());
            latest.setExecuteStatus(STATUS_RUNNING);
            latest.setExecuteResult(RESULT_NOT_EXECUTED);
            latest.setBuildNumber(buildNumber);
            latest.setConsoleUrl(consoleUrl);
            latest.setTestReportUrl(testReportUrl);
            latest.setLastResult(RESULT_NOT_EXECUTED);
            if (StringUtils.isNotBlank(req.getTestReportId())) {
                latest.setReportId(Long.parseLong(req.getTestReportId()));
            }
            executionRecordService.saveRecord(latest, executionRecord, null);
        }

        AutomationUiSceneExecResp resp = new AutomationUiSceneExecResp();
        resp.setTestReportId(StringUtils.isBlank(req.getTestReportId()) ? testReportId : req.getTestReportId());
        resp.setBuildNumber(buildNumber);
        resp.setConsoleUrl(consoleUrl);
        resp.setTestReportUrl(testReportUrl);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutomationUiSceneExecResp execAll(AutomationUiSceneExecAllReq req) {
        AutomationUiSceneQuery query = new AutomationUiSceneQuery();
        query.setProjectId(req.getProjectId());
        query.setVersionId(req.getVersionId());
        query.setModuleId(req.getModuleId());
        query.setLevel(req.getLevel());
        query.setExecuteStatus(req.getExecuteStatus());
        query.setExecuteResult(req.getExecuteResult());
        query.setStatus(req.getStatus());
        List<AutomationUiSceneResp> sceneRespList = super.list(query, null);
        CheckUtils.throwIf(sceneRespList == null || sceneRespList.isEmpty(), "执行失败：当前查询条件未匹配到场景");

        List<Long> sceneIds = new ArrayList<>(sceneRespList.size());
        for (AutomationUiSceneResp sceneResp : sceneRespList) {
            sceneIds.add(sceneResp.getId());
        }

        AutomationUiSceneExecReq execReq = new AutomationUiSceneExecReq();
        execReq.setSceneIds(sceneIds);
        execReq.setProjectEnvironmentId(req.getProjectEnvironmentId());
        execReq.setAutomationEnvironmentId(req.getAutomationEnvironmentId());
        execReq.setEngine(req.getEngine());
        execReq.setExecuteName(req.getExecuteName());
        execReq.setExecuteEmail(req.getExecuteEmail());
        execReq.setTestPlanId(req.getTestPlanId());
        execReq.setTestReportId(req.getTestReportId());
        return exec(execReq);
    }

    private AutomationUiExecutionEngineEnum resolveExecutionEngine(AutomationUiExecutionEngineEnum engine) {
        return engine == null ? AutomationUiExecutionEngineEnum.JENKINS : engine;
    }

    @Override
    public List<AutomationUiSceneResp> listSceneRespByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<AutomationUiSceneDO> sceneList = baseMapper.selectBatchIds(ids);
        sceneList.sort(Comparator.comparing(AutomationUiSceneDO::getSceneId, Comparator.nullsLast(String::compareTo)));
        List<AutomationUiSceneResp> result = BeanUtil.copyToList(sceneList, AutomationUiSceneResp.class);
        result.forEach(item -> hydrateExecutionData(item, 100));
        return result;
    }

    private void hydrateExecutionData(AutomationUiSceneResp item, int historyLimit) {
        if (item == null || item.getId() == null) {
            return;
        }
        item.setDebugRecord(executionRecordService.listRecords(item.getId(), false, historyLimit));
        item.setTestRecord(executionRecordService.listRecords(item.getId(), true, historyLimit));
        jdbcTemplate
            .query("SELECT execution_revision, execute_status, execute_result, case_total, case_pass, case_fail, case_skip," + " pass_rate, last_result, step_total, step_pass, step_fail, step_skip, update_time" + " FROM automation_ui_scene_execution_state WHERE scene_id = ?", rs -> {
                if (!rs.next())
                    return;
                item.setExecutionRevision(rs.getLong("execution_revision"));
                item.setExecuteStatus(rs.getString("execute_status"));
                item.setExecuteResult(rs.getString("execute_result"));
                item.setCaseTotal((Integer)rs.getObject("case_total"));
                item.setCasePass((Integer)rs.getObject("case_pass"));
                item.setCaseFail((Integer)rs.getObject("case_fail"));
                item.setCaseSkip((Integer)rs.getObject("case_skip"));
                item.setPassRate(rs.getString("pass_rate"));
                item.setLastResult(rs.getString("last_result"));
                item.setStepTotal((Integer)rs.getObject("step_total"));
                item.setStepPass((Integer)rs.getObject("step_pass"));
                item.setStepFail((Integer)rs.getObject("step_fail"));
                item.setStepSkip((Integer)rs.getObject("step_skip"));
                Timestamp timestamp = rs.getTimestamp("update_time");
                if (timestamp != null)
                    item.setUpdateTime(timestamp.toLocalDateTime());
            }, item.getId());
    }

    private void hydrateExecutionData(AutomationUiSceneDetailResp item, int historyLimit) {
        if (item == null || item.getId() == null) {
            return;
        }
        item.setDebugRecord(executionRecordService.listRecords(item.getId(), false, historyLimit));
        item.setTestRecord(executionRecordService.listRecords(item.getId(), true, historyLimit));
        jdbcTemplate
            .query("SELECT execution_revision, execute_status, execute_result, case_total, case_pass, case_fail, case_skip," + " pass_rate, last_result, step_total, step_pass, step_fail, step_skip" + " FROM automation_ui_scene_execution_state WHERE scene_id = ?", rs -> {
                if (!rs.next())
                    return;
                item.setExecutionRevision(rs.getLong("execution_revision"));
                item.setExecuteStatus(rs.getString("execute_status"));
                item.setExecuteResult(rs.getString("execute_result"));
                item.setCaseTotal((Integer)rs.getObject("case_total"));
                item.setCasePass((Integer)rs.getObject("case_pass"));
                item.setCaseFail((Integer)rs.getObject("case_fail"));
                item.setCaseSkip((Integer)rs.getObject("case_skip"));
                item.setPassRate(rs.getString("pass_rate"));
                item.setLastResult(rs.getString("last_result"));
                item.setStepTotal((Integer)rs.getObject("step_total"));
                item.setStepPass((Integer)rs.getObject("step_pass"));
                item.setStepFail((Integer)rs.getObject("step_fail"));
                item.setStepSkip((Integer)rs.getObject("step_skip"));
            }, item.getId());
    }

    @Override
    public List<AutomationUiSceneRevisionResp> listSceneRevisions(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectRevisions(ids);
    }

    @Override
    public void exportXml(Collection<Long> ids, HttpServletResponse response) {
        CheckUtils.throwIf(ids == null || ids.isEmpty(), "导出失败：未提供场景 ID");
        writeSceneBundle(baseMapper.selectBatchIds(ids), response);
    }

    @Override
    public void exportXmlAll(AutomationUiSceneQuery query, HttpServletResponse response) {
        List<AutomationUiSceneResp> sceneRespList = super.list(query, null);
        CheckUtils.throwIf(sceneRespList == null || sceneRespList.isEmpty(), "导出失败：当前查询条件未匹配到场景");
        List<Long> ids = new ArrayList<>(sceneRespList.size());
        for (AutomationUiSceneResp item : sceneRespList) {
            ids.add(item.getId());
        }
        writeSceneBundle(baseMapper.selectBatchIds(ids), response);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadResults(AutomationUiSceneUploadResultReq req) {
        AutomationUiSceneDO scene = resolveSceneForUpload(req);
        CheckUtils.throwIfNull(scene, "上传失败：场景不存在");

        AutomationUiSceneUploadResultReq.UiStatistic ui = req.getStatisticAnalysis().getUi();
        CheckUtils.throwIfNull(ui, "上传失败：UI 统计数据缺失");

        Map<String, Object> executingRecord = executionRecordService.findBatch(scene.getId(), String.valueOf(ui
            .getBuildNumber()));

        Map<String, Object> latest = new LinkedHashMap<>();
        String resolvedTestPlanId = StringUtils.defaultIfBlank(req.getTestPlanId(), executingRecord == null
            ? null
            : String.valueOf(executingRecord.get("testPlanId")));
        latest.put("testPlanId", resolvedTestPlanId);
        latest.put("testReportId", StringUtils.defaultIfBlank(req.getTestReportId(), executingRecord == null
            ? null
            : String.valueOf(executingRecord.get("testReportId"))));
        latest.put("buildNumber", ui.getBuildNumber());
        latest.put("executionType", "jenkins");
        latest.put("executionId", String.valueOf(ui.getBuildNumber()));
        latest.put("startedAt", ui.getDurationStartTime());
        latest.put("finishedAt", ui.getDurationEndTime());
        String consoleUrl = StringUtils.defaultIfBlank(ui.getConsoleUrl(), scene.getConsoleUrl());
        String testReportUrl = StringUtils.defaultIfBlank(ui.getTestReportUrl(), scene.getTestReportUrl());
        latest.put("artifactUrls", Map
            .of("console", defaultString(consoleUrl), "report", defaultString(testReportUrl)));
        latest.put("consoleUrl", consoleUrl);
        latest.put("testReportUrl", testReportUrl);
        latest.put("sceneTotal", defaultNumber(ui.getSceneTotal()));
        latest.put("scenePass", defaultNumber(ui.getScenePass()));
        latest.put("sceneFail", defaultNumber(ui.getSceneFail()));
        latest.put("sceneSkip", defaultNumber(ui.getSceneSkip()));
        latest.put("scenePassRate", StringUtils.defaultIfBlank(ui.getScenePassRate(), "-"));
        latest.put("caseTotal", defaultNumber(ui.getCaseTotal()));
        latest.put("casePass", defaultNumber(ui.getCasePass()));
        latest.put("caseFail", defaultNumber(ui.getCaseFail()));
        latest.put("caseSkip", defaultNumber(ui.getCaseSkip()));
        latest.put("casePassRate", StringUtils.defaultIfBlank(ui.getCasePassRate(), "-"));
        latest.put("stepTotal", defaultNumber(ui.getStepTotal()));
        latest.put("stepPass", defaultNumber(ui.getStepPass()));
        latest.put("stepFail", defaultNumber(ui.getStepFail()));
        latest.put("stepSkip", defaultNumber(ui.getStepSkip()));
        latest.put("stepPassRate", StringUtils.defaultIfBlank(ui.getStepPassRate(), "-"));
        latest.put("executeName", StringUtils.defaultIfBlank(ui.getExecuteName(), "-"));
        latest.put("executeStatus", AutomationUiSceneStatusCodes.normalizeStatus(ui.getExecuteStatus()));
        latest.put("executeResult", AutomationUiSceneStatusCodes.normalizeResult(ui.getExecuteResult(), ui
            .getSceneTotal(), ui.getScenePass(), ui.getSceneFail(), ui.getSceneSkip()));
        latest.put("duration", StringUtils.defaultIfBlank(ui.getDuration(), "-"));
        latest.put("durationStartTime", ui.getDurationStartTime());
        latest.put("durationEndTime", ui.getDurationEndTime());

        String executeStatus = AutomationUiSceneStatusCodes.normalizeStatus(ui.getExecuteStatus());
        String executeResult = AutomationUiSceneStatusCodes.normalizeResult(ui.getExecuteResult(), ui
            .getSceneTotal(), ui.getScenePass(), ui.getSceneFail(), ui.getSceneSkip());
        scene.setExecuteStatus(executeStatus);
        scene.setExecuteResult(executeResult);
        scene.setLastResult(executeResult);
        scene.setBuildNumber(ui.getBuildNumber());
        scene.setConsoleUrl(consoleUrl);
        scene.setTestReportUrl(testReportUrl);
        scene.setCaseTotal(defaultNumber(ui.getCaseTotal()));
        scene.setCasePass(defaultNumber(ui.getCasePass()));
        scene.setCaseFail(defaultNumber(ui.getCaseFail()));
        scene.setCaseSkip(defaultNumber(ui.getCaseSkip()));
        scene.setPassRate(StringUtils.defaultIfBlank(ui.getCasePassRate(), scene.getPassRate()));
        scene.setStepTotal(defaultNumber(ui.getStepTotal()));
        scene.setStepPass(defaultNumber(ui.getStepPass()));
        scene.setStepFail(defaultNumber(ui.getStepFail()));
        scene.setStepSkip(defaultNumber(ui.getStepSkip()));
        executionRecordService.saveRecord(scene, latest, null);
        Object reportIdValue = latest.get("testReportId");
        String testReportId = reportIdValue == null ? "" : String.valueOf(reportIdValue);
        if (StringUtils.isNotBlank(resolvedTestPlanId) && StringUtils
            .isNotBlank(testReportId) && planReportProgressServices != null) {
            planReportProgressServices.forEach(service -> service.onProgressChanged(resolvedTestPlanId, testReportId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearResults(AutomationUiSceneClearReq req) {
        List<AutomationUiSceneDO> sceneList = baseMapper.selectBatchIds(req.getSceneIds());
        for (AutomationUiSceneDO scene : sceneList) {
            scene.setDebugRecord(null);
            scene.setExecuteStatus(null);
            scene.setExecuteResult(null);
            scene.setTestRecord(null);
            scene.setBuildNumber(null);
            scene.setConsoleUrl(null);
            scene.setTestReportUrl(null);
            scene.setCaseTotal(null);
            scene.setCasePass(null);
            scene.setCaseFail(null);
            scene.setCaseSkip(null);
            scene.setPassRate(null);
            scene.setLastResult(null);
            scene.setStepTotal(null);
            scene.setStepPass(null);
            scene.setStepFail(null);
            scene.setStepSkip(null);
            baseMapper.clearExecutionState(scene.getId());
            executionRecordService.clearScene(scene.getId());
        }
    }

    /**
     * 导出指定场景 XML 压缩包。
     *
     * @param sceneList 场景列表
     * @param response  HTTP 响应
     */
    private void writeSceneBundle(List<AutomationUiSceneDO> sceneList, HttpServletResponse response) {
        CheckUtils.throwIf(sceneList == null || sceneList.isEmpty(), "导出失败：未找到可导出场景");
        sceneList.sort(Comparator.comparing(AutomationUiSceneDO::getSceneId, Comparator.nullsLast(String::compareTo)));

        Long projectId = sceneList.get(0).getProjectId();
        Long versionId = sceneList.get(0).getVersionId();
        boolean sameProjectVersion = sceneList.stream()
            .allMatch(scene -> Objects.equals(projectId, scene.getProjectId()) && Objects.equals(versionId, scene
                .getVersionId()));
        CheckUtils.throwIf(!sameProjectVersion, "导出失败：场景必须属于同一项目和版本");

        ProjectConfigDO projectConfig = projectConfigMapper.selectById(projectId);
        ProjectVersionConfigDO versionConfig = projectVersionConfigMapper.selectById(versionId);
        CheckUtils.throwIfNull(projectConfig, "导出失败：项目配置不存在");
        CheckUtils.throwIfNull(versionConfig, "导出失败：版本配置不存在");

        AutomationUiSceneXmlUtils.BundleContext bundleContext = null;
        try {
            bundleContext = AutomationUiSceneXmlUtils.createBundle(sceneList, defaultString(projectConfig
                .getName()), defaultString(projectConfig.getAbbreviate()), defaultString(versionConfig
                    .getName()), "", "", "", "");
            String fileName = defaultString(projectConfig.getAbbreviate()) + "_" + AutomationUiSceneXmlUtils
                .sanitizePathSegment(defaultString(versionConfig.getName())) + "_scene_bundle.zip";
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
            AutomationUiSceneXmlUtils.writeZip(bundleContext.bundleRoot(), response.getOutputStream());
        } catch (Exception e) {
            throw new BusinessException("导出失败：生成场景 XML 压缩包失败：" + e.getMessage());
        } finally {
            if (bundleContext != null) {
                AutomationUiSceneXmlUtils.deleteQuietly(bundleContext.workspaceRoot());
            }
        }
    }

    /**
     * 根据回传参数定位场景。
     *
     * @param req 结果回传请求
     * @return 场景实体
     */
    private AutomationUiSceneDO resolveSceneForUpload(AutomationUiSceneUploadResultReq req) {
        if (req.getId() != null) {
            return baseMapper.selectById(req.getId());
        }
        CheckUtils.throwIf(StringUtils.isBlank(req.getSceneId()), "上传失败：sceneId 不能为空");
        return baseMapper.lambdaQuery()
            .eq(AutomationUiSceneDO::getSceneId, req.getSceneId())
            .eq(StringUtils.isNotBlank(req.getProjectName()), AutomationUiSceneDO::getProjectName, req.getProjectName())
            .eq(StringUtils.isNotBlank(req.getVersionName()), AutomationUiSceneDO::getVersionName, req.getVersionName())
            .last("LIMIT 1")
            .one();
    }

    /**
     * 为可能为空的数量字段提供默认值。
     *
     * @param value 原始数量
     * @return 非空数量
     */
    private Integer defaultNumber(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 解析脚本落盘目录。
     *
     * @param projectConfig 自动化项目配置
     * @return 脚本目录
     */
    private Path resolveSceneWorkspaceRoot(AutomationProjectConfigDO projectConfig) {
        String scriptPath = projectConfig == null ? null : projectConfig.getScriptPath();
        CheckUtils.throwIf(StringUtils.isBlank(scriptPath), "执行失败：项目脚本路径未配置");
        return Path.of(scriptPath.trim());
    }

    /**
     * 生成执行中的历史记录快照。
     *
     * @param scene         场景
     * @param buildNumber   Jenkins 构建号
     * @param consoleUrl    控制台地址
     * @param testReportUrl 报告地址
     * @param executeName   执行人
     * @param testPlanId    测试计划 ID
     * @param testReportId  测试报告 ID
     * @return 历史记录
     */
    private Map<String, Object> buildExecutingRecord(AutomationUiSceneDO scene,
                                                     Integer buildNumber,
                                                     String consoleUrl,
                                                     String testReportUrl,
                                                     String executeName,
                                                     String testPlanId,
                                                     String testReportId) {
        Map<String, Object> record = new HashMap<>(16);
        int caseTotal = scene.getCaseList() == null ? 0 : scene.getCaseList().size();
        int stepTotal = 0;
        if (scene.getCaseList() != null) {
            for (CaseDO caseDO : scene.getCaseList()) {
                if (caseDO != null && caseDO.getStepList() != null) {
                    stepTotal += caseDO.getStepList().size();
                }
            }
        }
        record.put("testPlanId", testPlanId);
        record.put("testReportId", testReportId);
        record.put("buildNumber", buildNumber);
        record.put("batchId", String.valueOf(buildNumber));
        record.put("executionType", "jenkins");
        record.put("executionId", String.valueOf(buildNumber));
        record.put("startedAt", java.time.OffsetDateTime.now().toString());
        record.put("finishedAt", null);
        record.put("artifactUrls", Map
            .of("console", defaultString(consoleUrl), "report", defaultString(testReportUrl)));
        record.put("consoleUrl", consoleUrl);
        record.put("testReportUrl", testReportUrl);
        record.put("executeName", executeName);
        record.put("executeStatus", STATUS_RUNNING);
        record.put("executeResult", RESULT_NOT_EXECUTED);
        record.put("duration", "-");
        record.put("scenePassRate", "-");
        record.put("caseTotal", caseTotal);
        record.put("casePass", 0);
        record.put("caseFail", 0);
        record.put("caseSkip", 0);
        record.put("stepTotal", stepTotal);
        record.put("stepPass", 0);
        record.put("stepFail", 0);
        record.put("stepSkip", 0);
        return record;
    }

    private String joinSceneIds(List<AutomationUiSceneDO> sceneList) {
        List<String> sceneIds = new ArrayList<>(sceneList.size());
        for (AutomationUiSceneDO scene : sceneList) {
            sceneIds.add(scene.getSceneId());
        }
        return String.join(",", sceneIds);
    }

    private AutomationNodeConfigDO resolveNode(List<AutomationNodeConfigDO> nodeList) {
        if (nodeList == null || nodeList.isEmpty()) {
            return null;
        }
        for (AutomationNodeConfigDO node : nodeList) {
            if (node == null || node.getActive() == null || node.getActive().getOffline() == null || node.getActive()
                .getIdle() == null) {
                continue;
            }
            boolean online = Objects.equals(5, node.getActive().getOffline().getStatus());
            boolean idle = Objects.equals(7, node.getActive().getIdle().getStatus());
            if (online && idle) {
                return node;
            }
        }
        return nodeList.get(0);
    }

    private String resolveJobName(AutomationJenkinsConfigDO jenkinsConfig) {
        if (jenkinsConfig == null || jenkinsConfig.getJobList() == null || jenkinsConfig.getJobList().isEmpty()) {
            return null;
        }
        Object first = jenkinsConfig.getJobList().get(0);
        if (first instanceof Map<?, ?> map) {
            Object name = map.get("name");
            if (name != null) {
                return name.toString();
            }
            Object label = map.get("label");
            if (label != null) {
                return label.toString();
            }
        }
        return BeanUtil.getProperty(first, "name");
    }

    private String normalizeUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String resolveServerConfigParam(ProjectServerConfigDO serverConfig, String paramName) {
        if (serverConfig == null || serverConfig.getConfigList() == null || serverConfig.getConfigList().isEmpty()) {
            return "";
        }
        for (Object item : serverConfig.getConfigList()) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object currentName = map.get("paramsName");
            if (currentName != null && Objects.equals(String.valueOf(currentName), paramName)) {
                Object currentValue = map.get("paramsValue");
                return currentValue == null ? "" : String.valueOf(currentValue);
            }
        }
        return "";
    }

    private AutomationProjectConfigDO resolveAutomationProjectConfig(List<?> source, ProjectConfigDO projectConfig) {
        if (source == null || source.isEmpty()) {
            return resolveAutomationProjectConfigFromTable(projectConfig);
        }
        List<AutomationProjectConfigDO> candidates = new ArrayList<>(source.size());
        for (Object item : source) {
            AutomationProjectConfigDO candidate = BeanUtil.toBean(item, AutomationProjectConfigDO.class);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return resolveAutomationProjectConfigFromTable(projectConfig);
        }

        if (projectConfig != null) {
            for (AutomationProjectConfigDO candidate : candidates) {
                if (candidate.getId() != null && Objects.equals(candidate.getId(), projectConfig.getId())) {
                    return withScriptPathFallback(candidate, projectConfig);
                }
            }
            String projectName = defaultString(projectConfig.getName());
            String projectAbbreviate = defaultString(projectConfig.getAbbreviate());
            for (AutomationProjectConfigDO candidate : candidates) {
                String candidateName = defaultString(candidate.getName());
                if (Objects.equals(candidateName, projectName) || Objects.equals(candidateName, projectAbbreviate)) {
                    return withScriptPathFallback(candidate, projectConfig);
                }
            }
        }

        for (AutomationProjectConfigDO candidate : candidates) {
            if (StringUtils.isNotBlank(candidate.getScriptPath())) {
                return candidate;
            }
        }
        return withScriptPathFallback(candidates.get(0), projectConfig);
    }

    private AutomationProjectConfigDO withScriptPathFallback(AutomationProjectConfigDO selected,
                                                             ProjectConfigDO projectConfig) {
        if (selected != null && StringUtils.isNotBlank(selected.getScriptPath())) {
            return selected;
        }
        AutomationProjectConfigDO dbConfig = resolveAutomationProjectConfigFromTable(projectConfig);
        if (dbConfig != null && StringUtils.isNotBlank(dbConfig.getScriptPath())) {
            return dbConfig;
        }
        return selected;
    }

    private AutomationProjectConfigDO resolveAutomationProjectConfigFromTable(ProjectConfigDO projectConfig) {
        if (projectConfig == null) {
            return null;
        }
        if (projectConfig.getId() != null) {
            AutomationProjectConfigDO byId = automationProjectConfigMapper.selectById(projectConfig.getId());
            if (byId != null) {
                return byId;
            }
        }
        String projectName = defaultString(projectConfig.getName());
        String projectAbbreviate = defaultString(projectConfig.getAbbreviate());
        List<AutomationProjectConfigDO> list = automationProjectConfigMapper.lambdaQuery()
            .eq(StringUtils.isNotBlank(projectName), AutomationProjectConfigDO::getName, projectName)
            .or(StringUtils.isNotBlank(projectAbbreviate))
            .eq(StringUtils.isNotBlank(projectAbbreviate), AutomationProjectConfigDO::getName, projectAbbreviate)
            .orderByDesc(AutomationProjectConfigDO::getId)
            .last("LIMIT 1")
            .list();
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private <T> T firstBean(List<?> source, Class<T> clazz) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return BeanUtil.toBean(source.get(0), clazz);
    }

    public void addStep1(CaseDO caseDO, Long id) {
        CheckUtils.throwIf(caseDO.getStep() == null, "step 不能为空");
        StepDO step = caseDO.getStep();
        step.setPid(caseDO.getId());
        step.setExpectedDefinitionVersion(caseDO.getExpectedDefinitionVersion());
        caseTreeService.addStep(id, step);
    }

    @Override
    public boolean isExists(Long id, Object... param) {
        return baseMapper.lambdaQuery()
            .eq(AutomationUiSceneDO::getProjectId, param[0])
            .eq(AutomationUiSceneDO::getVersionId, param[1])
            .eq(AutomationUiSceneDO::getSceneId, param[2])
            .eq(AutomationUiSceneDO::getDelFlag, 3)
            .ne(null != id, AutomationUiSceneDO::getId, id)
            .exists();
    }
}
