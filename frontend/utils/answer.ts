export function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export interface CiteRef {
  docId: string
  docName: string
  pageNum: number
  text: string
}

/**
 * 将模型回答中的【来源：文件名，第X页】标记渲染为可点击引用，返回安全的 HTML。
 */
export function renderAnswerWithCitations(answer: string, cites: CiteRef[]): string {
  const escaped = escapeHtml(answer || '')
  let html = escaped
  const byKey = new Map<string, CiteRef>()
  for (const c of cites) byKey.set(`${c.docName}|${c.pageNum}`, c)

  // 逐个替换引用标记为链接
  const markerRe = /【来源：([^】]+?)，第([0-9一二三四五六七八九十百千]+)页】/g
  html = html.replace(markerRe, (whole, name, page) => {
    const cn = name.trim()
    const p = normalizePage(page)
    const ref = byKey.get(`${cn}|${p}`)
    if (!ref) return whole
    const link = `/pdf-viewer?docId=${encodeURIComponent(ref.docId)}&page=${p}`
    return `<a href="${link}" class="cite-chip" target="_blank" rel="noopener">📄 ${escapeHtml(cn)}·第${p}页</a>`
  })

  // 保留简单换行
  html = html.replace(/\n/g, '<br/>')
  return html
}

function normalizePage(p: string): number {
  const cn = '零一二三四五六七八九'
  const map: Record<string, number> = {}
  for (let i = 0; i < cn.length; i++) map[cn[i]] = i
  let n = 0
  for (const ch of p) {
    if (ch >= '0' && ch <= '9') n = n * 10 + (ch.charCodeAt(0) - 48)
    else n = n * 10 + (map[ch] ?? 0)
  }
  return n || 1
}

export function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  return d.toLocaleString('zh-CN', { hour12: false })
}

export function formatSize(bytes: number): string {
  if (!bytes && bytes !== 0) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

export function randomId(): string {
  return Math.random().toString(36).slice(2) + Date.now().toString(36)
}
