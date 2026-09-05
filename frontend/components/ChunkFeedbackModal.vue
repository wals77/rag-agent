<script setup lang="ts">
import type { ChunkDto } from '~/types'

const props = defineProps<{ chunk: ChunkDto }>()
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'confirm', payload: { type: string; comment: string; corrected?: string }): void
}>()

const type = ref<'bad_split' | 'wrong_content' | 'missing_context'>('bad_split')
const comment = ref('')
const corrected = ref('')
const submitting = ref(false)

const typeMeta = {
  bad_split: '分块切得不合理（语句被切断/主题混杂）',
  wrong_content: '内容错误，需要修正',
  missing_context: '上下文缺失，应补充周边内容'
} as Record<string, string>

async function confirm() {
  if (!comment.value.trim() && !corrected.value.trim()) return
  submitting.value = true
  try {
    emit('confirm', {
      type: type.value,
      comment: comment.value,
      corrected: corrected.value.trim() || undefined
    })
  } finally { submitting.value = false }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4" @click.self="emit('close')">
    <div class="w-full max-w-lg rounded-2xl bg-white p-5 shadow-xl">
      <div class="mb-3 flex items-center justify-between">
        <h3 class="text-base font-semibold">分块反馈</h3>
        <button class="btn-ghost !px-2 !py-1" @click="emit('close')">✕</button>
      </div>
      <p class="mb-3 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">{{ chunk.docName }} · 第{{ chunk.pageNum }}页</p>

      <div class="mb-3 space-y-2">
        <label v-for="(desc, k) in typeMeta" :key="k" class="flex cursor-pointer items-start gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs" :class="{ 'border-brand-500 bg-brand-50': type === k }">
          <input v-model="type" type="radio" :value="k" class="mt-0.5 accent-brand-600" />
          <span>
            <b>{{ k }}</b>
            <span class="block text-slate-400">{{ desc }}</span>
          </span>
        </label>
      </div>

      <label class="label">补充说明</label>
      <textarea v-model="comment" rows="2" class="input mb-3" placeholder="选填，说明问题细节…"></textarea>

      <label v-if="type === 'wrong_content'" class="label">修正后的文本（提交后自动生成 edited 新版本并替换检索）</label>
      <textarea v-if="type === 'wrong_content'" v-model="corrected" rows="4" class="input font-mono text-xs"></textarea>

      <div class="mt-4 flex justify-end gap-2">
        <button class="btn-outline" @click="emit('close')">取消</button>
        <button class="btn-primary" :disabled="(!comment.trim() && !corrected.trim()) || submitting" @click="confirm">提交</button>
      </div>
    </div>
  </div>
</template>
