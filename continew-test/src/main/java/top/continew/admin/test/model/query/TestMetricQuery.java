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

package top.continew.admin.test.model.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class TestMetricQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private Long versionId;
    private Ui ui;

    /**
     * 兼容老项目测试度量接口的嵌套 UI 查询条件。
     */
    @Data
    public static class Ui implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long projectId;
        private Long versionId;
        private Long parentId;
    }
}
