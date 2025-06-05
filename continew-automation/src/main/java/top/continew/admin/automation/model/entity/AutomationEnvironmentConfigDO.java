package top.continew.admin.automation.model.entity;

import com.baomidou.mybatisplus.extension.handlers.FastjsonTypeHandler;
import lombok.Data;
import java.io.Serial;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import top.continew.admin.common.model.entity.BaseDO;
import top.continew.admin.common.enums.StatusTypeEnum;


/**
 * 自动化管理-环境配置实体
 *
 * @author hagyao520
 * @since 2025/05/29 17:41
 */
@Data
@TableName(value = "automation_environment_config", autoResultMap = true)
public class AutomationEnvironmentConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 环境类型
     */
    private String type;

    /**
     * 环境名称
     */
    private String name;

    /**
     * 环境描述
     */
    private String description;

    /**
     * 环境项目信息
     */
    @TableField(typeHandler = FastjsonTypeHandler.class)
    private List<AutomationProjectConfigDO> projectConfig;

    /**
     * 环境Jenkins信息
     */
    @TableField(typeHandler = FastjsonTypeHandler.class)
    private List<AutomationJenkinsConfigDO> jenkinsConfig;

    /**
     * 环境节点信息
     */
    @TableField(typeHandler = FastjsonTypeHandler.class)
    private List<AutomationNodeConfigDO> nodeConfig;

    /**
     * 环境浏览器信息
     */
    @TableField(typeHandler = FastjsonTypeHandler.class)
    private List<AutomationBrowserConfigDO> browserConfig;

    /**
     * 状态
     */
    private StatusTypeEnum status;

    /**
     * 创建部门
     */
    private Long deptId;

    /**
     * 更新IP
     */
    private String updateIp;

    /**
     * 备注
     */
    private String remark;

    /**
     * 版本
     */
    private String version;

    /**
     * 删除标志（3正常 4异常）
     */
    private StatusTypeEnum delFlag = StatusTypeEnum.NORMAL;
}
