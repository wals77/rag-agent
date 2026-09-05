<script setup lang="ts">
import type { ChunkDto } from '~/types'

const props = defineProps<{ chunk: ChunkDto }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'confirm', pieces: string[]): void }>()

const pieces = ref<string[]>([])
const submitting = ref(false)

// 默认在句号处切为两段，便于演示
const seed = props.chunk.chunkText
const idx = seed.indexOf('。')
pieces.value = idx > 0
  ? [seed.slice(0, idx + 1).trim(), seed.slice(idx + 1).trim()]
  : [seed]

function add() { pieces.value.push('') }
function remove(i: number) { pieces.value.splice(i, 1) }

async function confirm() {
  submitting.value = true
  try { emit('confirm', pieces.value) } finally { submitting.value = false }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4" @click.self="emit('close')">
    <div class="flex max-h-[90vh] w-full max-w-3xl flex-col rounded-2xl bg-white p-5 shadow-xl">
      <div class="mb-3 flex items-center justify-between">
        <h3 class="text-base font-semibold">拆分为多个语义子块</h3>
        <button class="btn-ghost !px-2 !py-1" @click="emit('close')">✕</button>
      </div>
      <p class="mb-2 text-xs text-slate-400">{{ chunk.docName }} · 第{{ chunk.pageNum }}页 · 拆分后原块标记 split，子块 parent_chunk_id 指向原块</p>
      <div class="flex-1 space-y-3 overflow-y-auto pr-1">
        <div v-for="(p, i) in pieces" :key="i" class="flex items-start gap-2">
          <span class="mt-2 w-6 shrink-0 text-center text-xs font-bold text-slate-400">{{ i + 1 }}</span>
          <textarea v-model="pieces[i]" rows="4" class="input flex-1 font-mono text-xs leading-relaxed" :placeholder="'第 ' + (i + 1) + ' 个子块内容'"></textarea>
          <button class="btn-danger mt-1 !px-2 !py-1 text-xs" @click="remove(i)">删除</button>
        </div>
      </div>
      <div class="mt-4 flex items-center justify-between border-t pt-3">
        <button class="btn-outline text-xs" @click="add">+ 添加一段</button>
        <div class="flex gap-2">
          <button class="btn-outline" @click="emit('close')">取消</button>
          <button class="btn-primary" :disabled="pieces.length < 2 || pieces.some(p => !p.trim()) || submitting" @click="confirm">
            确认拆分 ({{ pieces.length }} 段)
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
