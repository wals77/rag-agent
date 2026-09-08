<script setup lang="ts">
import type { Citation } from '~/types'
import { renderAnswerWithCitations } from '~/utils/answer'

const props = defineProps<{
  answer: string
  citations: Citation[]
  hasAnswer: boolean
  notice?: string | null
}>()

const emit = defineEmits<{ (e: 'open-pdf', docId: string, page: number, section?: string): void }>()

const userId = 'user_001'
const toast = ref('')
const correctionOpen = ref(false)
const correctionText = ref('')
const correctionComment = ref('')
const selected = ref<Citation | null>(props.citations[0] || null)

const rendered = computed(() =>
  renderAnswerWithCitations(props.answer, props.citations.map(c => ({
    docId: c.docId, docName: c.docName, pageNum: c.pageNum, text: c.text, chapterTitle: c.chapterTitle
  })))
)

function openCitation(c: Citation) {
  emit('open-pdf', c.docId, c.pageNum, c.chapterTitle || undefined)
}

async function send(type: string, chunkId: string, comment: string, corrected?: string) {
  try {
    const res = await submitFeedback(chunkId, {
      feedbackType: type,
      userComment: comment || undefined,
      correctedText: corrected || undefined,
      userId
    })
    toast.value = res.notice || '已记录您的反馈，感谢！'
    correctionOpen.value = false
    correctionText.value = ''
    correctionComment.value = ''
    setTimeout(() => (toast.value = ''), 4000)
  } catch (e: any) {
    toast.value = '反馈提交失败: ' + e.message
  }
}

function like(kind: 'good' | 'bad') {
  const c = props.citations[0]
  if (!c) return
  send(kind === 'good' ? 'good_answer' : 'bad_answer', c.chunkId, kind === 'good' ? '答案有帮助' : '答案没有帮助')
}

function openCorrection() {
  correctionOpen.value = true
  selected.value = props.citations[0] || null
}
</script>

<template>
  <div class="answer-bubble rounded-2xl rounded-tl-sm bg-white p-4 shadow-sm ring-1 ring-slate-200">
    <!-- 渲染后的答案（引用标记已变成可点击链接） -->
    <div class="answer prose prose-sm max-w-none break-words" v-html="rendered"></div>

    <!-- 引用卡片列表 -->
    <div v-if="citations.length" class="mt-4 space-y-2 border-t border-slate-100 pt-3">
      <div class="text-xs font-medium text-slate-400">引用来源（点击跳转源文件对应内容）</div>
      <button
        v-for="(c, i) in citations"
        :key="c.chunkId + i"
        class="group flex w-full items-start gap-2 rounded-lg bg-brand-50/60 px-3 py-2 text-left ring-1 ring-brand-100 transition hover:bg-brand-50"
        @click="openCitation(c)"
      >
        <span class="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded bg-brand-600 text-[10px] font-bold text-white">{{ i + 1 }}</span>
        <span class="min-w-0 flex-1">
          <span class="block truncate text-xs font-medium text-brand-700">📄 {{ c.docName }} · 第{{ c.pageNum }}页</span>
          <span v-if="c.chapterTitle" class="block text-[11px] text-slate-400">{{ c.chapterTitle }}</span>
          <span class="mt-1 line-clamp-2 block text-xs text-slate-500">{{ c.text }}</span>
        </span>
        <span class="shrink-0 self-center text-xs text-brand-500 opacity-0 transition group-hover:opacity-100">查看 ▸</span>
      </button>
    </div>

    <!-- 反馈 -->
    <div v-if="citations.length" class="mt-3 flex items-center gap-1 border-t border-slate-100 pt-2">
      <span class="text-[11px] text-slate-400">这个回答有帮助吗？</span>
      <button class="btn-ghost !px-2 !py-1 text-xs" title="有帮助" @click="like('good')">👍</button>
      <button class="btn-ghost !px-2 !py-1 text-xs" title="没帮助" @click="like('bad')">👎</button>
      <button class="btn-ghost !px-2 !py-1 text-xs" title="引用/内容有误，可提交修正" @click="openCorrection">引用有误/我要修正</button>
    </div>

    <div v-if="toast" class="mt-2 rounded bg-emerald-50 px-3 py-1.5 text-xs text-emerald-700">{{ toast }}</div>
    <div v-if="!hasAnswer" class="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700">
      ⚠️ 本次未在资料库中找到可靠依据，未生成无引用内容。可尝试上传相关文档或换个问法。
    </div>

    <!-- 修正弹窗 -->
    <div v-if="correctionOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4" @click.self="correctionOpen = false">
      <div class="w-full max-w-lg rounded-2xl bg-white p-5 shadow-xl">
        <h3 class="mb-3 text-base font-semibold text-slate-800">反馈引用问题</h3>
        <label class="label">关联引用</label>
        <select v-if="citations.length > 1" v-model="selected" class="input mb-3">
          <option v-for="(c, i) in citations" :key="c.chunkId" :value="c">
            {{ c.docName }} · 第{{ c.pageNum }}页
          </option>
        </select>
        <div v-else-if="selected" class="mb-3 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600">
          {{ selected.docName }} · 第{{ selected.pageNum }}页
        </div>

        <label class="label">备注（必填）</label>
        <textarea v-model="correctionComment" rows="2" class="input mb-3" placeholder="例如：这里分块把两条条款拆开了 / 页面页码标错了…" />

        <label class="label">若内容有误，可粘贴修正后的文字（将自动生成新的分块版本）</label>
        <textarea v-model="correctionText" rows="4" class="input" placeholder="选填：正确的内容…" />

        <div class="mt-4 flex justify-end gap-2">
          <button class="btn-outline" @click="correctionOpen = false">取消</button>
          <button
            class="btn-primary"
            :disabled="!correctionComment.trim()"
            @click="send(correctionText.trim() ? 'wrong_content' : 'missing_context', selected?.chunkId || citations[0].chunkId, correctionComment, correctionText.trim() || undefined)"
          >提交反馈</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Markdown 回答排版（未启用 typography 插件，手动补齐常用元素） */
.answer :deep(p) { margin: 0.4em 0; }
.answer :deep(p:first-child) { margin-top: 0; }
.answer :deep(p:last-child) { margin-bottom: 0; }
.answer :deep(ul),
.answer :deep(ol) { padding-left: 1.4em; margin: 0.5em 0; list-style: revert; }
.answer :deep(li) { margin: 0.2em 0; }
.answer :deep(h1),
.answer :deep(h2),
.answer :deep(h3),
.answer :deep(h4) {
  font-weight: 600;
  color: #1e293b;
  margin: 0.8em 0 0.4em;
}
.answer :deep(h1) { font-size: 1.15rem; }
.answer :deep(h2) { font-size: 1.05rem; }
.answer :deep(h3) { font-size: 0.95rem; }
.answer :deep(strong) { font-weight: 600; color: #0f172a; }
.answer :deep(code) {
  background: #f1f5f9;
  border-radius: 4px;
  padding: 0.1em 0.35em;
  font-size: 0.9em;
}
.answer :deep(pre) {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  padding: 0.9em 1em;
  overflow-x: auto;
  margin: 0.6em 0;
}
.answer :deep(pre code) { background: transparent; color: inherit; padding: 0; }
.answer :deep(blockquote) {
  border-left: 3px solid #cbd5e1;
  padding-left: 0.8em;
  color: #64748b;
  margin: 0.6em 0;
}
.answer :deep(table) { border-collapse: collapse; margin: 0.6em 0; display: block; overflow-x: auto; max-width: 100%; }
.answer :deep(th),
.answer :deep(td) { border: 1px solid #e2e8f0; padding: 0.3em 0.7em; font-size: 12px; }
.answer :deep(th) { background: #f8fafc; font-weight: 600; }
.answer :deep(hr) { border-color: #e2e8f0; margin: 0.8em 0; }

.answer :deep(.cite-chip) {
  display: inline-block;
  margin: 0 2px;
  padding: 0 6px;
  border-radius: 6px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid #dbeafe;
  text-decoration: none;
  transition: all 0.15s;
}
.answer :deep(.cite-chip:hover) {
  background: #dbeafe;
}
</style>
