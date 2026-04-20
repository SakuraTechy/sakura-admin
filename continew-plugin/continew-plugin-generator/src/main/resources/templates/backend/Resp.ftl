package ${packageName}.${subPackageName};

import lombok.Data;

import java.io.Serial;
import java.util.List;

import cn.crane4j.annotation.Assemble;
import cn.crane4j.core.executor.handler.ManyToManyAssembleOperationHandler;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.constant.ContainerConstants;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;

<#if hasTimeField>
import java.time.*;
</#if>
<#if hasBigDecimalField>
import java.math.BigDecimal;
</#if>

/**
 * ${businessName}信息
 *
 * @author ${author}
 * @since ${datetime}
 */
@Data
@Schema(description = "${businessName}信息")
public class ${className} extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;
<#if fieldConfigs??>
  <#list fieldConfigs as fieldConfig>
    <#if fieldConfig.showInList>

    /**
     * ${fieldConfig.comment}
     */
    @Schema(description = "${fieldConfig.comment}")
    <#if fieldConfig.fieldType = 'List<Object>'>
    @Assemble(prop = ":${fieldConfig.fieldName}Names", container = ContainerConstants.USER_NICKNAME, handlerType = ManyToManyAssembleOperationHandler.class)
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName};
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName}Names;
    <#elseif fieldConfig.fieldName = 'status'>
    private StatusTypeEnum ${fieldConfig.fieldName};
    <#elseif fieldConfig.fieldName = 'delFlag'>
    private StatusTypeEnum ${fieldConfig.fieldName};
    <#else>
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName};
    </#if>
    </#if>
  </#list>
</#if>
}