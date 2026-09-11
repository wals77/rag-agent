<script setup lang="ts">
import type { ChatItem, ConversationDto } from '~/types'
import { formatTime, randomId } from '~/utils/answer'

const USER_ID = 'user_001'
const ACTIVE_CONV_KEY = 'rag_active_conv_v1'
const scrollKeyPrefix = 'rag_chat_scroll_v1'

const conversations = ref<ConversationDto[]>([])
const currentId = ref<string | null>(null)
const messages = ref<ChatItem[]>([])
const sending = ref(false)
const convLoading = ref(false)

const samples = [
  '违约金比例是多少？',
  '合同中的付款方式是怎样的？',
  '这份文档提到了哪些数据？'
]

const scrollEl = ref<HTMLElement>()
const scrollKey = computed(() => scrollKeyPrefix + ':' + (currentId.value || 'none'))

function scrollBottom() {
  nextTick(() => {
    if (scrollEl.value) scrollEl.value.scrollTop = scrollEl.value.scrollHeight
  })
}

function onChatScroll() {
  if (!scrollEl.value) return
  try { sessionStorage.setItem(scrollKey.value, String(scrollEl.value.scrollTop)) } catch { /* ignore */ }
}

async function loadConversations() {
  try {
    conversations.value = await listConversations(USER_ID)
  } catch { conversations.value = [] }
}

function toChatItems(list: { messageId: number; role: 'user' | 'assistant'; content: string; citations: any[] | null }[]): ChatItem[] {
  return list.map(m =>
    m.role === 'user'
      ? { id: 'm' + m.messageId, role: 'user' as const, question: m.content }
      : { id: 'm' + m.messageId, role: 'assistant' as const, answer: m.content, citations: m.citations || [], hasAnswer: !!(m.citations && m.citations.length) }
  )
}

async function selectConversation(conversationId: string, restoreScroll = false) {
  if (sending.value || conversationId === currentId.value) return
  convLoading.value = true
  try {
    const list = await listMessages(conversationId)
    currentId.value = conversationId
    try { sessionStorage.setItem(ACTIVE_CONV_KEY, conversationId) } catch { /* ignore */ }
    messages.value = toChatItems(list)
    await nextTick()
    if (scrollEl.value) {
      const saved = Number(sessionStorage.getItem(scrollKey.value) || '0')
      scrollEl.value.scrollTop = restoreScroll && saved > 0 ? saved : scrollEl.value.scrollHeight
    }
  } catch (e: any) {
    messages.value = []
    currentId.value = null
  } finally {
    convLoading.value = false
  }
}

async function newConversation() {
  if (sending.value) return
  try {
    const conv = await createConversation(USER_ID)
    conversations.value.unshift(conv)
    currentId.value = conv.conversationId
    messages.value = []
    try { sessionStorage.setItem(ACTIVE_CONV_KEY, conv.conversationId) } catch { /* ignore */ }
  } catch (e: any) {
    console.error('创建对话失败', e)
  }
}

async function removeConversation(conversationId: string) {
  if (sending.value) return
  try {
    await deleteConversation(conversationId)
  } catch { /* 删除失败也先从界面移除? 不,保留 */ return }
  conversations.value = conversations.value.filter(c => c.conversationId !== conversationId)
  if (currentId.value === conversationId) {
    currentId.value = null
    messages.value = []
    const next = conversations.value[0]
    if (next) await selectConversation(next.conversationId)
  }
}

onMounted(async () => {
  await loadConversations()
  let target: string | null = null
  try { target = sessionStorage.getItem(ACTIVE_CONV_KEY) } catch { /* ignore */ }
  if (!target || !conversations.value.some(c => c.conversationId === target)) {
    target = conversations.value[0]?.conversationId || null
  }
  if (target) await selectConversation(target, true)
})

async function submit(text: string) {
  if (sending.value) return
  messages.value.push({ id: randomId(), role: 'user', question: text })
  const item: ChatItem = { id: randomId(), role: 'assistant', loading: true, streaming: false }
  messages.value.push(item)
  sending.value = true
  scrollBottom()
  try {
    // 无会话时先自动创建（懒创建，避免空会话堆积）
    let cid = currentId.value
    if (!cid) {
      const conv = await createConversation(USER_ID)
      conversations.value.unshift(conv)
      currentId.value = conv.conversationId
      cid = conv.conversationId
      try { sessionStorage.setItem(ACTIVE_CONV_KEY, cid) } catch { /* ignore */ }
    }
    await askChatStream(text, USER_ID, cid, {
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
    loadConversations()
  }
}

function openPdf(docId: string, page: number, section?: string) {
  const query: Record<string, string> = { docId, page: String(page) }
  if (section) query.section = section
  navigateTo({ path: '/pdf-viewer', query })
}

function removeCurrent() {
  if (currentId.value) removeConversation(currentId.value)
}
</script>

<template>
  <div class="flex h-[calc(100vh-8.5rem)] gap-4">
    <!-- 会话侧边栏 -->
    <aside class="hidden w-60 shrink-0 flex-col gap-2 rounded-xl bg-slate-100/70 p-3 md:flex">
      <button class="btn-primary w-full !py-2 text-sm" :disabled="sending" @click="newConversation">＋ 新对话</button>
      <div class="min-h-0 flex-1 space-y-1 overflow-y-auto">
        <div
          v-for="c in conversations"
          :key="c.conversationId"
          class="group flex cursor-pointer items-center gap-2 rounded-lg px-2.5 py-2 transition"
          :class="c.conversationId === currentId ? 'bg-white text-brand-700 ring-1 ring-brand-100' : 'text-slate-600 hover:bg-white/70'"
          :title="c.title"
          @click="selectConversation(c.conversationId, true)"
        >
          <span class="min-w-0 flex-1">
            <span class="block truncate text-sm font-medium">{{ c.title || '新对话' }}</span>
            <span class="block text-[10px] text-slate-400">{{ c.lastMessageTime ? formatTime(c.lastMessageTime) : '' }}</span>
          </span>
          <button
            class="hidden shrink-0 rounded px-1 text-xs text-slate-400 hover:text-red-500 group-hover:block"
            title="删除对话"
            @click.stop="removeConversation(c.conversationId)"
          >✕</button>
        </div>
        <div v-if="!conversations.length" class="px-2 py-4 text-center text-xs text-slate-400">
          暂无对话，点击上方“新对话”开始
        </div>
      </div>
      <button
        v-if="currentId"
        class="btn-ghost w-full !py-1.5 text-xs text-slate-500"
        :disabled="sending"
        @click="removeCurrent"
      >删除当前对话</button>
    </aside>

    <!-- 主对话区 -->
    <div class="flex min-w-0 flex-1 flex-col">
      <!-- 头部 -->
      <div class="mb-3 flex items-center justify-between">
        <div class="min-w-0">
          <h1 class="truncate text-lg font-semibold text-slate-800">
            {{ conversations.find(c => c.conversationId === currentId)?.title || '基于文档的问答' }}
          </h1>
          <p class="text-xs text-slate-500">多轮对话已启用记忆；回答严格基于已上传文档并强制引用【来源：文件名，第X页】，点击引用跳转源文件对应内容。</p>
        </div>
      </div>

      <!-- 消息区 -->
      <div ref="scrollEl" class="relative min-h-0 flex-1 space-y-4 overflow-y-auto rounded-xl bg-slate-100/70 p-4" @scroll.passive="onChatScroll">
        <div v-if="convLoading" class="absolute inset-x-0 top-2 text-center text-xs text-slate-400">正在加载对话记录…</div>
        <div v-if="!messages.length" class="flex h-full flex-col items-center justify-center text-center">
          <div class="mb-4 text-5xl">📚</div>
          <p class="mb-1 text-sm font-medium text-slate-600">向企业知识库提问</p>
          <p class="mb-4 max-w-md text-xs text-slate-400">先在“文档管理”上传 PDF / Word / TXT / Markdown，系统自动完成解析、结构化分块与向量化；每个回答都会给出可核验的出处，支持多轮追问。</p>
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
  </div>
</template>

<style scoped>
.dot { animation: blink 1.2s infinite both; }
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%, 80%, 100% { opacity: 0.25; } 40% { opacity: 1; } }
</style>