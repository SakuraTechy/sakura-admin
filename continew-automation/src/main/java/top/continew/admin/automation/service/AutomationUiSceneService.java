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

package top.continew.admin.automation.service;

import java.util.Collection;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import top.continew.admin.automation.model.entity.ui.CaseDO;
import top.continew.admin.automation.model.entity.ui.StepDO;
import top.continew.starter.extension.crud.service.BaseService;
import top.continew.admin.automation.model.query.AutomationUiSceneQuery;
import top.continew.admin.automation.model.req.AutomationUiSceneClearReq;
import top.continew.admin.automation.model.req.AutomationUiSceneExecAllReq;
import top.continew.admin.automation.model.req.AutomationUiSceneExecReq;
import top.continew.admin.automation.model.req.AutomationUiSceneReq;
import top.continew.admin.automation.model.req.AutomationUiSceneUploadResultReq;
import top.continew.admin.automation.model.resp.AutomationUiSceneDetailResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneExecResp;
import top.continew.admin.automation.model.resp.AutomationUiSceneResp;

/**
 * 自动化管理-UI自动化场景业务接口
 *
 * @author hagyao520
 * @since 2025/06/13 11:49
 */
public interface AutomationUiSceneService extends BaseService<AutomationUiSceneResp, AutomationUiSceneDetailResp, AutomationUiSceneQuery, AutomationUiSceneReq> {
    /**
     * 根据 ID 查询
     *
     * @param ids ID 列表
     */
    List<AutomationUiSceneDetailResp> selectByIds(List<Long> ids);

    /**
     * 根据源场景ID，复制新测试场景
     *
     * @param sourceId 源场景 ID
     * @param req      新场景参数
     * @return 新场景 ID
     */
    Long copy(Long sourceId, AutomationUiSceneReq req);

    /**
     * 根据 ID 删除
     *
     * @param ids ID 列表
     */
    void deleteByIds(List<Long> ids);

    /**
     * 根据 场景ID 添加用例
     *
     * @param caseDO 场景用例
     * @param id     场景ID
     */
    void addCase(CaseDO caseDO, Long id);

    /**
     * 根据 场景ID 修改用例
     *
     * @param caseDO 场景用例
     * @param id     场景ID
     */
    void updateCase(CaseDO caseDO, Long id);

    /**
     * 根据 场景ID 删除用例
     *
     * @param caseDO 场景用例
     * @param id     场景ID
     */
    void deleteCase(CaseDO caseDO, Long id);

    /**
     * 根据 场景ID 拖拽用例
     *
     * @param caseDO 场景用例
     * @param id     场景ID
     */
    void dragCase(CaseDO caseDO, Long id);

    /**
     * 根据 场景ID 添加用例步骤
     *
     * @param stepDO 添加用例步骤
     * @param id     场景ID
     */
    String addStep(StepDO stepDO, Long id);

    /**
     * 根据 步骤ID 修改用例步骤
     *
     * @param stepDO 修改用例步骤
     * @param id     步骤ID
     */
    void updateStep(StepDO stepDO, Long id);

    /**
     * 根据 步骤ID 删除用例步骤
     *
     * @param stepDO 删除用例步骤
     * @param id     步骤ID
     */
    void deleteStep(StepDO stepDO, Long id);

    /**
     * 根据 步骤ID 拖拽用例步骤
     *
     * @param stepDO 拖拽用例步骤
     * @param id     步骤ID
     */
    void dragStep(StepDO stepDO, Long id);

    /**
     * 执行 UI 自动化场景
     *
     * @param req 执行参数
     * @return 执行结果
     */
    AutomationUiSceneExecResp exec(AutomationUiSceneExecReq req);

    /**
     * 执行查询范围内全部 UI 自动化场景
     *
     * @param req 执行全部参数
     * @return 执行结果
     */
    AutomationUiSceneExecResp execAll(AutomationUiSceneExecAllReq req);

    /**
     * 根据 ID 集合查询场景详情
     *
     * @param ids ID 集合
     * @return 场景详情
     */
    List<AutomationUiSceneResp> listSceneRespByIds(Collection<Long> ids);

    /**
     * 导出选中场景 XML
     *
     * @param ids      场景 ID 集合
     * @param response 响应流
     */
    void exportXml(Collection<Long> ids, HttpServletResponse response);

    /**
     * 导出查询范围内全部场景 XML
     *
     * @param query    查询条件
     * @param response 响应流
     */
    void exportXmlAll(AutomationUiSceneQuery query, HttpServletResponse response);

    /**
     * 清空测试场景结果
     * 
     * @param req
     */
    void clearResults(AutomationUiSceneClearReq req);

    /**
     * 上传测试场景结果
     * 
     * @param req
     */
    void uploadResults(AutomationUiSceneUploadResultReq req);

    /**
     * 根据参数条件，判断项目是否存在
     *
     * @param param 参数条件
     * @param id    ID
     * @return true：存在；false：不存在
     */
    boolean isExists(Long id, Object... param);
}
