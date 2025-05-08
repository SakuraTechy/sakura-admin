package ${packageName}.${subPackageName};

import lombok.Data;
import java.io.Serial;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import top.continew.admin.common.model.entity.BaseDO;
import top.continew.admin.common.enums.DisEnableStatusEnum;

<#if imports??>
    <#list imports as className>
import ${className};
    </#list>
</#if>
<#if hasTimeField>
import java.time.*;
</#if>
<#if hasBigDecimalField>
import java.math.BigDecimal;
</#if>

/**
 * ${businessName}实体
 *
 * @author ${author}
 * @since ${datetime}
 */
@Data
@TableName(value = "${tableName}", autoResultMap = true)
public class ${className} extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;
<#if fieldConfigs??>
  <#list fieldConfigs as fieldConfig>

    /**
     * ${fieldConfig.comment}
     */
    <#if fieldConfig.fieldType = 'List<Object>'>
    @TableField(typeHandler = JacksonTypeHandler.class)
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName};
    <#elseif fieldConfig.fieldName = 'status'>
    private DisEnableStatusEnum ${fieldConfig.fieldName};
    <#elseif fieldConfig.fieldName = 'delFlag'>
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName} = 1;
    <#else>
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName};
    </#if>
  </#list>
</#if>
}
