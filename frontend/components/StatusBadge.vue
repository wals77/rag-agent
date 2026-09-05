<script setup lang="ts">
defineProps<{ status: string }>()

const map: Record<string, string> = {
  processing: 'bg-sky-50 text-sky-600 ring-sky-200',
  done: 'bg-emerald-50 text-emerald-600 ring-emerald-200',
  failed: 'bg-red-50 text-red-600 ring-red-200',
  active: 'bg-emerald-50 text-emerald-600 ring-emerald-200',
  merged: 'bg-violet-50 text-violet-600 ring-violet-200',
  split: 'bg-amber-50 text-amber-600 ring-amber-200',
  edited: 'bg-slate-100 text-slate-500 ring-slate-200'
}

const labels: Record<string, string> = {
  processing: '解析中',
  done: '已完成',
  failed: '失败',
  active: '生效',
  merged: '已合并',
  split: '已拆分',
  edited: '已被编辑'
}
</script>

<template>
  <span class="badge ring-1" :class="map[status] || 'bg-slate-100 text-slate-500 ring-slate-200'">
    <span v-if="status === 'processing'" class="mr-1 inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-sky-500"></span>
    {{ labels[status] || status }}
  </span>
</template>
