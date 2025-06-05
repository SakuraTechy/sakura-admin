package ${packageName}.${subPackageName};

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.starter.data.core.annotation.Query;
import top.continew.starter.data.core.enums.QueryType;

<#if hasTimeField>
import java.time.*;
</#if>
<#if hasBigDecimalField>
import java.math.BigDecimal;
</#if>

/**
 * ${businessName}查询条件
 *
 * @author ${author}
 * @since ${datetime}
 */
@Data
@Schema(description = "${businessName}查询条件")
public class ${className} implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

<#if fieldConfigs??>
  <#list fieldConfigs as fieldConfig>
    <#if fieldConfig.showInQuery>

    /**
     * ${fieldConfig.comment}
     */
    @Schema(description = "${fieldConfig.comment}")
    @Query(type = QueryType.${fieldConfig.queryType})
    <#if fieldConfig.queryType = 'IN' || fieldConfig.queryType = 'NOT_IN' || fieldConfig.queryType = 'BETWEEN'>
    private ${fieldConfig.fieldType}[] ${fieldConfig.fieldName};
    <#elseif fieldConfig.fieldName = 'status'>
    private StatusTypeEnum ${fieldConfig.fieldName};
    <#elseif fieldConfig.fieldName = 'delFlag'>
    private StatusTypeEnum ${fieldConfig.fieldName} = StatusTypeEnum.NORMAL;
    <#else>
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName};
    </#if>
    </#if>
  </#list>
</#if>
}