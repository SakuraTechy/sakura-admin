package top.continew.admin.project.model.entity;

import lombok.Data;
import java.io.Serial;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import top.continew.admin.common.model.entity.BaseDO;
import top.continew.admin.common.enums.DisEnableStatusEnum;

/**
 * 项目管理-版本配置实体
 *
 * @author hagyao520
 * @since 2025/04/28 15:33
 */
@Data
@TableName(value = "project_version_config", autoResultMap = true)
public class ProjectVersionConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    private Long projectId;

    /**
     * 版本名称
     */
    private String name;

    /**
     * 版本描述
     */
    private String description;

    /**
     * 状态
     */
    private DisEnableStatusEnum status;

    /**
     * 更新人IP
     */
    private String updateIp;

    /**
     * 删除标志（0删除 1存在）
     */
    private Integer delFlag = 1;
}
