<script setup lang="ts">
import Pdf from 'pdf-vue3'

const route = useRoute()
const docId = computed(() => String(route.query.docId || ''))
const page = computed(() => Math.max(1, Number(route.query.page || 1)))

const cur = ref(page.value)
const numPages = ref(0)
const ready = ref(false)
const pdfSrc = computed(() => (docId.value ? fileUrl(docId.value) : ''))

watch(page, (v) => (cur.value = v))
watch(docId, () => {
  cur.value = 1
  ready.value = false
  numPages.value = 0
})

function onNumPages(n: number) {
  numPages.value = n
  ready.value = true
}

function go(delta: number) {
  const next = cur.value + delta
  if (next >= 1 && (numPages.value === 0 || next <= numPages.value)) cur.value = next
}
</script>

<template>
  <div class="mx-auto max-w-5xl">
    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <NuxtLink to="/" class="btn-outline !py-1.5 text-xs">← 返回问答</NuxtLink>
      <h1 class="text-sm font-semibold text-slate-700">PDF 预览</h1>
      <div class="flex items-center gap-1 text-xs">
        <button class="btn-outline !px-2 !py-1" :disabled="cur <= 1" @click="go(-1)">‹</button>
        <input v-model.number="cur" type="number" min="1" :max="numPages || undefined" class="w-14 text-center" />
        <span class="text-slate-400">/ {{ numPages || '?' }} 页</span>
        <button class="btn-outline !px-2 !py-1" :disabled="numPages > 0 && cur >= numPages" @click="go(1)">›</button>
      </div>
    </div>

    <div v-if="!docId" class="card py-16 text-center text-sm text-slate-400">缺少 docId 参数</div>
    <div v-else class="card overflow-hidden bg-slate-100 p-4">
      <div class="mx-auto min-h-[60vh] max-w-4xl bg-white shadow">
        <Pdf :src="pdfSrc" :page="cur" @num-pages="onNumPages" />
      </div>
      <div class="mt-3 text-center text-xs text-slate-400">
        当前已定位到第 {{ cur }} 页（点击问答中的引用会自动带页码跳转）
      </div>
    </div>
  </div>
</template>
