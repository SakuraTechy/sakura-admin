<template>
  <div class="gi_table_page">
    <GiTable
      title="${businessName}管理"
      ref="tableRef"
      row-key="id"
      v-model:selectedKeys="selectedKeys"
      :data="dataList"
      :columns="columns"
      :loading="loading"
      :scroll="{ x: '100%', y: '100%', minWidth: 1000 }"
      :pagination="pagination"
      :disabled-tools="['size']"
      :disabled-column-keys="['name']"
      :row-selection="{ type: 'checkbox', showCheckedAll: true }"
      :show-selection-alert="true"
      :selection-message="`已选中 <#noparse>${selectedKeys.length}</#noparse> 条记录(可跨页)`"
      no-selection-message="未选中任何记录"
      @select="select"
      @select-all="selectAll"
      @refresh="search"
    >
      <#-- 查询字段配置 -->
      <template #toolbar-left>
      <#list fieldConfigs as fieldConfig>
      <#if fieldConfig.showInQuery>
	  <#if fieldConfig.formType == "SELECT"><#-- 下拉框 -->
        <a-select
          v-model="queryForm.${fieldConfig.fieldName}"
          :options="${fieldConfig.dictCode!"default_value"}"
          placeholder="请选择${fieldConfig.comment}"
          allow-clear
          style="width: 150px"
          @change="search"
        />
      <#elseif fieldConfig.formType == "SWITCH"><#-- 开关 -->
        <a-select
          v-model="queryForm.${fieldConfig.fieldName}"
          :options="${fieldConfig.dictCode!"default_value"}"
          placeholder="请选择${fieldConfig.comment}"
          allow-clear
          style="width: 150px"
          @change="search"
        />
	  <#elseif fieldConfig.formType == "RADIO"><#-- 单选框 -->
		<a-radio-group v-model="queryForm.${fieldConfig.fieldName}" :options="${fieldConfig.dictCode!'dictKey 或者自定义数组'}" @change="search"/>
	  <#elseif fieldConfig.formType == "DATE"><#-- 日期框 -->
        <#if fieldConfig.queryType == "BETWEEN">
        <DateRangePicker v-model="queryForm.${fieldConfig.fieldName}" format="YYYY-MM-DD" @change="search" />
        <#else>
        <a-date-picker
          v-model="queryForm.${fieldConfig.fieldName}"
          placeholder="请选择${fieldConfig.comment}"
          format="YYYY-MM-DD"
          style="height: 32px"
        />
        </#if>
      <#elseif fieldConfig.formType == "DATE_TIME"><#-- 日期时间框 -->
        <#if fieldConfig.queryType == "BETWEEN">
        <DateRangePicker v-model="queryForm.${fieldConfig.fieldName}" @change="search" />
        <#else>
        <a-date-picker
          v-model="queryForm.${fieldConfig.fieldName}"
          placeholder="请选择${fieldConfig.comment}"
          show-time
          format="YYYY-MM-DD HH:mm:ss"
          style="height: 32px"
        />
        </#if>
	  <#else>
	    <a-input-search v-model="queryForm.${fieldConfig.fieldName}" placeholder="请输入${fieldConfig.comment}" allow-clear @search="search" />
      </#if>
      </#if>
      </#list>
        <a-button @click="reset">
          <template #icon><icon-refresh /></template>
          <template #default>重置</template>
        </a-button>
      </template>
      <template #toolbar-right>
        <a-button v-permission="['${apiModuleName}:${apiName}:create']" type="primary" @click="onAdd">
          <template #icon><icon-plus /></template>
          <template #default>新增</template>
        </a-button>
        <a-button v-permission="['${apiModuleName}:${apiName}:export']" @click="onExport">
          <template #icon><icon-download /></template>
          <template #default>导出</template>
        </a-button>
      </template>
      <template #status="{ record }">
        <GiCellStatus :status="record.status" />
      </template>
      <#list fieldConfigs as fieldConfig>
      <#if fieldConfig.showInList>
      <#if fieldConfig.dictCode?? && fieldConfig.dictCode != "">
      <template #${fieldConfig.fieldName}="{ record }">
        <GiCellTag :value="record.${fieldConfig.fieldName}" :dict="${fieldConfig.dictCode}" />
      </template>
      </#if>
      </#if>
      </#list>
      <template #action="{ record }">
        <a-space>
          <a-link v-permission="['${apiModuleName}:${apiName}:get']" title="详情" @click="onDetail(record)">详情</a-link>
          <a-link v-permission="['${apiModuleName}:${apiName}:update']" title="修改" @click="onUpdate(record)">修改</a-link>
          <a-link
            v-permission="['${apiModuleName}:${apiName}:delete']"
            status="danger"
            :disabled="record.disabled"
            :title="record.disabled ? '禁止删除' : '删除'"
            @click="onDelete(record)"
          >
            删除
          </a-link>
        </a-space>
      </template>
    </GiTable>

    <${classNamePrefix}AddModal ref="${classNamePrefix}AddModalRef" @save-success="search" />
    <${classNamePrefix}DetailDrawer ref="${classNamePrefix}DetailDrawerRef" />
  </div>
</template>

<script setup lang="ts">
import type { TableInstance } from '@arco-design/web-vue'
import ${classNamePrefix}AddModal from './${classNamePrefix}AddModal.vue'
import ${classNamePrefix}DetailDrawer from './${classNamePrefix}DetailDrawer.vue'
import { type ${classNamePrefix}Resp, type ${classNamePrefix}Query, delete${classNamePrefix}, export${classNamePrefix}, list${classNamePrefix} } from '@/apis/${apiModuleName}/${apiName}'
import { useDownload, useTable } from '@/hooks'
import { useDict } from '@/hooks/app'
import { isMobile } from '@/utils'
import has from '@/utils/has'

defineOptions({ name: '${classNamePrefix}' })

<#if hasDictField>
const { <#list dictCodes as dictCode>${dictCode}<#if dictCode_has_next>,</#if></#list> } = useDict(<#list dictCodes as dictCode>'${dictCode}'<#if dictCode_has_next>,</#if></#list>)
</#if>

const queryForm = reactive<${classNamePrefix}Query>({
<#list fieldConfigs as fieldConfig>
<#if fieldConfig.showInQuery>
  ${fieldConfig.fieldName}: undefined,
</#if>
</#list>
  sort: ['id,desc']
})

const {
  tableData: dataList,
  loading,
  pagination,
  search,
  select,
  selectAll,
  selectedKeys,
  handleDelete
} = useTable((page) => list${classNamePrefix}({ ...queryForm, ...page }), { immediate: true })
const columns: TableInstance['columns'] = [
<#if fieldConfigs??>
  <#list fieldConfigs as fieldConfig>
  <#if fieldConfig.showInList>
    <#if fieldConfig.fieldName=="createUser" >
    { title: '${fieldConfig.comment}', dataIndex: 'createUserString', slotName: '${fieldConfig.fieldName}', width: 120, ellipsis: true, tooltip: true },
    <#elseif fieldConfig.fieldName=="updateUser"  >
    { title: '${fieldConfig.comment}', dataIndex: 'updateUserString', slotName: '${fieldConfig.fieldName}', width: 120, ellipsis: true, tooltip: true },
    <#elseif fieldConfig.fieldName=="createTime"  >
    { title: '${fieldConfig.comment}', dataIndex: 'createTime', slotName: '${fieldConfig.fieldName}', width: 180, ellipsis: true, tooltip: true },
    <#elseif fieldConfig.fieldName=="updateTime"  >
    { title: '${fieldConfig.comment}', dataIndex: 'updateTime', slotName: '${fieldConfig.fieldName}', width: 180, ellipsis: true, tooltip: true },
    <#else>
    { title: '${fieldConfig.comment}', dataIndex: '${fieldConfig.fieldName}', slotName: '${fieldConfig.fieldName}', width: 120, ellipsis: true, tooltip: true },
  </#if>
</#if>
</#list>
</#if>
  {
    title: '操作',
    dataIndex: 'action',
    slotName: 'action',
    width: 160,
    align: 'center',
    fixed: !isMobile() ? 'right' : undefined,
    show: has.hasPermOr(['${apiModuleName}:${apiName}:get', '${apiModuleName}:${apiName}:update', '${apiModuleName}:${apiName}:delete'])
  }
]

// 表格引用
const tableRef = ref()

// 重置
const reset = () => {
<#list fieldConfigs as fieldConfig>
<#if fieldConfig.showInQuery>
  queryForm.${fieldConfig.fieldName} = undefined
</#if>
</#list>
  search()
}

// 删除
const onDelete = (record: ${classNamePrefix}Resp) => {
  return handleDelete(() => delete${classNamePrefix}(record.id), {
    content: `是否确定删除该条数据？`,
    showModal: true
  })
}

// 导出
const onExport = () => {
  useDownload(() => export${classNamePrefix}(queryForm))
}

// 组件引用类型
interface ${classNamePrefix}AddModalType {
  onAdd: () => void
  onUpdate: (id: string) => void
}
interface ${classNamePrefix}DetailDrawerType {
  onOpen: (id: string) => void
}

const ${classNamePrefix}AddModalRef = ref<${classNamePrefix}AddModalType>()
// 新增
const onAdd = () => {
  ${classNamePrefix}AddModalRef.value?.onAdd()
}

// 修改
const onUpdate = (record: ${classNamePrefix}Resp) => {
  ${classNamePrefix}AddModalRef.value?.onUpdate(record.id)
}

const ${classNamePrefix}DetailDrawerRef = ref<${classNamePrefix}DetailDrawerType>()
// 详情
const onDetail = (record: ${classNamePrefix}Resp) => {
  ${classNamePrefix}DetailDrawerRef.value?.onOpen(record.id)
}
</script>

<style scoped lang="scss"></style>
