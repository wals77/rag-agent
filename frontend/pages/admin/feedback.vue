<script setup lang="ts">
import type { FeedbackDto } from '~/types'
import { formatTime } from '~/utils/answer'

const items = ref<FeedbackDto[]>([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const loading = ref(false)
const toast = ref('')
const expanded = ref<number | null>(null)

const typeMeta: Record<string, { label: string; cls: string }> = {
  good_answer: { label: '👍 有帮助', cls: 'bg-emerald-50 text-emerald-600 ring-emerald-200' },
  bad_answer: { label: '👎 没帮助', cls: 'bg-orange-50 text-orange-600 ring-orange-200' },
  bad_split: { label: '分块不合理', cls: 'bg-amber-50 text-amber-600 ring-amber-200' },
  wrong_content: { label: '内容错误', cls: 'bg-red-50 text-red-600 ring-red-200' },
  missing_context: { label: '上下文缺失', cls: 'bg-sky-50 text-sky-600 ring-sky-200' }
}

async function load() {
  loading.value = true
  try {
    const res = await listFeedbacks(page.value, size.value)
    items.value = res.items
    total.value = res.total
  } catch (e: any) {
    toast.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="mb-4">
      <h1 class="text-lg font-semibold text-slate-800">反馈中心</h1>
      <p class="text-xs text-slate-500">收集问答/分块的用户反馈。wrong_content 附修正文本时会自动生成新的分块版本。</p>
    </div>

    <div v-if="toast" class="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700">{{ toast }}</div>

    <div class="card overflow-hidden">
      <table class="w-full text-left text-sm">
        <thead class="border-b border-slate-200 bg-slate-50 text-xs text-slate-500">
          <tr>
            <th class="px-4 py-3 font-medium">类型</th>
            <th class="px-4 py-3 font-medium">来源分块</th>
            <th class="px-4 py-3 font-medium">用户备注</th>
            <th class="px-4 py-3 font-medium">是否含修正文本</th>
            <th class="px-4 py-3 font-medium">反馈人</th>
            <th class="px-4 py-3 font-medium">时间</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr v-if="loading"><td colspan="6" class="px-4 py-10 text-center text-xs text-slate-400">加载中…</td></tr>
          <tr v-else-if="!items.length"><td colspan="6" class="px-4 py-10 text-center text-xs text-slate-400">暂无反馈</td></tr>
          <template v-for="f in items" :key="f.feedbackId">
            <tr class="cursor-pointer align-top hover:bg-slate-50/70" @click="expanded = expanded === f.feedbackId ? null : f.feedbackId">
              <td class="px-4 py-3">
                <span class="badge ring-1" :class="typeMeta[f.feedbackType]?.cls || 'bg-slate-100 text-slate-500'">
                  {{ typeMeta[f.feedbackType]?.label || f.feedbackType }}
                </span>
              </td>
              <td class="px-4 py-3">
                <p class="text-xs font-medium text-slate-600">{{ f.docName || '-' }}</p>
                <p class="text-[10px] text-slate-400">{{ f.chunkId }} · 第{{ f.pageNum || '?' }}页</p>
              </td>
              <td class="max-w-xs px-4 py-3 text-xs text-slate-600">{{ f.userComment || '—' }}</td>
              <td class="px-4 py-3 text-xs">
                <span v-if="f.correctedText" class="text-emerald-600">✔ 含修正文本</span>
                <span v-else class="text-slate-300">—</span>
              </td>
              <td class="px-4 py-3 text-xs text-slate-500">{{ f.userId || '匿名' }}</td>
              <td class="px-4 py-3 text-xs text-slate-500">{{ formatTime(f.createdAt) }}</td>
            </tr>
            <tr v-if="expanded === f.feedbackId">
              <td colspan="6" class="bg-slate-50/60 px-6 py-4">
                <div v-if="f.oldChunkText" class="mb-2">
                  <p class="mb-1 text-[11px] font-medium text-slate-400">原始分块内容：</p>
                  <pre class="whitespace-pre-wrap rounded bg-white p-3 text-xs leading-relaxed text-slate-600 ring-1 ring-slate-200">{{ f.oldChunkText }}</pre>
                </div>
                <div v-if="f.correctedText">
                  <p class="mb-1 text-[11px] font-medium text-emerald-500">用户修正文本：</p>
                  <pre class="whitespace-pre-wrap rounded bg-white p-3 text-xs leading-relaxed text-emerald-700 ring-1 ring-emerald-200">{{ f.correctedText }}</pre>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <div class="mt-3 flex items-center justify-between text-xs text-slate-500">
      <span>共 {{ total }} 条 · 第 {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span>
      <div class="flex gap-2">
        <button class="btn-outline !px-2 !py-1" :disabled="page === 0" @click="page--; load()">上一页</button>
        <button class="btn-outline !px-2 !py-1" :disabled="(page + 1) * size >= total" @click="page++; load()">下一页</button>
      </div>
    </div>
  </div>
</template>
