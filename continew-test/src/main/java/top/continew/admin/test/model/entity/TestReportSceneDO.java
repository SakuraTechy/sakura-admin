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

package top.continew.admin.test.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import top.continew.admin.common.model.entity.BaseCreateDO;

import java.io.Serial;

/**
 * 测试报告场景执行范围快照。
 */
@Data
@TableName("test_report_scene")
public class TestReportSceneDO extends BaseCreateDO {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long testReportId;
    private Long testPlanId;
    private Long projectId;
    private Long versionId;
    private Long moduleId;
    private Long sceneId;
    private String sceneKey;
    private String sceneName;
    private String sceneLevel;
    private Long definitionRevisionId;
    private Integer sort;
}
