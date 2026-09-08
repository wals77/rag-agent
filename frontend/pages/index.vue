<script setup lang="ts">
import type { ChatItem } from '~/types'
import { randomId } from '~/utils/answer'

const messages = ref<ChatItem[]>([])
const sending = ref(false)
const storageKey = 'rag_chat_history_v1'
const scrollKey = 'rag_chat_scroll_v1'

const samples = [
  '违约金比例是多少？',
  '合同中的付款方式是怎样的？',
  '这份文档提到了哪些数据？'
]

/** 持久化前快照：不保存流式/加载中的瞬时状态，避免返回/刷新后出现卡住的占位 */
function stableSnapshot(list: ChatItem[]): ChatItem[] {
  const out: ChatItem[] = []
  for (const m of list) {
    if (m.role === 'user') { out.push(m); continue }
    if (!m.answer && !m.error && !m.citations?.length) continue
    const { loading, streaming, ...rest } = m
    out.push(rest)
  }
  return out
}

function persistNow() {
  try { sessionStorage.setItem(storageKey, JSON.stringify(stableSnapshot(messages.value))) } catch { /* ignore */ }
}

let persistTimer: ReturnType<typeof setTimeout> | null = null
function persistSoon() {
  if (persistTimer) clearTimeout(persistTimer)
  persistTimer = setTimeout(() => {
    persistTimer = null
    persistNow()
  }, 200)
}

onMounted(async () => {
  if (import.meta.client) {
    try {
      const raw = sessionStorage.getItem(storageKey)
      if (raw) {
        const restored = JSON.parse(raw) as ChatItem[]
        messages.value = restored
          .filter(m => m.role === 'user' || !!m.answer || !!m.error || !!m.citations?.length)
          .map(m => (m.role === 'assistant' ? { ...m, loading: false, streaming: false } : m))
      }
    } catch { /* ignore */ }
  }
  // 恢复回来的滚动位置，避免返回/刷新后回答内容被顶出可视区
  await nextTick()
  const savedStr = import.meta.client ? sessionStorage.getItem(scrollKey) : null
  const saved = Number(savedStr || '0')
  const target = Number.isFinite(saved) && saved > 0 ? saved : (scrollEl.value?.scrollHeight ?? 0)
  if (scrollEl.value) scrollEl.value.scrollTop = target
  await nextTick()
  if (scrollEl.value && scrollEl.value.scrollTop === 0 && target > 0) {
    scrollEl.value.scrollTop = scrollEl.value.scrollHeight
  }
})

function onChatScroll() {
  if (!scrollEl.value) return
  try {
    sessionStorage.setItem(scrollKey, String(scrollEl.value.scrollTop))
  } catch { /* ignore */ }
}

watch(messages, () => persistSoon(), { deep: true })

const scrollEl = ref<HTMLElement>()

function scrollBottom() {
  nextTick(() => {
    if (scrollEl.value) scrollEl.value.scrollTop = scrollEl.value.scrollHeight
  })
}

async function submit(text: string) {
  if (sending.value) return
  messages.value.push({ id: randomId(), role: 'user', question: text })
  const item: ChatItem = { id: randomId(), role: 'assistant', loading: true, streaming: false }
  messages.value.push(item)
  sending.value = true
  scrollBottom()
  try {
    await askChatStream(text, 'user_001', {
      onStatus() {
        scrollBottom()
      },
      onDelta(part) {
        item.loading = false
        item.streaming = true
        item.answer = (item.answer || '') + part
        scrollBottom()
      },
      onDone(res) {
        item.loading = false
        item.streaming = false
        item.answer = res.answer
        item.citations = res.citations
        item.hasAnswer = res.hasAnswer
        scrollBottom()
      },
      onError(err) {
        item.loading = false
        item.streaming = false
        item.error = err.message
      }
    })
  } catch (e: any) {
    item.loading = false
    item.streaming = false
    item.error = e?.message || '请求失败'
  } finally {
    sending.value = false
    // 明确持久化最终快照，避免依赖 deep watcher 的触发时机导致丢失
    persistNow()
    scrollBottom()
  }
}

function openPdf(docId: string, page: number, section?: string) {
  const query: Record<string, string> = { docId, page: String(page) }
  if (section) query.section = section
  navigateTo({ path: '/pdf-viewer', query })
}

function clearAll() {
  messages.value = []
  if (persistTimer) { clearTimeout(persistTimer); persistTimer = null }
  persistNow()
  try { sessionStorage.removeItem(scrollKey) } catch { /* ignore */ }
}
</script>

<template>
  <div class="flex h-[calc(100vh-8.5rem)] flex-col">
    <!-- 头部 -->
    <div class="mb-3 flex items-center justify-between">
      <div>
        <h1 class="text-lg font-semibold text-slate-800">基于文档的问答</h1>
        <p class="text-xs text-slate-500">回答严格基于已上传文档并强制引用【来源：文件名，第X页】，点击引用跳转源文件对应内容。</p>
      </div>
      <button v-if="messages.length" class="btn-ghost text-xs" @click="clearAll">清空对话</button>
    </div>

    <!-- 消息区 -->
    <div ref="scrollEl" class="flex-1 space-y-4 overflow-y-auto rounded-xl bg-slate-100/70 p-4" @scroll.passive="onChatScroll">
      <div v-if="!messages.length" class="flex h-full flex-col items-center justify-center text-center">
        <div class="mb-4 text-5xl">📚</div>
        <p class="mb-1 text-sm font-medium text-slate-600">向企业知识库提问</p>
        <p class="mb-4 max-w-md text-xs text-slate-400">先在“文档管理”上传 PDF / Word / TXT，系统自动完成解析、智能分块与向量化；每个回答都会给出可核验的出处。</p>
        <div class="flex flex-wrap justify-center gap-2">
          <button v-for="s in samples" :key="s" class="btn-outline text-xs" @click="submit(s)">{{ s }}</button>
        </div>
      </div>

      <template v-for="m in messages" :key="m.id">
        <!-- 用户问题 -->
        <div v-if="m.role === 'user'" class="flex justify-end">
          <div class="max-w-[80%] rounded-2xl rounded-tr-sm bg-brand-600 px-4 py-2.5 text-sm text-white shadow-sm">
            {{ m.question }}
          </div>
        </div>

        <!-- 回答 -->
        <div v-else class="flex justify-start">
          <div v-if="m.loading" class="flex items-center gap-2 rounded-2xl bg-white px-5 py-3 shadow-sm ring-1 ring-slate-200">
            <span class="flex gap-1">
              <span class="dot h-2 w-2 rounded-full bg-brand-400"></span>
              <span class="dot h-2 w-2 rounded-full bg-brand-400"></span>
              <span class="dot h-2 w-2 rounded-full bg-brand-400"></span>
            </span>
            <span class="text-xs text-slate-400">正在检索知识库并生成带引用的回答…</span>
          </div>
          <div v-else-if="m.error" class="max-w-[80%] rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-600 ring-1 ring-red-200">{{ m.error }}</div>
          <!-- 流式中间态：实时展示已生成文本 -->
          <div v-else-if="m.streaming" class="max-w-[85%] whitespace-pre-wrap break-words rounded-2xl rounded-tl-sm bg-white px-4 py-3 text-sm leading-relaxed text-slate-700 shadow-sm ring-1 ring-slate-200">
            {{ m.answer }}
            <span class="ml-0.5 inline-block h-3.5 w-0.5 animate-pulse bg-brand-500 align-middle"></span>
          </div>
          <div v-else class="max-w-[85%]">
            <AnswerCard
              :answer="m.answer || ''"
              :citations="m.citations || []"
              :has-answer="!!m.hasAnswer"
              @open-pdf="openPdf"
            />
          </div>
        </div>
      </template>
    </div>

    <!-- 输入区 -->
    <div class="pt-3">
      <ChatInput :loading="sending" @submit="submit" />
    </div>
  </div>
</template>

<style scoped>
.dot { animation: blink 1.2s infinite both; }
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%, 80%, 100% { opacity: 0.25; } 40% { opacity: 1; } }
</style>
