package ${packageName}.${subPackageName};

import lombok.Data;

import java.util.List;
import java.io.Serial;
import java.io.Serializable;

<#if hasRequiredField>
import jakarta.validation.constraints.*;
</#if>

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import top.continew.admin.common.enums.DisEnableStatusEnum;

<#if hasTimeField>
import java.time.*;
</#if>
<#if hasBigDecimalField>
import java.math.BigDecimal;
</#if>

/**
 * 创建或修改${businessName}参数
 *
 * @author ${author}
 * @since ${datetime}
 */
@Data
@Schema(description = "创建或修改${businessName}参数")
public class ${className} implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
<#if fieldConfigs??>
  <#list fieldConfigs as fieldConfig>
    <#if fieldConfig.showInForm>

    /**
     * ${fieldConfig.comment}
     */
    @Schema(description = "${fieldConfig.comment}")
    <#if fieldConfig.isRequired>
    <#if fieldConfig.fieldType = 'String'>
    @NotBlank(message = "${fieldConfig.comment}不能为空")
    <#elseif fieldConfig.fieldType != 'String' && fieldConfig.fieldType != 'List<Object>'>
    @NotNull(message = "${fieldConfig.comment}不能为空")
    </#if>
    </#if>
    <#if fieldConfig.fieldType = 'String' && fieldConfig.columnSize??>
    @Length(max = ${fieldConfig.columnSize?c}, message = "${fieldConfig.comment}长度不能超过 {max} 个字符")
    </#if>
    <#if fieldConfig.fieldType = 'List<Object>'>
    @NotEmpty(message = "${fieldConfig.comment}不能为空")
    @Size(max = 10, message = "${fieldConfig.comment}最多支持 {max} 人")
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName};
    <#elseif fieldConfig.fieldName = 'status'>
    private DisEnableStatusEnum ${fieldConfig.fieldName};
    <#else>
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName};
    </#if>
    </#if>
    <#if fieldConfig.fieldName = 'delFlag'>

    /**
     * ${fieldConfig.comment}
     */
    @Schema(description = "${fieldConfig.comment}")
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName} = 1;
    </#if>
  </#list>
</#if>
}