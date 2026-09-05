<script setup lang="ts">
import type { ChatItem } from '~/types'
import { randomId } from '~/utils/answer'

const messages = ref<ChatItem[]>([])
const sending = ref(false)
const storageKey = 'rag_chat_history_v1'

const samples = [
  '违约金比例是多少？',
  '合同中的付款方式是怎样的？',
  '这份文档提到了哪些数据？'
]

onMounted(() => {
  try {
    const raw = sessionStorage.getItem(storageKey)
    if (raw) messages.value = JSON.parse(raw) as ChatItem[]
  } catch { /* ignore */ }
})

watch(messages, (v) => {
  try { sessionStorage.setItem(storageKey, JSON.stringify(v)) } catch { /* ignore */ }
}, { deep: true })

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
    scrollBottom()
  }
}

function openPdf(docId: string, page: number) {
  navigateTo({ path: '/pdf-viewer', query: { docId, page: String(page) } })
}

function clearAll() {
  messages.value = []
}
</script>

<template>
  <div class="flex h-[calc(100vh-8.5rem)] flex-col">
    <!-- 头部 -->
    <div class="mb-3 flex items-center justify-between">
      <div>
        <h1 class="text-lg font-semibold text-slate-800">基于文档的问答</h1>
        <p class="text-xs text-slate-500">回答严格基于已上传文档并强制引用【来源：文件名，第X页】，点击引用跳转 PDF 页码。</p>
      </div>
      <button v-if="messages.length" class="btn-ghost text-xs" @click="clearAll">清空对话</button>
    </div>

    <!-- 消息区 -->
    <div ref="scrollEl" class="flex-1 space-y-4 overflow-y-auto rounded-xl bg-slate-100/70 p-4">
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
