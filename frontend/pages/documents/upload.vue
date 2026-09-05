<script setup lang="ts">
import type { DocumentDto } from '~/types'

const uploading = ref(false)
const queue = ref<{ name: string; status: string; docId?: string; error?: string }[]>([])
const toast = ref('')

async function onFiles(files: File[]) {
  uploading.value = true
  for (const file of files) {
    const entry = { name: file.name, status: '上传中…' }
    queue.value.push(entry)
    try {
      const doc = await uploadDocument(file)
      entry.docId = doc.docId
      entry.status = '解析中…'
      poll(doc, entry)
    } catch (e: any) {
      entry.status = 'failed'
      entry.error = e.message
    }
  }
  uploading.value = false
}

function poll(doc: DocumentDto, entry: { status: string; docId?: string; error?: string }) {
  let n = 0
  const timer = setInterval(async () => {
    n++
    if (n > 120) {
      clearInterval(timer)
      return
    }
    try {
      const cur = await getDocument(doc.docId)
      entry.status = cur.status
      if (cur.status === 'done' || cur.status === 'failed') {
        clearInterval(timer)
        if (cur.status === 'done') toast.value = `「${cur.docName}」处理完成，共 ${cur.totalPages} 页`
      }
    } catch (e: any) {
      entry.error = e.message
      clearInterval(timer)
    }
  }, 2000)
}
</script>

<template>
  <div class="mx-auto max-w-3xl">
    <h1 class="mb-1 text-lg font-semibold text-slate-800">上传文档</h1>
    <p class="mb-4 text-xs text-slate-500">支持批量；上传后进入“解析 → 规则粗切(父块) → LLM语义分块(Qwen2.5) → 向量化入库(ES+MySQL)”流水线。</p>

    <DocUpload accept=".pdf,.docx,.txt" :multiple="true" @files="onFiles" />

    <div v-if="queue.length" class="card mt-4 divide-y divide-slate-100">
      <div v-for="(q, i) in queue" :key="i" class="flex items-center gap-3 px-4 py-3">
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm font-medium text-slate-700">{{ q.name }}</p>
          <p v-if="q.error" class="text-xs text-red-500">{{ q.error }}</p>
        </div>
        <StatusBadge v-if="q.status === 'done' || q.status === 'failed'" :status="q.status" />
        <span v-else class="badge bg-sky-50 text-sky-600 ring-1 ring-sky-200">
          <span class="mr-1 inline-block h-1.5 w-1.5 animate-spin rounded-full border border-sky-500 border-t-transparent"></span>{{ q.status }}
        </span>
        <NuxtLink v-if="q.status === 'done'" :to="`/documents/${q.docId}/chunks`" class="btn-outline !py-1 text-xs">查看分块</NuxtLink>
      </div>
    </div>

    <div class="mt-4 flex justify-between text-xs text-slate-400">
      <NuxtLink to="/documents" class="text-brand-600 hover:underline">← 返回文档列表</NuxtLink>
    </div>
    <div v-if="toast" class="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-xs text-emerald-700">{{ toast }}</div>
  </div>
</template>
