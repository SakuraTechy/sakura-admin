package ${packageName}.${subPackageName};

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;

import cn.crane4j.annotation.Assemble;
import cn.crane4j.core.executor.handler.ManyToManyAssembleOperationHandler;

import top.continew.admin.common.constant.ContainerConstants;
import top.continew.admin.common.enums.StatusTypeEnum;
import top.continew.admin.common.model.resp.BaseDetailResp;
import top.continew.starter.file.excel.converter.ExcelBaseEnumConverter;
import top.continew.starter.file.excel.converter.ExcelListConverter;

<#if hasTimeField>
import java.time.*;
</#if>
<#if hasBigDecimalField>
import java.math.BigDecimal;
</#if>

/**
 * ${businessName}详情信息
 *
 * @author ${author}
 * @since ${datetime}
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "${businessName}详情信息")
public class ${className} extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;
<#if fieldConfigs??>
  <#list fieldConfigs as fieldConfig>
    <#assign orderNumber = fieldConfig_index + 2>

    /**
     * ${fieldConfig.comment}
     */
    @Schema(description = "${fieldConfig.comment}")
    <#if fieldConfig.fieldType = 'List<Object>'>
    @Assemble(prop = ":${fieldConfig.fieldName}Names", container = ContainerConstants.USER_NICKNAME, handlerType = ManyToManyAssembleOperationHandler.class)
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName};

    @ExcelProperty(value = "${fieldConfig.comment}", converter = ExcelListConverter.class, order = ${orderNumber})
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName}Names;
    <#elseif fieldConfig.fieldName = 'status' ||  fieldConfig.fieldName = 'delFlag'>
    @ExcelProperty(value = "${fieldConfig.comment}", converter = ExcelBaseEnumConverter.class, order = ${orderNumber})
    private StatusTypeEnum ${fieldConfig.fieldName};
    <#else>
    @ExcelProperty(value = "${fieldConfig.comment}", order = ${orderNumber})
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName};
    </#if>
  </#list>
</#if>
}