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

import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;
import top.continew.admin.automation.model.resp.environment.AutomationEnvironmentResourceResp;

/** 项目环境资源角色、绑定和执行期解析服务。 */
public interface AutomationEnvironmentResourceService {

    String SERVER = "SERVER";
    String DATABASE = "DATABASE";
    String CERTIFICATE = "CERTIFICATE";

    List<AutomationEnvironmentResourceResp> listSlots(Long projectId, String kind);

    List<AutomationEnvironmentResourceResp> listEnvironmentResources(Long environmentId);

    AutomationEnvironmentResourceResp bind(Long environmentId, Long slotId, Long resourceId);

    AutomationEnvironmentResourceResp uploadCertificate(Long environmentId, Long slotId, MultipartFile file);

    void unbind(Long environmentId, Long slotId);

    ResolvedResource resolve(Long environmentId, Long projectId, String expectedKind, Map<String, Object> resourceRef);

    record ResolvedResource(Long slotId, String resourceCode, String resourceKind, Long resourceId,
                            Integer bindingVersion) {
    }
}
