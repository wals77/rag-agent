<script setup lang="ts">
const props = defineProps<{ accept?: string; multiple?: boolean }>()
const emit = defineEmits<{ (e: 'files', files: File[]): void }>()

const dragOver = ref(false)
const inputEl = ref<HTMLInputElement>()
const ext = computed(() => (props.accept || '.pdf,.docx,.txt').split(',').map(x => x.trim()).join(' '))

function pick(files: FileList | null) {
  if (!files) return
  const arr = Array.from(files)
  if (arr.length) emit('files', arr)
  if (inputEl.value) inputEl.value.value = ''
}

function onDrop(e: DragEvent) {
  dragOver.value = false
  pick(e.dataTransfer?.files || null)
}
</script>

<template>
  <div
    class="flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-6 py-12 text-center transition"
    :class="dragOver ? 'border-brand-500 bg-brand-50' : 'border-slate-300 bg-white hover:border-brand-400 hover:bg-brand-50/40'"
    @click="inputEl?.click()"
    @dragover.prevent="dragOver = true"
    @dragleave.prevent="dragOver = false"
    @drop.prevent="onDrop"
  >
    <div class="mb-2 text-4xl">📤</div>
    <p class="text-sm font-medium text-slate-700">点击选择 或 拖拽文件到此处</p>
    <p class="mt-1 text-xs text-slate-400">支持 PDF（含扫描件 OCR）/ DOCX / TXT，单文件最大 200MB，上传后自动解析、智能分块并向量化</p>
    <input ref="inputEl" type="file" :accept="ext" :multiple="multiple" class="hidden" @change="e => pick((e.target as HTMLInputElement).files)" />
  </div>
</template>
