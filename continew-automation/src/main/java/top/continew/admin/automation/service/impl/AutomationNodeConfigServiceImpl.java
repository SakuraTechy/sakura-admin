package top.continew.admin.automation.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import top.continew.admin.automation.model.resp.AutomationJenkinsConfigDetailResp;
import top.continew.admin.automation.service.AutomationJenkinsConfigService;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.jenkins.JenkinsService;
import top.continew.admin.common.json.JsonUtils;
import top.continew.admin.common.util.StringUtils;
import top.continew.starter.extension.crud.service.BaseServiceImpl;
import top.continew.admin.common.context.UserContextHolder;
import top.continew.admin.automation.mapper.AutomationNodeConfigMapper;
import top.continew.admin.automation.model.entity.AutomationNodeConfigDO;
import top.continew.admin.automation.model.query.AutomationNodeConfigQuery;
import top.continew.admin.automation.model.req.AutomationNodeConfigReq;
import top.continew.admin.automation.model.resp.AutomationNodeConfigDetailResp;
import top.continew.admin.automation.model.resp.AutomationNodeConfigResp;
import top.continew.admin.automation.service.AutomationNodeConfigService;

/**
 * 自动化管理-节点配置业务实现
 *
 * @author hagyao520
 * @since 2025/05/20 11:21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationNodeConfigServiceImpl extends BaseServiceImpl<AutomationNodeConfigMapper, AutomationNodeConfigDO, AutomationNodeConfigResp, AutomationNodeConfigDetailResp, AutomationNodeConfigQuery, AutomationNodeConfigReq> implements AutomationNodeConfigService {

    private final AutomationJenkinsConfigService automationJenkinsConfigService;

    @Override
    public List<AutomationNodeConfigDetailResp> selectByIds(List<Long> ids) {
        List<AutomationNodeConfigDetailResp> list = BeanUtil.copyToList(baseMapper.selectByIds(ids), AutomationNodeConfigDetailResp.class);
        list.forEach(item -> {
            item.setJenkinsName(automationJenkinsConfigService.get(item.getJenkinsId()).getIp());
            item.setCreateUserString(UserContextHolder.getNickname(item.getCreateUser()));
            item.setUpdateUserString(UserContextHolder.getNickname(item.getUpdateUser()));
        });
        return list;
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        baseMapper.deleteByIds(ids);
    }

    @Override
    public boolean isExists(Long id, Object... param) {
        return baseMapper.lambdaQuery()
                .eq(AutomationNodeConfigDO::getName, param[0])
                .eq(AutomationNodeConfigDO::getDelFlag, 3)
                .ne(null != id, AutomationNodeConfigDO::getId, id)
                .exists();
    }

    @Override
    public boolean syncAllNode(Long jenkinsId) {
        AutomationJenkinsConfigDetailResp jenkinsConfigDetailResp = automationJenkinsConfigService.get(jenkinsId);
        String jenkinsUrl = jenkinsConfigDetailResp.getUrl();
        String jenkinsUserName = jenkinsConfigDetailResp.getUserName();
        String jenkinsPassWord = jenkinsConfigDetailResp.getPassWord();

        try {
            JsonNode jsonNode = JenkinsService.getJenkinsNodeAll(jenkinsUrl, jenkinsUserName, jenkinsPassWord);
            List<AutomationNodeConfigDO> automationNodeConfigDOList = baseMapper.lambdaQuery()
                    .eq(AutomationNodeConfigDO::getJenkinsId, jenkinsId)
                    .eq(AutomationNodeConfigDO::getDelFlag, 3)
                    .list();
            if (StringUtils.isNotEmpty(automationNodeConfigDOList) && jsonNode.get("computer").size() == automationNodeConfigDOList.size()) {
                for (JsonNode computer : jsonNode.get("computer")) {
                    final String[] displayName = {computer.path("displayName").asText()};
                    try {
                        automationNodeConfigDOList.forEach((automationNodeConfigDO) -> {
                            if ("Built-In Node".equals(displayName[0])) {
                                displayName[0] = "(master)";
                            }
                            if (automationNodeConfigDO.getName().equals(displayName[0])) {
                                automationNodeConfigDO.setUrl(jenkinsUrl + "/computer/" + displayName[0] + "/");
                                String descriptionStr = computer.path("description").asText();
                                AutomationNodeConfigDO.Description description = new AutomationNodeConfigDO.Description();
                                if (JsonUtils.isJson(descriptionStr)) {
                                    description = JSON.parseObject(descriptionStr, AutomationNodeConfigDO.Description.class);
                                } else {
                                    description.setName("Jenkins节点描述非JSON格式，请检查配置！");
                                }
//                                automationNodeConfigDO.setDescription(description);
                                AutomationNodeConfigDO.Active active = new AutomationNodeConfigDO.Active();
                                AutomationNodeConfigDO.Active.Offline offline = new AutomationNodeConfigDO.Active.Offline();
                                boolean offlineStatus = computer.get("offline").asBoolean();
                                offline.setStatus(offlineStatus ? 6 : 5);
                                automationNodeConfigDO.setOfflineStatus(offlineStatus ? StatusTypeEnum.OFFLINE : StatusTypeEnum.ONLINE);
                                offline.setOfflineCauseReason(computer.path("offlineCauseReason").asText());
                                active.setOffline(offline);
                                AutomationNodeConfigDO.Active.Idle idle = new AutomationNodeConfigDO.Active.Idle();
                                boolean idleStatus = computer.get("idle").asBoolean();
                                idle.setStatus(idleStatus ? 7 : 8);
                                automationNodeConfigDO.setIdleStatus(idleStatus ? StatusTypeEnum.IDLE : StatusTypeEnum.IN_USE);
                                if (!idleStatus) {
                                    List<AutomationNodeConfigDO.Active.Idle.CurrentExecutable> currentExecutableList = new ArrayList<>();
//                            List<AutomationNodeConfigDO.Active.Idle.CurrentExecutable> currentExecutableList = automationNodeConfigDO.getActive().getIdle().getCurrentExecutable();
                                    for (JsonNode executors : computer.get("executors")) {
                                        JsonNode currentExecutableJn = executors.get("currentExecutable");
                                        if (!currentExecutableJn.isEmpty()) {
                                            String url = currentExecutableJn.path("url").asText();
                                            if (StringUtils.isNotEmpty(url)) {
                                                AutomationNodeConfigDO.Active.Idle.CurrentExecutable currentExecutable = new AutomationNodeConfigDO.Active.Idle.CurrentExecutable();
                                                currentExecutable.setUrl(url);
                                                currentExecutable.setUser(JenkinsService.getJenkinsJobParameters(url, jenkinsUserName, jenkinsPassWord));
                                                currentExecutableList.add(currentExecutable);
                                            }
                                        }
                                    }
                                    idle.setExecutors(currentExecutableList);
                                }
                                active.setIdle(idle);
                                automationNodeConfigDO.setActive(active);

                                List<AutomationNodeConfigDO.Config> configList = automationNodeConfigDO.getConfigList();
                                Response response = JenkinsService.getJenkinsNodeDetails(jenkinsUrl, jenkinsUserName, jenkinsPassWord, displayName[0]);
                                JsonNode rootNode = JenkinsService.parseXmlToJson(response.xmlPath());
                                configList.stream().filter(o -> "workDirPath".equals(o.getParamsName())).forEach(f -> {
                                    f.setParamsValue(rootNode.get("slave").path("remoteFS").asText());
                                });
                                JsonNode credentialsId = rootNode.get("slave").get("launcher").get("credentialsId");
                                if (credentialsId != null && !credentialsId.isMissingNode()) {
                                    description.setSystemType("Linux");
                                    description.setCredentialsId(jenkinsUrl+"/credentials/store/system/domain/_/credential/"+credentialsId.asText());
                                    automationNodeConfigDO.setType("Linux");
                                } else {
                                    description.setSystemType("Windows");
                                    automationNodeConfigDO.setType("Windows");
                                }
                                automationNodeConfigDO.setDescription(description);
//                                automationNodeConfigDO.setJson(rootNode.toString());
                                JsonNode toolLocationNode = rootNode.get("slave").get("nodeProperties").get("hudson.tools.ToolLocationNodeProperty").get("locations").get("hudson.tools.ToolLocationNodeProperty_-ToolLocation");
                                for (JsonNode tools : toolLocationNode) {
                                    configList.stream().filter(o -> o.getParamsName().equals(tools.path("name").asText())).forEach(f -> {
                                        f.setParamsValue(tools.path("home").asText());
                                    });
                                }

                                automationNodeConfigDO.setConfigList(configList);
//                                automationNodeConfigDO.setStatus(0);
//                                automationNodeConfigDO.setCreateUserString(UserContextHolder.getNickname(automationNodeConfigDO.getCreateUser()));
//                                automationNodeConfigDO.setCreateTime(DateUtils.getTime());
//                                automationNodeConfigDO.setUpdateUserString(UserContextHolder.getNickname(automationNodeConfigDO.getCreateUser()));
//                                automationNodeConfigDO.setUpdateTime(DateUtils.getTime());
                            }
                            baseMapper.updateById(automationNodeConfigDO);
                        });
                    } catch (Exception e) {
                        log.error("更新节点配置失败，跳过该节点: {}", displayName[0], e);
                    }
                }
            } else {
                int index = 1;
                for (JsonNode computer : jsonNode.get("computer")) {
                    String displayName = computer.path("displayName").asText();
                    try {
                        AutomationNodeConfigDO automationNodeConfigDO = new AutomationNodeConfigDO();
                        automationNodeConfigDO.setIndex(index);
//                        automationNodeConfigDO.setId(Long.valueOf(IdUtils.randomUUID()));
                        log.info("开始同步节点：{}", displayName);
                        if ("Built-In Node".equals(displayName)) {
                            displayName = "(master)";
                        }
                        automationNodeConfigDO.setName(displayName);
                        automationNodeConfigDO.setUrl(jenkinsUrl + "/computer/" + displayName + "/");
                        String descriptionStr = computer.path("description").asText();
                        AutomationNodeConfigDO.Description description = new AutomationNodeConfigDO.Description();
                        if (JsonUtils.isJson(descriptionStr)) {
                            description = JSON.parseObject(descriptionStr, AutomationNodeConfigDO.Description.class);
                        } else {
                            description.setName("Jenkins节点描述非JSON格式，请检查配置！");
                        }
//                        automationNodeConfigDO.setDescription(description);
                        AutomationNodeConfigDO.Active active = new AutomationNodeConfigDO.Active();
                        AutomationNodeConfigDO.Active.Offline offline = new AutomationNodeConfigDO.Active.Offline();
                        boolean offlineStatus = computer.get("offline").asBoolean();
                        offline.setStatus(offlineStatus ? 6 : 5);
                        automationNodeConfigDO.setOfflineStatus(offlineStatus ? StatusTypeEnum.OFFLINE : StatusTypeEnum.ONLINE);
                        offline.setOfflineCauseReason(computer.path("offlineCauseReason").asText());
                        active.setOffline(offline);
                        AutomationNodeConfigDO.Active.Idle idle = new AutomationNodeConfigDO.Active.Idle();
                        boolean idleStatus = computer.get("idle").asBoolean();
                        idle.setStatus(idleStatus ? 7 : 8);
                        automationNodeConfigDO.setIdleStatus(idleStatus ? StatusTypeEnum.IDLE : StatusTypeEnum.IN_USE);
                        if (!idleStatus) {
                            List<AutomationNodeConfigDO.Active.Idle.CurrentExecutable> currentExecutableList = new ArrayList<>();
                            for (JsonNode executors : computer.get("executors")) {
                                JsonNode currentExecutableJn = executors.get("currentExecutable");
                                if (!currentExecutableJn.isEmpty()) {
                                    String url = currentExecutableJn.path("url").asText();
                                    if (StringUtils.isNotEmpty(url)) {
                                        AutomationNodeConfigDO.Active.Idle.CurrentExecutable currentExecutable = new AutomationNodeConfigDO.Active.Idle.CurrentExecutable();
                                        currentExecutable.setUrl(url);
                                        currentExecutable.setUser(JenkinsService.getJenkinsJobParameters(url, jenkinsUserName, jenkinsPassWord));
                                        currentExecutableList.add(currentExecutable);
                                    }
                                }
                            }
                            idle.setExecutors(currentExecutableList);
                        }
                        active.setIdle(idle);
                        automationNodeConfigDO.setActive(active);

                        List<AutomationNodeConfigDO.Config> configList = new ArrayList<>();
                        AutomationNodeConfigDO.Config config = new AutomationNodeConfigDO.Config();
                        Response response = JenkinsService.getJenkinsNodeDetails(jenkinsUrl, jenkinsUserName, jenkinsPassWord, displayName);
                        JsonNode rootNode = JenkinsService.parseXmlToJson(response.xmlPath());
                        if (rootNode != null) {
                            config.setParamsName("workDirPath");
                            config.setParamsValue(rootNode.get("slave").path("remoteFS").asText());
                            configList.add(config);
                            JsonNode toolLocationNode = rootNode.get("slave").get("nodeProperties").get("hudson.tools.ToolLocationNodeProperty").get("locations").get("hudson.tools.ToolLocationNodeProperty_-ToolLocation");
                            for (JsonNode tools : toolLocationNode) {
                                config = new AutomationNodeConfigDO.Config();
                                config.setParamsName(tools.path("name").asText());
                                config.setParamsValue(tools.path("home").asText());
                                configList.add(config);
                            }
                            JsonNode credentialsId = rootNode.get("slave").get("launcher").get("credentialsId");
                            if (credentialsId != null && !credentialsId.isMissingNode()) {
                                description.setSystemType("Linux");
                                description.setCredentialsId(jenkinsUrl+"/credentials/store/system/domain/_/credential/"+credentialsId.asText());
                                automationNodeConfigDO.setType("Linux");
                            } else {
                                description.setSystemType("Windows");
                                automationNodeConfigDO.setType("Windows");
                            }
                            automationNodeConfigDO.setDescription(description);
//                            automationNodeConfigDO.setJson(rootNode.toString());
                        }
                        automationNodeConfigDO.setConfigList(configList);
                        automationNodeConfigDO.setStatus(StatusTypeEnum.ENABLE);
                        automationNodeConfigDO.setJenkinsId(jenkinsId);
//                        automationNodeConfigDO.setCreateByName(SecurityUtils.getLoginUser().getUser().getName());
//                        automationNodeConfigDO.setCreateTime(DateUtils.getTime());
//                        automationNodeConfigDO.setUpdateByName(SecurityUtils.getLoginUser().getUser().getName());
//                        automationNodeConfigDO.setUpdateTime(DateUtils.getTime());
                        index++;
                        baseMapper.insert(automationNodeConfigDO);
                    } catch (Exception e) {
                        log.error("插入节点配置失败，跳过该节点: {}", displayName, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("同步Jenkins节点配置失败", e);
            return false;
        }
        return true;
    }

    @Override
    public boolean syncNode(List<Long> ids) {
        try {
            List<AutomationNodeConfigDO> list = BeanUtil.copyToList(baseMapper.selectByIds(ids), AutomationNodeConfigDO.class);
            for (AutomationNodeConfigDO item : list) {
                AutomationJenkinsConfigDetailResp jenkinsConfigDetailResp = automationJenkinsConfigService.get(item.getJenkinsId());
                String jenkinsUrl = jenkinsConfigDetailResp.getUrl();
                String jenkinsUserName = jenkinsConfigDetailResp.getUserName();
                String jenkinsPassWord = jenkinsConfigDetailResp.getPassWord();

                JsonNode computer = JenkinsService.getJenkinsNode(jenkinsUrl, jenkinsUserName, jenkinsPassWord, item.getName());
                final String[] displayName = {computer.path("displayName").asText()};
                if (item.getName().equals(displayName[0])) {
                    try {
                        if ("Built-In Node".equals(displayName[0])) {
                            displayName[0] = "(master)";
                        }
                        if (item.getName().equals(displayName[0])) {
                            item.setUrl(jenkinsUrl + "/computer/" + displayName[0] + "/");
                            String descriptionStr = computer.path("description").asText();
                            AutomationNodeConfigDO.Description description = new AutomationNodeConfigDO.Description();
                            if (JsonUtils.isJson(descriptionStr)) {
                                description = JSON.parseObject(descriptionStr, AutomationNodeConfigDO.Description.class);
                            } else {
                                description.setName("Jenkins节点描述非JSON格式，请检查配置！");
                            }
                            AutomationNodeConfigDO.Active active = new AutomationNodeConfigDO.Active();
                            AutomationNodeConfigDO.Active.Offline offline = new AutomationNodeConfigDO.Active.Offline();
                            boolean offlineStatus = computer.get("offline").asBoolean();
                            offline.setStatus(offlineStatus ? 6 : 5);
                            item.setOfflineStatus(offlineStatus ? StatusTypeEnum.OFFLINE : StatusTypeEnum.ONLINE);
                            offline.setOfflineCauseReason(computer.path("offlineCauseReason").asText());
                            active.setOffline(offline);
                            AutomationNodeConfigDO.Active.Idle idle = new AutomationNodeConfigDO.Active.Idle();
                            boolean idleStatus = computer.get("idle").asBoolean();
                            idle.setStatus(idleStatus ? 7 : 8);
                            item.setIdleStatus(idleStatus ? StatusTypeEnum.IDLE : StatusTypeEnum.IN_USE);
                            if (!idleStatus) {
                                List<AutomationNodeConfigDO.Active.Idle.CurrentExecutable> currentExecutableList = new ArrayList<>();
//                            List<AutomationNodeConfigDO.Active.Idle.CurrentExecutable> currentExecutableList = item.getActive().getIdle().getCurrentExecutable();
                                for (JsonNode executors : computer.get("executors")) {
                                    JsonNode currentExecutableJn = executors.get("currentExecutable");
                                    if (!currentExecutableJn.isEmpty()) {
                                        String url = currentExecutableJn.path("url").asText();
                                        if (StringUtils.isNotEmpty(url)) {
                                            AutomationNodeConfigDO.Active.Idle.CurrentExecutable currentExecutable = new AutomationNodeConfigDO.Active.Idle.CurrentExecutable();
                                            currentExecutable.setUrl(url);
                                            currentExecutable.setUser(JenkinsService.getJenkinsJobParameters(url, jenkinsUserName, jenkinsPassWord));
                                            currentExecutableList.add(currentExecutable);
                                        }
                                    }
                                }
                                idle.setExecutors(currentExecutableList);
                            }
                            active.setIdle(idle);
                            item.setActive(active);

                            List<AutomationNodeConfigDO.Config> configList = item.getConfigList();
                            Response response = JenkinsService.getJenkinsNodeDetails(jenkinsUrl, jenkinsUserName, jenkinsPassWord, displayName[0]);
                            item.setXml(response.asString());
                            JsonNode rootNode = JenkinsService.parseXmlToJson(response.xmlPath());
                            configList.stream().filter(o -> "workDirPath".equals(o.getParamsName())).forEach(f -> {
                                f.setParamsValue(rootNode.get("slave").path("remoteFS").asText());
                            });
                            try {
                                JsonNode credentialsId = rootNode.get("slave").get("launcher").get("credentialsId");
                                if (credentialsId != null && !credentialsId.isMissingNode()) {
                                    description.setSystemType("Linux");
                                    description.setCredentialsId(jenkinsUrl+"/credentials/store/system/domain/_/credential/"+credentialsId.asText());
                                    item.setType("Linux");
                                } else {
                                    description.setSystemType("Windows");
                                    item.setType("Windows");
                                }
                            } catch (Exception e){
                                log.error("获取节点描述失败，跳过该节点: {}", displayName[0], e);
                            }
                            item.setDescription(description);
                            JsonNode toolLocationNode = rootNode.get("slave").get("nodeProperties").get("hudson.tools.ToolLocationNodeProperty").get("locations").get("hudson.tools.ToolLocationNodeProperty_-ToolLocation");
                            for (JsonNode tools : toolLocationNode) {
                                configList.stream().filter(o -> o.getParamsName().equals(tools.path("name").asText())).forEach(f -> {
                                    f.setParamsValue(tools.path("home").asText());
                                });
                            }
                            item.setConfigList(configList);
                        }
                        baseMapper.updateById(item);
                    } catch (Exception e) {
                        log.error("更新节点配置失败，跳过该节点: {}", displayName[0], e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("同步Jenkins节点配置失败", e);
            return false;
        }
        return true;
    }

    @Override
    public boolean addNode(AutomationNodeConfigDO automationNodeConfigDO) {
        try {
            Long jenkinsId = automationNodeConfigDO.getJenkinsId();
            AutomationJenkinsConfigDetailResp jenkinsConfigDetailResp = automationJenkinsConfigService.get(jenkinsId);
            String jenkinsUrl = jenkinsConfigDetailResp.getUrl();
            String jenkinsUserName = jenkinsConfigDetailResp.getUserName();
            String jenkinsPassWord = jenkinsConfigDetailResp.getPassWord();

            String nodeName = automationNodeConfigDO.getName();
            if(JenkinsService.addJenkinsNode(jenkinsUrl, jenkinsUserName, jenkinsPassWord, nodeName, automationNodeConfigDO.getType(), automationNodeConfigDO.getJson())){
                JsonNode computer = JenkinsService.getJenkinsNode(jenkinsUrl, jenkinsUserName, jenkinsPassWord, nodeName);
                automationNodeConfigDO.setUrl(jenkinsUrl + "/computer/" + nodeName + "/");
                String descriptionStr = computer.path("description").asText();
                AutomationNodeConfigDO.Description description = new AutomationNodeConfigDO.Description();
                if (JsonUtils.isJson(descriptionStr)) {
                    description = JSON.parseObject(descriptionStr, AutomationNodeConfigDO.Description.class);
                } else {
                    description.setName("Jenkins节点描述非JSON格式，请检查配置！");
                }
                automationNodeConfigDO.setType(description.getSystemType());
                AutomationNodeConfigDO.Active active = new AutomationNodeConfigDO.Active();
                AutomationNodeConfigDO.Active.Offline offline = new AutomationNodeConfigDO.Active.Offline();
                boolean offlineStatus = computer.get("offline").asBoolean();
                offline.setStatus(offlineStatus ? 6 : 5);
                automationNodeConfigDO.setOfflineStatus(offlineStatus ? StatusTypeEnum.OFFLINE : StatusTypeEnum.ONLINE);
                offline.setOfflineCauseReason(computer.path("offlineCauseReason").asText());
                active.setOffline(offline);
                AutomationNodeConfigDO.Active.Idle idle = new AutomationNodeConfigDO.Active.Idle();
                boolean idleStatus = computer.get("idle").asBoolean();
                idle.setStatus(idleStatus ? 7 : 8);
                automationNodeConfigDO.setIdleStatus(idleStatus ? StatusTypeEnum.IDLE : StatusTypeEnum.IN_USE);
                if (!idleStatus) {
                    List<AutomationNodeConfigDO.Active.Idle.CurrentExecutable> currentExecutableList = new ArrayList<>();
                    for (JsonNode executors : computer.get("executors")) {
                        JsonNode currentExecutableJn = executors.get("currentExecutable");
                        if (!currentExecutableJn.isEmpty()) {
                            String url = currentExecutableJn.path("url").asText();
                            if (StringUtils.isNotEmpty(url)) {
                                AutomationNodeConfigDO.Active.Idle.CurrentExecutable currentExecutable = new AutomationNodeConfigDO.Active.Idle.CurrentExecutable();
                                currentExecutable.setUrl(url);
                                currentExecutable.setUser(JenkinsService.getJenkinsJobParameters(url, jenkinsUserName, jenkinsPassWord));
                                currentExecutableList.add(currentExecutable);
                            }
                        }
                    }
                    idle.setExecutors(currentExecutableList);
                }
                active.setIdle(idle);
                automationNodeConfigDO.setActive(active);

                List<AutomationNodeConfigDO.Config> configList = new ArrayList<>();
                AutomationNodeConfigDO.Config config = new AutomationNodeConfigDO.Config();
                Response response = JenkinsService.getJenkinsNodeDetails(jenkinsUrl, jenkinsUserName, jenkinsPassWord, nodeName);
                JsonNode rootNode = JenkinsService.parseXmlToJson(response.xmlPath());
                try {
                    JsonNode credentialsId = rootNode.get("slave").get("launcher").get("credentialsId");
                    if (credentialsId != null && !credentialsId.isMissingNode()) {
                        description.setSystemType("Linux");
                        description.setCredentialsId(jenkinsUrl+"/credentials/store/system/domain/_/credential/"+credentialsId.asText());
                        automationNodeConfigDO.setType("Linux");
                    } else {
                        description.setSystemType("Windows");
                        automationNodeConfigDO.setType("Windows");
                    }
                } catch (Exception e){
                    log.error("获取节点描述失败，跳过该节点: {}", nodeName, e);
                }
                automationNodeConfigDO.setDescription(description);
                if (rootNode != null) {
                    config.setParamsName("workDirPath");
                    config.setParamsValue(rootNode.get("slave").path("remoteFS").asText());
                    configList.add(config);
                    JsonNode toolLocationNode = rootNode.get("slave").get("nodeProperties").get("hudson.tools.ToolLocationNodeProperty").get("locations").get("hudson.tools.ToolLocationNodeProperty_-ToolLocation");
                    for (JsonNode tools : toolLocationNode) {
                        config = new AutomationNodeConfigDO.Config();
                        config.setParamsName(tools.path("name").asText());
                        config.setParamsValue(tools.path("home").asText());
                        configList.add(config);
                    }
                }
                automationNodeConfigDO.setConfigList(configList);
                automationNodeConfigDO.setStatus(StatusTypeEnum.ENABLE);
                automationNodeConfigDO.setJenkinsId(jenkinsId);
                baseMapper.insert(automationNodeConfigDO);
                return true;
            }
        } catch (Exception e) {
            log.error("添加Jenkins节点配置失败", e);
        }
        return false;
    }

    @Override
    public boolean updateNode(AutomationNodeConfigDO automationNodeConfigDO) {
        try {
            Long jenkinsId = automationNodeConfigDO.getJenkinsId();
            AutomationJenkinsConfigDetailResp jenkinsConfigDetailResp = automationJenkinsConfigService.get(jenkinsId);
            String jenkinsUrl = jenkinsConfigDetailResp.getUrl();
            String jenkinsUserName = jenkinsConfigDetailResp.getUserName();
            String jenkinsPassWord = jenkinsConfigDetailResp.getPassWord();

            String nodeName = automationNodeConfigDO.getName();
            String json = automationNodeConfigDO.getJson();
            if(JenkinsService.updateJenkinsNode(jenkinsUrl, jenkinsUserName, jenkinsPassWord, nodeName, json)){
                return syncNode(Collections.singletonList(automationNodeConfigDO.getId()));
            }
        } catch (Exception e) {
            log.error("更新Jenkins节点配置失败", e);
        }
        return false;
    }

    @Override
    public boolean delNode(AutomationNodeConfigDetailResp automationNodeConfigDetailResp) {
        try {
            Long jenkinsId = automationNodeConfigDetailResp.getJenkinsId();
            AutomationJenkinsConfigDetailResp jenkinsConfigDetailResp = automationJenkinsConfigService.get(jenkinsId);
            String jenkinsUrl = jenkinsConfigDetailResp.getUrl();
            String jenkinsUserName = jenkinsConfigDetailResp.getUserName();
            String jenkinsPassWord = jenkinsConfigDetailResp.getPassWord();

            String nodeName = automationNodeConfigDetailResp.getName();
            return JenkinsService.delJenkinsNode(jenkinsUrl, jenkinsUserName, jenkinsPassWord, nodeName);
        } catch (Exception e) {
            log.error("添加Jenkins节点配置失败", e);
        }
        return false;
    }
}