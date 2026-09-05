<script setup lang="ts">
import type { DocumentDto } from '~/types'
import { formatSize, formatTime } from '~/utils/answer'

const docs = ref<DocumentDto[]>([])
const loading = ref(true)
const toast = ref('')

async function load() {
  loading.value = true
  try {
    docs.value = await listDocuments()
  } catch (e: any) {
    toast.value = e.message
  } finally {
    loading.value = false
  }
}

async function doReprocess(doc: DocumentDto) {
  if (!confirm(`确认重新解析并分块「${doc.docName}」？现有分块与向量将被清空。`)) return
  await reprocessDocument(doc.docId)
  toast.value = `已触发「${doc.docName}」重新处理`
  load()
}

async function doDelete(doc: DocumentDto) {
  if (!confirm(`确认删除文档「${doc.docName}」及其全部分块与向量？`)) return
  await deleteDocument(doc.docId)
  toast.value = `已删除「${doc.docName}」`
  load()
}

onMounted(() => {
  load()
  setInterval(async () => {
    if (docs.value.some(d => d.status === 'processing')) await load()
  }, 3000)
})

const hasProcessing = computed(() => docs.value.some(d => d.status === 'processing'))
</script>

<template>
  <div>
    <div class="mb-4 flex items-center justify-between">
      <div>
        <h1 class="text-lg font-semibold text-slate-800">文档管理</h1>
        <p class="text-xs text-slate-500">共 {{ docs.length }} 份文档{{ hasProcessing ? ' · 存在处理中的任务，列表自动刷新' : '' }}</p>
      </div>
      <div class="flex gap-2">
        <button class="btn-outline text-xs" :disabled="loading" @click="load">刷新</button>
        <NuxtLink to="/documents/upload" class="btn-primary text-xs">+ 上传文档</NuxtLink>
      </div>
    </div>

    <div v-if="toast" class="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700">{{ toast }}</div>

    <div class="card overflow-hidden">
      <table class="w-full text-left text-sm">
        <thead class="border-b border-slate-200 bg-slate-50 text-xs text-slate-500">
          <tr>
            <th class="px-4 py-3 font-medium">文件名</th>
            <th class="px-4 py-3 font-medium">大小</th>
            <th class="px-4 py-3 font-medium">页数</th>
            <th class="px-4 py-3 font-medium">状态</th>
            <th class="px-4 py-3 font-medium">上传时间</th>
            <th class="px-4 py-3 text-right font-medium">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr v-if="loading"><td colspan="6" class="px-4 py-10 text-center text-xs text-slate-400">加载中…</td></tr>
          <tr v-else-if="!docs.length"><td colspan="6" class="px-4 py-10 text-center text-xs text-slate-400">还没有文档，<NuxtLink to="/documents/upload" class="text-brand-600">去上传 →</NuxtLink></td></tr>
          <tr v-for="d in docs" :key="d.docId" class="hover:bg-slate-50/70">
            <td class="px-4 py-3">
              <NuxtLink :to="`/documents/${d.docId}/chunks`" class="font-medium text-slate-700 hover:text-brand-600">
                📄 {{ d.docName }}
              </NuxtLink>
            </td>
            <td class="px-4 py-3 text-xs text-slate-500">{{ formatSize(d.fileSize) }}</td>
            <td class="px-4 py-3 text-xs text-slate-500">{{ d.totalPages ?? '-' }} 页</td>
            <td class="px-4 py-3"><StatusBadge :status="d.status" /></td>
            <td class="px-4 py-3 text-xs text-slate-500">{{ formatTime(d.uploadTime) }}</td>
            <td class="px-4 py-3">
              <div class="flex justify-end gap-1">
                <NuxtLink v-if="d.status === 'done'" :to="`/pdf-viewer?docId=${d.docId}&page=1`" class="btn-ghost !px-2 !py-1 text-xs">预览</NuxtLink>
                <NuxtLink :to="`/documents/${d.docId}/chunks`" class="btn-ghost !px-2 !py-1 text-xs">分块</NuxtLink>
                <button v-if="d.status === 'failed' || d.status === 'done'" class="btn-ghost !px-2 !py-1 text-xs" @click="doReprocess(d)">重处理</button>
                <button class="btn-danger !px-2 !py-1 text-xs" @click="doDelete(d)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
