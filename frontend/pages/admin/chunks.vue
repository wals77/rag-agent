<script setup lang="ts">
const defaultDocId = ref('')

onMounted(async () => {
  try {
    const d = await listDocuments()
    defaultDocId.value = d[0]?.docId || ''
  } catch { /* ignore */ }
})
</script>

<template>
  <div>
    <div class="mb-4">
      <h1 class="text-lg font-semibold text-slate-800">分块管理后台</h1>
      <p class="text-xs text-slate-500">查看/筛选全部分块，支持编辑（生成版本链）、合并、拆分与反馈，修改实时更新 ES 向量与检索结果。</p>
    </div>
    <ChunkManager :default-doc-id="defaultDocId" />
  </div>
</template>
