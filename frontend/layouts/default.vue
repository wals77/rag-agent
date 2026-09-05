<script setup lang="ts">
const links = [
  { label: '问答', to: '/' },
  { label: '文档管理', to: '/documents' },
  { label: '分块管理', to: '/admin/chunks' },
  { label: '反馈中心', to: '/admin/feedback' }
]
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <header class="sticky top-0 z-20 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div class="mx-auto flex h-14 w-full max-w-7xl items-center justify-between px-4">
        <div class="flex items-center gap-2">
          <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 text-sm font-bold text-white">R</span>
          <NuxtLink to="/" class="text-lg font-semibold text-slate-800">可信RAG知识库 · 强制引用问答</NuxtLink>
        </div>
        <nav class="flex items-center gap-1">
          <NuxtLink
            v-for="l in links"
            :key="l.to"
            :to="l.to"
            class="rounded-lg px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100"
            :class="{ 'bg-brand-50 font-medium text-brand-700': $route.path === l.to || (l.to === '/documents' && $route.path.startsWith('/documents')) }"
          >{{ l.label }}</NuxtLink>
        </nav>
      </div>
    </header>

    <main class="mx-auto w-full max-w-7xl flex-1 px-4 py-6">
      <slot />
    </main>

    <footer class="border-t border-slate-200 py-4 text-center text-xs text-slate-400">
      每个回答均带可点击引用 · 未找到资料时拒绝作答 · 数据本地化
    </footer>
  </div>
</template>
