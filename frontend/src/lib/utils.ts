import type { ImageRef, PageSpec, SectionSpec } from '../types'

export const PRESETS = [
  { src: '/presets/visual-01.svg', label: '暖光球体' },
  { src: '/presets/visual-02.svg', label: '金属拱门' },
  { src: '/presets/visual-03.svg', label: '深林切面' },
  { src: '/presets/visual-04.svg', label: '柔雾流体' },
  { src: '/presets/visual-05.svg', label: '日光网格' },
]

export function cloneSpec<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

export function getAtPath(target: unknown, path: string): unknown {
  if (!path) return target
  return path.split('.').reduce<unknown>((current, key) => {
    if (current === null || current === undefined) return undefined
    if (Array.isArray(current)) return current[Number(key)]
    if (typeof current === 'object') return (current as Record<string, unknown>)[key]
    return undefined
  }, target)
}

export function setAtPath(target: unknown, path: string, value: unknown): void {
  const keys = path.split('.')
  let current: any = target
  for (let i = 0; i < keys.length - 1; i += 1) {
    current = current[keys[i]]
    if (current === undefined || current === null) return
  }
  current[keys[keys.length - 1]] = value
}

export function findSection(spec: PageSpec, sectionId: string): SectionSpec | undefined {
  return spec.sections.find((section) => section.id === sectionId)
}

export interface ImageEntry {
  sectionId: string
  path: string
  label: string
  image: ImageRef
}

export function collectImages(spec: PageSpec): ImageEntry[] {
  const result: ImageEntry[] = []
  for (const section of spec.sections) {
    if (section.component === 'hero.split' && section.props.image) {
      result.push({
        sectionId: section.id,
        path: 'props.image',
        label: '首屏主图',
        image: section.props.image,
      })
    }
    if (section.component === 'gallery.grid' && Array.isArray(section.props.items)) {
      section.props.items.forEach((item: any, index: number) => {
        if (item?.image) {
          result.push({
            sectionId: section.id,
            path: `props.items.${index}.image`,
            label: `作品图 ${index + 1}`,
            image: item.image,
          })
        }
      })
    }
  }
  return result
}

export function firstImage(spec: PageSpec): ImageEntry | undefined {
  return collectImages(spec)[0]
}

export function formatRelativeTime(iso: string): string {
  const time = new Date(iso).getTime()
  if (!Number.isFinite(time)) return '刚刚'
  const diff = Date.now() - time
  const minute = 60_000
  const hour = 3_600_000
  const day = 86_400_000
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < day * 7) return `${Math.floor(diff / day)} 天前`
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'short',
    day: 'numeric',
  }).format(new Date(iso))
}

export function sectionLabel(component: SectionSpec['component']): string {
  switch (component) {
    case 'nav.basic':
      return '导航'
    case 'hero.split':
      return '首屏'
    case 'gallery.grid':
      return '作品画廊'
    case 'stats.row':
      return '数据条'
    case 'cta.banner':
      return '行动号召'
    case 'footer.basic':
      return '页脚'
    default:
      return '区块'
  }
}

export function sanitizeFilename(name: string): string {
  return (
    name
      .trim()
      .replace(/[\\/:*?"<>|]+/g, '-')
      .replace(/\s+/g, '-')
      .slice(0, 60) || 'aster-page'
  )
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 2000)
}
