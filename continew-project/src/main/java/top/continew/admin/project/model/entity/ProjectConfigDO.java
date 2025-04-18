package top.continew.admin.project.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableName;

import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.model.entity.BaseDO;


import java.io.Serial;
import java.util.List;

/**
 * 项目配置实体
 *
 * @author hagyao520
 * @since 2025/04/15 11:56
 */
@Data
@TableName(value = "project_config", autoResultMap = true)
public class ProjectConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 项目简称
     */
    private String abbreviate;

    /**
     * 项目成员
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> members;

    /**
     * 项目描述
     */
    private String description;

    /**
     * 项目域名
     */
    private String lastDomain;

    /**
     * 主线版本
     */
    private String lastVersion;

    /**
     * 状态（0禁用 1启用）
     */
    private DisEnableStatusEnum status;

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
     * 删除标志（0删除 1存在）
     */
    private Integer delFlag;
}
