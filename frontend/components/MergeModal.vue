<script setup lang="ts">
import type { ChunkDto } from '~/types'

const props = defineProps<{ chunks: ChunkDto[] }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'confirm', mergedText: string): void }>()

const mergedText = ref(props.chunks.map(c => c.chunkText).join('\n'))
const submitting = ref(false)

async function confirm() {
  submitting.value = true
  try {
    emit('confirm', mergedText.value)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4" @click.self="emit('close')">
    <div class="w-full max-w-3xl rounded-2xl bg-white p-5 shadow-xl">
      <div class="mb-3 flex items-center justify-between">
        <h3 class="text-base font-semibold">合并分块（{{ chunks.length }} 个 → 1 个）</h3>
        <button class="btn-ghost !px-2 !py-1" @click="emit('close')">✕</button>
      </div>
      <p class="mb-2 text-xs text-slate-400">合并后将生成新分块，原分块状态变为 merged 并记录 merged_into_chunk_id</p>
      <div v-for="(c, i) in chunks" :key="c.chunkId" class="mb-1.5 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">
        <b>{{ i + 1 }}.</b> {{ c.docName }} · 第{{ c.pageNum }}页 · {{ c.chunkText.slice(0, 60) }}…
      </div>
      <textarea v-model="mergedText" rows="8" class="input mt-2 font-mono text-xs leading-relaxed"></textarea>
      <div class="mt-4 flex justify-end gap-2">
        <button class="btn-outline" @click="emit('close')">取消</button>
        <button class="btn-primary" :disabled="!mergedText.trim() || submitting" @click="confirm">确认合并</button>
      </div>
    </div>
  </div>
</template>
