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

package top.continew.admin.test.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.continew.admin.schedule.service.JobService;
import top.continew.admin.test.model.resp.TestTimedTaskCapabilityResp;

import java.util.List;

@Slf4j
@Service
public class TestTimedTaskScheduleCapabilityService {

    private final JobService jobService;
    private final boolean clientEnabled;
    private final String groupName;

    public TestTimedTaskScheduleCapabilityService(JobService jobService,
                                                  @Value("${snail-job.enabled:true}") boolean clientEnabled,
                                                  @Value("${snail-job.group:continew-admin}") String groupName) {
        this.jobService = jobService;
        this.clientEnabled = clientEnabled;
        this.groupName = groupName;
    }

    public TestTimedTaskCapabilityResp probe() {
        TestTimedTaskCapabilityResp resp = new TestTimedTaskCapabilityResp();
        resp.setClientEnabled(clientEnabled);
        resp.setGroupName(groupName);
        if (!clientEnabled) {
            resp.setMessage("调度客户端未启用");
            return resp;
        }
        try {
            List<String> groups = jobService.listGroup();
            resp.setApiReachable(true);
            resp.setGroupAvailable(groups != null && groups.contains(groupName));
            resp.setReady(resp.isGroupAvailable());
            resp.setMessage(resp.isReady() ? "调度服务可用" : "调度分组不存在或未启用");
        } catch (Exception e) {
            log.warn("探测测试定时任务调度能力失败，groupName={}", groupName, e);
            resp.setMessage("调度中心连接失败");
        }
        return resp;
    }
}
