<script setup lang="ts">
const props = defineProps<{ loading?: boolean }>()
const emit = defineEmits<{ (e: 'submit', text: string): void }>()

const text = ref('')
const ta = ref<HTMLTextAreaElement>()

function submit() {
  const v = text.value.trim()
  if (!v || props.loading) return
  emit('submit', v)
  text.value = ''
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    submit()
  }
}

function adjust() {
  if (ta.value) {
    ta.value.style.height = 'auto'
    ta.value.style.height = Math.min(160, ta.value.scrollHeight) + 'px'
  }
}
</script>

<template>
  <div class="flex items-end gap-2 rounded-xl border border-slate-300 bg-white p-2 shadow-sm focus-within:border-brand-500 focus-within:ring-2 focus-within:ring-brand-100">
    <textarea
      ref="ta"
      v-model="text"
      rows="1"
      class="max-h-40 flex-1 resize-none bg-transparent px-2 py-2 text-sm outline-none placeholder:text-slate-400"
      placeholder="输入问题，例如：这份合同的违约金比例是多少？（Shift+Enter 换行，Enter 发送）"
      @keydown="onKeydown"
      @input="adjust"
    />
    <button class="btn-primary shrink-0" :disabled="loading || !text.trim()" @click="submit">
      {{ loading ? '思考中…' : '发送' }}
    </button>
  </div>
</template>
