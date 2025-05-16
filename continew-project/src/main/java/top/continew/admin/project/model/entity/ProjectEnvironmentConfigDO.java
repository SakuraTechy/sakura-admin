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
 * 项目管理-环境配置实体
 *
 * @author hagyao520
 * @since 2025/05/15 09:47
 */
@Data
@TableName(value = "project_environment_config", autoResultMap = true)
public class ProjectEnvironmentConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    private Long projectId;

    /**
     * 环境名称
     */
    private String name;

    /**
     * 环境描述
     */
    private String description;

    /**
     * 环境版本信息
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> versionConfig;

    /**
     * 环境服务器信息
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> serverConfig;

    /**
     * 环境数据库信息
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> dataBaseConfig;

    /**
     * 主线版本
     */
    private String lastVersion;

    /**
     * 环境域名
     */
    private String lastDomain;

    /**
     * 状态
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
    private Integer delFlag = 1;
}
