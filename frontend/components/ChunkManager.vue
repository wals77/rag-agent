<script setup lang="ts">
import type { ChunkDto, DocumentDto } from '~/types'
import { formatTime } from '~/utils/answer'

const props = defineProps<{ docIdFilter?: string; defaultDocId?: string }>()
const emit = defineEmits<{ (e: 'mutated'): void }>()

const docs = ref<DocumentDto[]>([])
const chunks = ref<ChunkDto[]>([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const status = ref('')
const keyword = ref('')
const selectedDoc = ref(props.docIdFilter || '')
const loading = ref(false)
const toast = ref('')
const selected = ref(new Set<string>())
const rawOpen = reactive(new Set<string>())

function toggleRaw(chunkId: string) {
  if (rawOpen.has(chunkId)) rawOpen.delete(chunkId)
  else rawOpen.add(chunkId)
}

const editTarget = ref<ChunkDto | null>(null)
const splitTarget = ref<ChunkDto | null>(null)
const mergeOpen = ref(false)
const feedbackTarget = ref<ChunkDto | null>(null)

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return chunks.value
  return chunks.value.filter(c =>
    c.chunkText.toLowerCase().includes(kw) ||
    (c.chapterTitle || '').toLowerCase().includes(kw) ||
    c.chunkId.toLowerCase().includes(kw)
  )
})

const selectable = computed(() => chunks.value.filter(c => c.status === 'active'))
const mergedSet = computed(() => new Set(Array.from(selected.value).filter(id => selectable.value.some(c => c.chunkId === id))))
const canMerge = computed(() => {
  const sel = Array.from(selected.value)
  if (sel.length < 2) return false
  const rows = chunks.value.filter(c => sel.includes(c.chunkId) && c.status === 'active')
  return rows.length >= 2 && new Set(rows.map(r => r.docId)).size === 1
})

async function loadDocs() {
  try {
    docs.value = await listDocuments()
    if (props.docIdFilter && !selectedDoc.value) selectedDoc.value = props.docIdFilter
    if (props.defaultDocId && !selectedDoc.value) selectedDoc.value = props.defaultDocId
  } catch (e: any) {
    toast.value = e.message
  }
}

async function load() {
  loading.value = true
  try {
    const res = await listChunks({
      docId: selectedDoc.value || undefined,
      status: status.value || undefined,
      page: page.value,
      size: size.value
    })
    chunks.value = res.items
    total.value = res.total
    selected.value.clear()
  } catch (e: any) {
    toast.value = e.message
  } finally {
    loading.value = false
  }
}

function toggle(id: string) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id); else s.add(id)
  selected.value = s
}

function chooseDoc() {
  page.value = 0
  load()
}

function notice(msg: string) {
  toast.value = msg
  setTimeout(() => (toast.value = ''), 4000)
}

function refreshAfterMutation(msg: string) {
  notice(msg)
  page.value = 0
  load()
  emit('mutated')
}

async function saveEdit() {
  if (!editTarget.value) return
  const res = await editChunk(editTarget.value.chunkId, editTarget.value.chunkText)
  editTarget.value = null
  refreshAfterMutation('已生成编辑版本，原分块已归档' + (res.notice || ''))
}

async function doMerge(mergedText: string) {
  const ids = Array.from(selected.value)
  const res = await mergeChunks(ids, mergedText || '')
  mergeOpen.value = false
  refreshAfterMutation('合并成功，新分块: ' + (res.chunk?.chunkId || ''))
}

async function doSplit(pieces: string[]) {
  if (!splitTarget.value) return
  await splitChunk(splitTarget.value.chunkId, pieces.filter(p => p.trim()))
  splitTarget.value = null
  refreshAfterMutation('拆分成功，原分块已标记为 split')
}

async function doFeedback(payload: { type: string; comment: string; corrected?: string }) {
  if (!feedbackTarget.value) return
  const res = await submitFeedback(feedbackTarget.value.chunkId, {
    feedbackType: payload.type,
    userComment: payload.comment,
    correctedText: payload.corrected,
    userId: 'user_001'
  })
  feedbackTarget.value = null
  notice(res.notice || '反馈已记录')
}

onMounted(() => {
  loadDocs().then(load)
})
</script>

<template>
  <div>
    <div v-if="toast" class="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700">{{ toast }}</div>

    <!-- 工具栏 -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <select v-model="selectedDoc" class="input !w-64" @change="chooseDoc">
        <option value="">全部文档</option>
        <option v-for="d in docs" :key="d.docId" :value="d.docId">{{ d.docName }}</option>
      </select>
      <select v-model="status" class="input !w-36" @change="load">
        <option value="">全部状态</option>
        <option value="active">active(生效)</option>
        <option value="split">split(父块/已拆分)</option>
        <option value="merged">merged(已合并)</option>
        <option value="edited">edited(已归档)</option>
      </select>
      <input v-model="keyword" class="input !w-56" placeholder="在当前页过滤文本/章节…" />
      <span class="text-xs text-slate-400">共 {{ total }} 块</span>
      <div class="flex-1"></div>
      <button class="btn-primary text-xs" :disabled="!canMerge" title="勾选同一文档 ≥2 个 active 分块后合并" @click="mergeOpen = true">合并选中 ({{ mergedSet.size }})</button>
    </div>

    <!-- 表格 -->
    <div class="card overflow-x-auto">
      <table class="w-full text-left text-sm">
        <thead class="border-b border-slate-200 bg-slate-50 text-xs text-slate-500">
          <tr>
            <th class="w-10 px-3 py-2.5"></th>
            <th class="px-3 py-2.5 font-medium">分块内容</th>
            <th class="w-24 px-3 py-2.5 font-medium">来源页</th>
            <th class="w-28 px-3 py-2.5 font-medium">方法</th>
            <th class="w-24 px-3 py-2.5 font-medium">状态</th>
            <th class="w-16 px-3 py-2.5 font-medium">质量</th>
            <th class="w-64 px-3 py-2.5 font-medium">溯源链</th>
            <th class="w-52 px-3 py-2.5 text-right font-medium">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr v-if="loading"><td colspan="8" class="px-3 py-10 text-center text-xs text-slate-400">加载中…</td></tr>
          <tr v-else-if="!filtered.length"><td colspan="8" class="px-3 py-10 text-center text-xs text-slate-400">无分块数据</td></tr>
          <tr v-for="c in filtered" :key="c.chunkId" class="align-top hover:bg-slate-50/70">
            <td class="px-3 py-2.5">
              <input
                v-if="c.status === 'active'"
                type="checkbox"
                :checked="selected.has(c.chunkId)"
                class="h-4 w-4 accent-brand-600"
                @change="toggle(c.chunkId)"
              />
            </td>
            <td class="max-w-md px-3 py-2.5">
              <p class="line-clamp-3 whitespace-pre-wrap text-xs leading-relaxed text-slate-600">{{ c.chunkText }}</p>
              <template v-if="c.rawContent">
                <button class="mt-1 text-[10px] text-brand-600 hover:underline" @click="toggleRaw(c.chunkId)">
                  {{ rawOpen.has(c.chunkId) ? '收起原文' : '查看原文(Markdown)' }}
                </button>
                <pre v-if="rawOpen.has(c.chunkId)" class="mt-1 max-h-64 overflow-auto whitespace-pre-wrap break-words rounded bg-slate-900 p-2 text-[10px] leading-relaxed text-slate-100">{{ c.rawContent }}</pre>
              </template>
              <p class="mt-1 text-[10px] text-slate-300">#{{ c.chunkId.slice(-12) }}</p>
            </td>
            <td class="px-3 py-2.5">
              <span class="text-xs text-slate-600">第{{ c.pageNum }}页</span>
              <span v-if="c.chapterTitle" class="mt-0.5 block text-[10px] text-slate-400">{{ c.chapterTitle }}</span>
            </td>
            <td class="px-3 py-2.5"><span class="badge bg-slate-100 text-slate-500">{{ c.splitMethod }}</span></td>
            <td class="px-3 py-2.5"><StatusBadge :status="c.status" /></td>
            <td class="px-3 py-2.5 text-xs text-slate-500">{{ c.qualityScore != null ? c.qualityScore.toFixed(2) : '-' }}</td>
            <td class="px-3 py-2.5 text-[10px] leading-relaxed text-slate-400">
              <template v-if="c.parentChunkId">父块: {{ c.parentChunkId.slice(-12) }}<br /></template>
              <template v-if="c.editedVersionOf">替代原块: {{ c.editedVersionOf.slice(-12) }}<br /></template>
              <template v-if="c.mergedIntoChunkId">并入: {{ c.mergedIntoChunkId.slice(-12) }}</template>
              <template v-if="!c.parentChunkId && !c.editedVersionOf && !c.mergedIntoChunkId">—</template>
            </td>
            <td class="px-3 py-2.5">
              <div class="flex justify-end gap-1">
                <button class="btn-ghost !px-2 !py-1 text-xs" :disabled="c.status !== 'active'" @click="editTarget = c">编辑</button>
                <button class="btn-ghost !px-2 !py-1 text-xs" :disabled="c.status !== 'active'" @click="splitTarget = c">拆分</button>
                <button class="btn-ghost !px-2 !py-1 text-xs" :disabled="c.status !== 'active'" @click="feedbackTarget = c">反馈</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="mt-3 flex items-center justify-between text-xs text-slate-500">
      <span>第 {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span>
      <div class="flex gap-2">
        <button class="btn-outline !px-2 !py-1" :disabled="page === 0" @click="page--; load()">上一页</button>
        <button class="btn-outline !px-2 !py-1" :disabled="(page + 1) * size >= total" @click="page++; load()">下一页</button>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <Teleport to="body">
      <div v-if="editTarget" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4" @click.self="editTarget = null">
        <div class="w-full max-w-3xl rounded-2xl bg-white p-5 shadow-xl">
          <div class="mb-3 flex items-center justify-between">
            <h3 class="text-base font-semibold">编辑分块（将生成 edited 新版本）</h3>
            <button class="btn-ghost !px-2 !py-1" @click="editTarget = null">✕</button>
          </div>
          <p class="mb-2 text-xs text-slate-400">{{ editTarget.docName }} · 第{{ editTarget.pageNum }}页 · {{ editTarget.chunkId }}</p>
          <textarea v-model="editTarget.chunkText" rows="10" class="input font-mono text-xs leading-relaxed"></textarea>
          <div class="mt-4 flex justify-end gap-2">
            <button class="btn-outline" @click="editTarget = null">取消</button>
            <button class="btn-primary" :disabled="!editTarget.chunkText.trim()" @click="saveEdit">保存修改</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 合并弹窗 -->
    <Teleport to="body">
      <MergeModal v-if="mergeOpen" :chunks="chunks.filter(c => selected.has(c.chunkId))" @close="mergeOpen = false" @confirm="doMerge" />
    </Teleport>

    <!-- 拆分弹窗 -->
    <Teleport to="body">
      <SplitModal v-if="splitTarget" :chunk="splitTarget" @close="splitTarget = null" @confirm="doSplit" />
    </Teleport>

    <!-- 反馈弹窗 -->
    <Teleport to="body">
      <ChunkFeedbackModal v-if="feedbackTarget" :chunk="feedbackTarget" @close="feedbackTarget = null" @confirm="doFeedback" />
    </Teleport>
  </div>
</template>
