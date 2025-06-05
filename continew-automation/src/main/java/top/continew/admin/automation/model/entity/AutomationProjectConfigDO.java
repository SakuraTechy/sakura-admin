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
 * 自动化管理-项目配置实体
 *
 * @author hagyao520
 * @since 2025/05/19 15:14
 */
@Data
@TableName(value = "automation_project_config", autoResultMap = true)
public class AutomationProjectConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目类型
     */
    private String type;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 项目地址
     */
    private String url;

    /**
     * 项目描述
     */
    private String description;

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
