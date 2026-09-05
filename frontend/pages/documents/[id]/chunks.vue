<script setup lang="ts">
import type { DocumentDto } from '~/types'
import { formatTime } from '~/utils/answer'

const route = useRoute()
const docId = computed(() => String(route.params.id || ''))
const doc = ref<DocumentDto | null>(null)

async function loadDoc() {
  if (!docId.value) return
  try { doc.value = await getDocument(docId.value) } catch { /* ignore */ }
}
onMounted(loadDoc)
</script>

<template>
  <div>
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
      <div>
        <NuxtLink to="/documents" class="mb-1 inline-block text-xs text-brand-600 hover:underline">← 文档列表</NuxtLink>
        <h1 class="text-lg font-semibold text-slate-800">{{ doc?.docName || '分块详情' }}</h1>
        <p v-if="doc" class="text-xs text-slate-500">
          共 {{ doc.totalPages || 0 }} 页 · {{ formatTime(doc.uploadTime) }}
          <StatusBadge :status="doc.status" class="ml-2" />
        </p>
      </div>
      <NuxtLink v-if="doc?.docId" :to="`/pdf-viewer?docId=${doc.docId}&page=1`" class="btn-outline text-xs">打开 PDF 预览</NuxtLink>
    </div>
    <ChunkManager :doc-id-filter="docId" @mutated="loadDoc" />
  </div>
</template>
