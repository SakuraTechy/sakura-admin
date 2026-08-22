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

package top.continew.admin.automation.model.req;

import top.continew.starter.extension.crud.model.query.PageQuery;

/**
 * UI 自动化轻量查询分页参数。
 *
 * <p>保留原始排序参数，由业务服务按接口白名单解析，避免通用排序器在白名单校验前构造 SQL 字段。</p>
 */
public class AutomationUiPageReq extends PageQuery {

    private String[] requestedSort;

    @Override
    public void setSort(String[] sort) {
        this.requestedSort = sort == null ? null : sort.clone();
    }

    public String[] getRequestedSort() {
        return requestedSort == null ? null : requestedSort.clone();
    }
}
