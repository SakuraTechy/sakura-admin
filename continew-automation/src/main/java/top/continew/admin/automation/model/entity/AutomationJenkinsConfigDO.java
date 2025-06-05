package top.continew.admin.automation.model.entity;

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
 * 自动化管理-Jenkins配置实体
 *
 * @author hagyao520
 * @since 2025/05/19 16:59
 */
@Data
@TableName(value = "automation_jenkins_config", autoResultMap = true)
public class AutomationJenkinsConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 版本
     */
    private String version;

    /**
     * IP
     */
    private String ip;

    /**
     * 端口
     */
    private Integer port;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 密码
     */
    private String passWord;

    /**
     * 地址
     */
    private String url;

    /**
     * 关联项目
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> jobList;

    /**
     * 描述
     */
    private String description;

    /**
     * 节点列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> nodeList;

    /**
     * 状态
     */
    private StatusTypeEnum status;

    /**
     * 更新人IP
     */
    private String updateIp;

    /**
     * 删除标志（3正常 4异常）
     */
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
