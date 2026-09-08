import { marked } from 'marked'
import DOMPurify from 'dompurify'

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
  chapterTitle?: string | null
}

function isMarkdownName(name: string): boolean {
  return /\.md$/i.test(name) || /\.markdown$/i.test(name)
}

/** 引用跳转链接：md 文档携带 section（章节标题）以便滚动定位到对应章节 */
export function citeLink(ref: CiteRef): string {
  let link = `/pdf-viewer?docId=${encodeURIComponent(ref.docId)}&page=${ref.pageNum}`
  if (isMarkdownName(ref.docName) && ref.chapterTitle) {
    link += `&section=${encodeURIComponent(ref.chapterTitle)}`
  }
  return link
}

/** 反解码 marked 输出中的基础实体，用于引用文件名匹配 */
function unescapeBasic(s: string): string {
  return s
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
}

/** Markdown 输出允许保留的标签（引用 chip 的 <a> 也在其中）；其余 HTML 一律剔除 */
const ALLOWED_TAGS = [
  'p', 'br', 'hr', 'strong', 'em', 'del', 's',
  'ul', 'ol', 'li',
  'code', 'pre',
  'blockquote',
  'table', 'thead', 'tbody', 'tr', 'th', 'td',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'a', 'span',
]

/**
 * 将模型回答渲染为 Markdown（粗体/列表/表格/代码块等），
 * 其中【来源：文件名，第X页】标记转为可点击引用链接，返回安全的 HTML。
 *
 * 安全管线：marked 解析 -> 注入引用 <a> -> DOMPurify 按白名单消毒
 * （回答中的原生 HTML 元素一律剔除，仅保留 Markdown 结构与引用链接）。
 */
export function renderAnswerWithCitations(answer: string, cites: CiteRef[]): string {
  let html = marked.parse(answer || '', { gfm: true, breaks: true }) as string
  const byKey = new Map<string, CiteRef>()
  for (const c of cites) byKey.set(`${c.docName}|${c.pageNum}`, c)

  // 在解析后的 HTML 上替换引用标记为链接
  const markerRe = /【来源：([^】]+?)，第([0-9一二三四五六七八九十百千]+)页】/g
  html = html.replace(markerRe, (whole, name, page) => {
    const cn = unescapeBasic(name).trim()
    const p = normalizePage(page)
    const ref = byKey.get(`${cn}|${p}`)
    if (!ref) return whole
    return `<a href="${citeLink(ref)}" class="cite-chip" target="_blank" rel="noopener">📄 ${escapeHtml(cn)}·第${p}页</a>`
  })

  // 消毒（SSR 期 answer 恒为空，跳过依赖 window 的消毒步骤）
  return typeof window !== 'undefined'
    ? DOMPurify.sanitize(html, { ALLOWED_TAGS, ADD_ATTR: ['target'] })
    : html
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
