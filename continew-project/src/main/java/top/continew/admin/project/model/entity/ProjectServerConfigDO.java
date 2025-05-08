package top.continew.admin.project.model.entity;

import lombok.Data;
import java.io.Serial;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import top.continew.admin.common.model.entity.BaseDO;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.starter.security.crypto.annotation.FieldEncrypt;


/**
 * 项目管理-服务器配置实体
 *
 * @author hagyao520
 * @since 2025/05/06 15:09
 */
@Data
@TableName(value = "project_server_config", autoResultMap = true)
public class ProjectServerConfigDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属项目
     */
    private Long projectId;

    /**
     * 服务器类型
     */
    private String type;

    /**
     * 服务器版本
     */
    private String version;

    /**
     * 服务器IP
     */
    private String ip;

    /**
     * 服务器端口
     */
    private Long port;

    /**
     * 服务器用户名
     */
    private String userName;

    /**
     * 服务器密码
     */
    @FieldEncrypt
    private String passWord;

    /**
     * 服务器描述
     */
    private String description;

    /**
     * 服务器参数配置
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> configList;

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
