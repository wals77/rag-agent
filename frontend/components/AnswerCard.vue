<script setup lang="ts">
import type { Citation } from '~/types'
import { renderAnswerWithCitations } from '~/utils/answer'

const props = defineProps<{
  answer: string
  citations: Citation[]
  hasAnswer: boolean
  notice?: string | null
}>()

const emit = defineEmits<{ (e: 'open-pdf', docId: string, page: number): void }>()

const userId = 'user_001'
const toast = ref('')
const correctionOpen = ref(false)
const correctionText = ref('')
const correctionComment = ref('')
const selected = ref<Citation | null>(props.citations[0] || null)

const rendered = computed(() =>
  renderAnswerWithCitations(props.answer, props.citations.map(c => ({
    docId: c.docId, docName: c.docName, pageNum: c.pageNum, text: c.text
  })))
)

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
      <div class="text-xs font-medium text-slate-400">引用来源（点击跳转 PDF 页码）</div>
      <button
        v-for="(c, i) in citations"
        :key="c.chunkId + i"
        class="group flex w-full items-start gap-2 rounded-lg bg-brand-50/60 px-3 py-2 text-left ring-1 ring-brand-100 transition hover:bg-brand-50"
        @click="emit('open-pdf', c.docId, c.pageNum)"
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
