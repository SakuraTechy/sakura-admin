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

package top.continew.admin.project.service.impl;

import com.alibaba.excel.annotation.ExcelProperty;
import org.junit.jupiter.api.Test;
import top.continew.admin.project.model.entity.ProjectDataBaseConfigDO;
import top.continew.admin.project.model.req.ProjectDataBaseConfigReq;
import top.continew.admin.project.model.resp.ProjectDataBaseConfigDetailResp;
import top.continew.admin.project.model.resp.ProjectDataBaseConfigResp;
import top.continew.admin.project.service.ProjectConfigService;
import top.continew.starter.security.crypto.annotation.FieldEncrypt;
import top.continew.starter.security.mask.annotation.JsonMask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProjectDataBaseCredentialProtectionTest {

    @Test
    void shouldDeclareEncryptionMaskingAndExportExclusion() throws Exception {
        assertThat(ProjectDataBaseConfigDO.class.getDeclaredField("passWord").isAnnotationPresent(FieldEncrypt.class))
            .isTrue();
        assertThat(ProjectDataBaseConfigResp.class.getDeclaredField("passWord").isAnnotationPresent(JsonMask.class))
            .isTrue();
        assertThat(ProjectDataBaseConfigDetailResp.class.getDeclaredField("passWord")
            .isAnnotationPresent(JsonMask.class)).isTrue();
        assertThat(ProjectDataBaseConfigDetailResp.class.getDeclaredField("passWord")
            .isAnnotationPresent(ExcelProperty.class)).isFalse();
    }

    @Test
    void shouldPreserveCurrentPasswordForMaskedOrBlankUpdate() {
        ProjectDataBaseConfigServiceImpl service = new ProjectDataBaseConfigServiceImpl(mock(ProjectConfigService.class));
        ProjectDataBaseConfigDO current = new ProjectDataBaseConfigDO();
        current.setPassWord("stored-secret");
        ProjectDataBaseConfigReq req = new ProjectDataBaseConfigReq();

        req.setPassWord("******");
        service.preserveMaskedPassword(req, current);
        assertThat(req.getPassWord()).isEqualTo("stored-secret");

        req.setPassWord(" ");
        service.preserveMaskedPassword(req, current);
        assertThat(req.getPassWord()).isEqualTo("stored-secret");

        req.setPassWord("replacement-secret");
        service.preserveMaskedPassword(req, current);
        assertThat(req.getPassWord()).isEqualTo("replacement-secret");
    }

    @Test
    void shouldKeepOnlyNonSensitiveEnvironmentDatabaseSnapshot() {
        ProjectDataBaseConfigDO source = new ProjectDataBaseConfigDO();
        source.setId(9L);
        source.setIp("127.0.0.1");
        source.setUserName("db-user");
        source.setPassWord("db-secret");
        source.setUrl("jdbc:mysql://127.0.0.1/db");
        source.setConfigList(java.util.List.of(java.util.Map.of("token", "secret")));

        ProjectDataBaseConfigDO snapshot = ProjectEnvironmentConfigServiceImpl.toEnvironmentDatabaseSnapshot(source);

        assertThat(snapshot.getId()).isEqualTo(9L);
        assertThat(snapshot.getIp()).isEqualTo("127.0.0.1");
        assertThat(snapshot.getUserName()).isNull();
        assertThat(snapshot.getPassWord()).isNull();
        assertThat(snapshot.getUrl()).isNull();
        assertThat(snapshot.getConfigList()).isNull();
    }
}
