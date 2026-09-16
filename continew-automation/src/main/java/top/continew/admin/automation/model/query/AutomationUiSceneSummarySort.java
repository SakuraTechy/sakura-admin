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

package top.continew.admin.automation.model.query;

/** UI 自动化场景摘要排序条件。 */
public final class AutomationUiSceneSummarySort {

    private final String field;
    private final boolean ascending;

    public AutomationUiSceneSummarySort(String field, boolean ascending) {
        this.field = field;
        this.ascending = ascending;
    }

    public String getField() {
        return field;
    }

    public boolean isAscending() {
        return ascending;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AutomationUiSceneSummarySort that)) {
            return false;
        }
        return ascending == that.ascending && field.equals(that.field);
    }

    @Override
    public int hashCode() {
        return 31 * field.hashCode() + Boolean.hashCode(ascending);
    }
}
