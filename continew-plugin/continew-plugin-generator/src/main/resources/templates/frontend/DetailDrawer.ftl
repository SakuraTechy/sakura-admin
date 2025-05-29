<template>
  <a-drawer v-model:visible="visible" title="${businessName}详情" :width="width >= 800 ? 800 : '100%'" :footer="false">
    <a-descriptions :column="2" size="large" class="general-description">
      <#list fieldConfigs as fieldConfig>
      <a-descriptions-item label="${fieldConfig.comment}">{{ dataDetail?.${fieldConfig.fieldName} }}</a-descriptions-item>
      <#if fieldConfig.fieldType = 'List<Object>'>
      <a-descriptions-item label="${fieldConfig.comment}">
        <GiCellTags :data="dataDetail?.${fieldConfig.fieldName}Names || []" />
      </a-descriptions-item>
      <#elseif fieldConfig.fieldName = 'version'>
      <a-descriptions-item label="${fieldConfig.comment}">
        <GiCellVersion :version="dataDetail?.version ?? ''" />
      </a-descriptions-item>
      <#elseif fieldConfig.fieldName = 'passWord'>
      <a-descriptions-item label="${fieldConfig.comment}">
        <GiCellPassword :value="dataDetail?.passWord" />
      </a-descriptions-item>
      <#elseif fieldConfig.fieldName = 'status'>
<#--      <a-descriptions-item label="状态"><GiCellStatus :status="dataDetail?.status" /></a-descriptions-item>-->
      <a-descriptions-item label="状态">
<#--        <a-tag v-if="dataDetail?.status === 1" color="green">启用</a-tag>-->
<#--        <a-tag v-else color="red">禁用</a-tag>-->
        <GiCellTag :value="dataDetail?.status" :dict="status_type" />
      </a-descriptions-item>
      <#elseif fieldConfig.fieldName = 'createUser'>
      <a-descriptions-item label="创建人">{{ dataDetail?.createUserString }}</a-descriptions-item>
      <#elseif fieldConfig.fieldName = 'updateUser'>
      <a-descriptions-item label="修改人">{{ dataDetail?.updateUserString }}</a-descriptions-item>
      </#if>
      </#list>
    </a-descriptions>
  </a-drawer>
</template>

<script setup lang="ts">
import { useWindowSize } from '@vueuse/core'
import { type ${classNamePrefix}DetailResp, get${classNamePrefix} as getDetail } from '@/apis/${apiModuleName}/${apiName}'
import { useDict } from '@/hooks/app'

const { status_type } = useDict('status_type')

const { width } = useWindowSize()

const dataId = ref('')
const dataDetail = ref<${classNamePrefix}DetailResp>()
const visible = ref(false)

// 查询详情
const getDataDetail = async () => {
  const { data } = await getDetail(dataId.value)
  dataDetail.value = data
}

// 打开
const onOpen = async (id: string) => {
  dataId.value = id
  await getDataDetail()
  visible.value = true
}

defineExpose({ onOpen })
</script>

<script lang="ts">
export default {}
</script>

<style scoped lang="scss">
<#list fieldConfigs as fieldConfig>
<#if fieldConfig.fieldName = 'passWord'>
:deep(.gi-cell-key-value) {
  display: flex;
  align-items: center;
  justify-content: flex-start;
}
</#if>
</#list>
</style>
