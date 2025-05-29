package top.continew.admin.automation.model.entity;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import java.io.Serial;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.entity.BaseDO;
import top.continew.admin.common.enums.DisEnableStatusEnum;


/**
 * 自动化管理-节点配置实体
 *
 * @author hagyao520
 * @since 2025/05/20 11:21
 */
@Data
@TableName(value = "automation_node_config", autoResultMap = true)
public class AutomationNodeConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 节点序号
     */
    @TableField("`index`")
    private Integer index;

    /**
     * 所属Jenkins ID
     */
    private Long jenkinsId;

    /**
     * 节点名称
     */
    private String name;

    /**
     * 节点类型
     */
    private String type;

    /**
     * 节点配置json
     */
    private String json;

    /**
     * 节点配置xml
     */
    private String xml;

    /**
     * 节点地址
     */
    private String url;

    /**
     * 节点描述
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Description description;

    @Data
    public static class Description {
        private String name;
        private String systemType;
        private String userName;
        private String passWord;
        private String credentialsId;
    }

    /**
     * 节点环境状态
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Active active;

    @Data
    public static class Active {
        private Offline offline;
        private Idle idle;

        @Data
        public static class Offline {
            private Integer status;
            private String offlineCauseReason;
        }

        @Data
        public static class Idle {
            private Integer status;
            private List<CurrentExecutable> executors;
            private CurrentExecutable currentExecutable;

            @Data
            public static class CurrentExecutable {
                private String user;
                private String url;
            }
        }
    }

    /**
     * 节点在线状态
     */
    private StatusTypeEnum offlineStatus;

    /**
     * 节点使用状态
     */
    private StatusTypeEnum idleStatus;

    /**
     * 节点参数列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Config> configList;

    @Data
    public static class Config {
        private String paramsName;
        private String paramsValue;
    }

    /**
     * 状态
     */
    private StatusTypeEnum status;

    /**
     * 更新IP
     */
    private String updateIp;

    /**
     * 删除标志（3正常 4异常）
     */
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
