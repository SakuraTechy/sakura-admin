package top.continew.admin.automation.model.resp;

import lombok.Data;

import java.io.Serial;
import java.util.List;

import cn.crane4j.annotation.Assemble;
import cn.crane4j.core.executor.handler.ManyToManyAssembleOperationHandler;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.constant.ContainerConstants;
import top.continew.admin.common.enums.DisEnableStatusEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;

import java.time.*;

/**
 * 自动化管理-Jenkins配置信息
 *
 * @author hagyao520
 * @since 2025/05/19 16:59
 */
@Data
@Schema(description = "自动化管理-Jenkins配置信息")
public class AutomationJenkinsConfigResp extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 版本
     */
    @Schema(description = "版本")
    private String version;

    /**
     * IP
     */
    @Schema(description = "IP")
    private String ip;

    /**
     * 端口
     */
    @Schema(description = "端口")
    private Integer port;

    /**
     * 用户名
     */
    @Schema(description = "用户名")
    private String userName;

    /**
     * 密码
     */
    @Schema(description = "密码")
    private String passWord;

    /**
     * 地址
     */
    @Schema(description = "地址")
    private String url;

    /**
     * 关联项目
     */
    @Schema(description = "关联项目")
    private List<Object> jobList;

    /**
     * 描述
     */
    @Schema(description = "描述")
    private String description;

    /**
     * 节点列表
     */
    @Schema(description = "节点列表")
    private List<Object> nodeList;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private DisEnableStatusEnum status;

    /**
     * 修改人
     */
    @Schema(description = "修改人")
    private Long updateUser;

    /**
     * 修改时间
     */
    @Schema(description = "修改时间")
    private LocalDateTime updateTime;
}