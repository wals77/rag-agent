<script setup lang="ts">
import Pdf from 'pdf-vue3'
import type { DocumentDto } from '~/types'

const route = useRoute()
const docId = computed(() => String(route.query.docId || ''))
const page = computed(() => Math.max(1, Number(route.query.page || 1)))
const section = computed(() => String(route.query.section || ''))

const cur = ref(page.value)
const numPages = ref(0)
const ready = ref(false)
const pdfSrc = computed(() => (docId.value ? fileUrl(docId.value) : ''))

const docInfo = ref<DocumentDto | null>(null)
const contentLoading = ref(false)
const loadError = ref('')

type PreviewType = 'pdf' | 'md' | 'text' | 'docx' | 'unsupported'
const previewType = computed<PreviewType>(() => {
  const n = (docInfo.value?.docName || '').toLowerCase()
  if (!n) return 'pdf'
  if (/\.md$/.test(n) || /\.markdown$/.test(n)) return 'md'
  if (/\.docx$/.test(n)) return 'docx'
  if (/\.(txt|text)$/.test(n)) return 'text'
  if (/\.pdf$/.test(n)) return 'pdf'
  return 'unsupported'
})

const TYPE_TITLES: Record<PreviewType, string> = {
  pdf: 'PDF 预览',
  md: 'Markdown 预览',
  text: '文本预览',
  docx: 'DOCX 预览',
  unsupported: '文档预览',
}
const title = computed(() => TYPE_TITLES[previewType.value])

// Markdown / 纯文本内容
const textContent = ref('')
const mdHtml = ref('')

// Markdown 渲染容器（用于章节定位滚动）
const mdContainer = ref<HTMLElement | null>(null)

// DOCX 渲染容器
const docxContainer = ref<HTMLElement | null>(null)

async function loadDoc() {
  loadError.value = ''
  textContent.value = ''
  mdHtml.value = ''
  docInfo.value = null
  contentLoading.value = false
  const id = docId.value
  if (!id) return
  try {
    docInfo.value = await getDocument(id)
    const type = previewType.value
    if (type === 'md' || type === 'text') {
      contentLoading.value = true
      const res = await fetch(fileUrl(id))
      if (!res.ok) throw new Error(`文本加载失败(${res.status})`)
      const raw = await res.text()
      textContent.value = raw
      if (type === 'md') {
        const [{ marked }, { default: DOMPurify }] = await Promise.all([
          import('marked'),
          import('dompurify'),
        ])
        const html = marked.parse(raw, { gfm: true, breaks: true }) as string
        mdHtml.value = DOMPurify.sanitize(html)
        // 先结束 loading 态使 md 分支挂载，再定位章节
        contentLoading.value = false
        await nextTick()
        scrollToSection()
      }
      ready.value = true
    } else if (type === 'docx') {
      contentLoading.value = true
      const res = await fetch(fileUrl(id))
      if (!res.ok) throw new Error(`文档加载失败(${res.status})`)
      const buf = await res.arrayBuffer()
      const { renderAsync } = await import('docx-preview')
      // 等待 ref 容器挂载（分支在 docInfo 就绪后渲染）
      for (let i = 0; i < 20 && !docxContainer.value; i++) {
        await nextTick()
        await new Promise((r) => setTimeout(r, 50))
      }
      if (!docxContainer.value) throw new Error('渲染容器未就绪')
      await renderAsync(buf, docxContainer.value, undefined, {
        inWrapper: true,
        ignoreLastRenderedPageBreak: true,
      })
      ready.value = true
    }
  } catch (e: any) {
    loadError.value = e?.message || '文档加载失败'
  } finally {
    contentLoading.value = false
  }
}

watch(page, (v) => (cur.value = v))
watch(section, () => {
  if (previewType.value === 'md') scrollToSection()
})
watch(docId, () => {
  cur.value = 1
  ready.value = false
  numPages.value = 0
  loadDoc()
})

function onNumPages(n: number) {
  numPages.value = n
  ready.value = true
}

function go(delta: number) {
  const next = cur.value + delta
  if (next >= 1 && (numPages.value === 0 || next <= numPages.value)) cur.value = next
}

/** 标题匹配用的归一化：去空白、markdown 强调符与大小写差异 */
function normalizeForMatch(s: string): string {
  return (s || '').replace(/[\s`*_~#]/g, '').toLowerCase()
}

/** 滚动到 section 参数指定的章节标题处并高亮闪烁 */
function scrollToSection() {
  if (!section.value) return
  const root = mdContainer.value
  if (!root) return
  const norm = (s: string) => normalizeForMatch(s)
  // 章节路径（A > B > C）：优先按最后一级标题匹配，整条路径兜底（兼容旧数据）
  const parts = section.value.split('>').map((s) => s.trim()).filter(Boolean).reverse()
  const targets = [norm(section.value), ...parts.map(norm)].filter(Boolean)
  const heads = Array.from(root.querySelectorAll('h1, h2, h3, h4, h5, h6')) as HTMLElement[]
  let el: HTMLElement | undefined
  for (const t of targets) {
    el = heads.find((h) => norm(h.innerText) === t)
    if (el) break
  }
  if (!el) {
    for (const t of targets) {
      el = heads.find((h) => {
        const ht = norm(h.innerText)
        return ht && (ht.startsWith(t) || t.startsWith(ht))
      })
      if (el) break
    }
  }
  if (el) {
    // 容器内瞬时定位（确定性高，不牵动窗口滚动），视觉引导交给高亮闪烁
    const top = el.getBoundingClientRect().top - root.getBoundingClientRect().top + root.scrollTop - 8
    root.scrollTo({ top: Math.max(0, top), behavior: 'auto' })
    el.classList.remove('section-flash')
    // 强制重绘以支持重复触发动画
    void (el as HTMLElement & { offsetWidth: number }).offsetWidth
    el.classList.add('section-flash')
    setTimeout(() => el!.classList.remove('section-flash'), 2600)
  } else {
    root.scrollTop = 0
  }
}

onMounted(loadDoc)
</script>

<template>
  <div class="mx-auto max-w-5xl">
    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <NuxtLink to="/" class="btn-outline !py-1.5 text-xs">← 返回问答</NuxtLink>
      <h1 class="text-sm font-semibold text-slate-700">{{ title }}</h1>
      <div class="flex items-center gap-1 text-xs">
        <template v-if="previewType === 'pdf'">
          <button class="btn-outline !px-2 !py-1" :disabled="cur <= 1" @click="go(-1)">‹</button>
          <input v-model.number="cur" type="number" min="1" :max="numPages || undefined" class="w-14 text-center" />
          <span class="text-slate-400">/ {{ numPages || '?' }} 页</span>
          <button class="btn-outline !px-2 !py-1" :disabled="numPages > 0 && cur >= numPages" @click="go(1)">›</button>
        </template>
        <template v-else-if="previewType === 'md' || previewType === 'text'">
          <span v-if="textContent" class="text-slate-400">共 {{ textContent.length }} 字符</span>
          <span v-else class="text-slate-400">—</span>
        </template>
      </div>
    </div>

    <div v-if="!docId" class="card py-16 text-center text-sm text-slate-400">缺少 docId 参数</div>
    <div v-else-if="loadError" class="card py-16 text-center text-sm text-red-500">{{ loadError }}</div>
    <div v-else-if="!docInfo || (contentLoading && previewType !== 'docx')" class="card py-16 text-center text-sm text-slate-400">正在加载文档…</div>

    <!-- Markdown -->
    <div v-else-if="previewType === 'md'" class="card bg-slate-50 p-4">
      <div ref="mdContainer" class="md-preview max-h-[70vh] overflow-auto rounded-lg bg-white p-6 text-sm leading-relaxed text-slate-700 shadow-sm" v-html="mdHtml" />
    </div>

    <!-- 纯文本 -->
    <div v-else-if="previewType === 'text'" class="card bg-slate-50 p-4">
      <pre class="max-h-[70vh] overflow-auto whitespace-pre-wrap break-words rounded-lg bg-white p-4 text-sm leading-relaxed text-slate-700 shadow-sm">{{ textContent }}</pre>
    </div>

    <!-- DOCX -->
    <div v-else-if="previewType === 'docx'" class="card relative bg-slate-100 p-4">
      <div v-if="contentLoading" class="absolute inset-0 z-10 flex items-center justify-center bg-slate-100/80 text-sm text-slate-400">正在加载文档…</div>
      <div ref="docxContainer" class="docx-container mx-auto min-h-[60vh] max-w-4xl" />
    </div>

    <!-- 不支持的类型 -->
    <div v-else-if="previewType === 'unsupported'" class="card py-16 text-center text-sm text-slate-400">
      {{ docInfo?.docName }} 暂不支持在线预览，请下载后查看。
    </div>

    <!-- PDF -->
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

<style scoped>
.md-preview :deep(h1),
.md-preview :deep(h2),
.md-preview :deep(h3),
.md-preview :deep(h4) {
  font-weight: 600;
  color: #1e293b;
  margin: 0.8em 0 0.4em;
}
.md-preview :deep(h1) { font-size: 1.35rem; border-bottom: 1px solid #e2e8f0; padding-bottom: 0.3em; }
.md-preview :deep(h2) { font-size: 1.15rem; }
.md-preview :deep(h3) { font-size: 1rem; }
.md-preview :deep(p) { margin: 0.5em 0; }
.md-preview :deep(ul),
.md-preview :deep(ol) { padding-left: 1.4em; margin: 0.5em 0; list-style: revert; }
.md-preview :deep(li) { margin: 0.2em 0; }
.md-preview :deep(code) {
  background: #f1f5f9;
  border-radius: 4px;
  padding: 0.1em 0.35em;
  font-size: 0.9em;
}
.md-preview :deep(pre) {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  padding: 0.9em 1em;
  overflow-x: auto;
  margin: 0.6em 0;
}
.md-preview :deep(pre code) { background: transparent; color: inherit; padding: 0; }
.md-preview :deep(blockquote) {
  border-left: 3px solid #cbd5e1;
  padding-left: 0.8em;
  color: #64748b;
  margin: 0.6em 0;
}
.md-preview :deep(table) { border-collapse: collapse; margin: 0.6em 0; }
.md-preview :deep(th),
.md-preview :deep(td) { border: 1px solid #e2e8f0; padding: 0.3em 0.7em; }
.md-preview :deep(.section-flash) {
  animation: section-flash 2.5s ease-out;
  border-radius: 4px;
}
@keyframes section-flash {
  0% { background-color: rgb(253 224 71 / 0.65); }
  60% { background-color: rgb(253 224 71 / 0.35); }
  100% { background-color: transparent; }
}

:deep(.docx-container) {
  background: #fff;
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.1);
  padding: 2rem 0;
}
:deep(.docx-container .docx-wrapper) {
  background: #fff;
  padding: 0;
}
</style>