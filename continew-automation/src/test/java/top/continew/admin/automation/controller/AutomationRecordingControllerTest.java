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

package top.continew.admin.automation.controller;

import java.lang.reflect.Method;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import org.junit.jupiter.api.Test;
import top.continew.admin.automation.model.req.recording.AutomationRecordingImportReq;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationRecordingControllerTest {

    @Test
    void importEndpointMustDeclareCreateOrUpdatePermission() throws NoSuchMethodException {
        Method method = AutomationRecordingController.class
            .getDeclaredMethod("importRecording", AutomationRecordingImportReq.class);
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.mode()).isEqualTo(SaMode.OR);
        assertThat(permission.value())
            .containsExactly("automation:automationUiScene:create", "automation:automationUiScene:update");
    }
}
