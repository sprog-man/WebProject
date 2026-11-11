import JSZip from 'jszip'
import { renderToStaticMarkup } from 'react-dom/server'
import pageCss from '../renderer/page.css?raw'
import { PageRenderer } from '../renderer/PageRenderer'
import type { PageSpec } from '../types'
import { cloneSpec, collectImages, downloadBlob, getAtPath, sanitizeFilename } from './utils'

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

function extensionFor(blob: Blob, src: string): string {
  const type = blob.type.toLowerCase()
  if (type.includes('svg')) return 'svg'
  if (type.includes('png')) return 'png'
  if (type.includes('jpeg') || type.includes('jpg')) return 'jpg'
  if (type.includes('webp')) return 'webp'
  if (type.includes('gif')) return 'gif'
  const match = src.match(/\.([a-zA-Z0-9]{2,5})(?:[?#]|$)/)
  return match ? match[1].toLowerCase() : 'bin'
}

export async function exportStaticSite(spec: PageSpec, projectName: string): Promise<void> {
  if (!spec.sections.length) {
    throw new Error('页面还没有内容，先和 Aster 聊聊')
  }

  const exported = cloneSpec(spec)
  const images = collectImages(exported)
  const zip = new JSZip()
  const downloaded = new Map<string, string>()

  for (const [index, entry] of images.entries()) {
    const original = entry.image?.src
    if (!original || original.startsWith('data:')) continue
    if (downloaded.has(original)) {
      entry.image.src = downloaded.get(original)!
      continue
    }

    const response = await fetch(original, { credentials: 'include' })
    if (!response.ok) {
      throw new Error(`图片读取失败：${entry.label}`)
    }
    const blob = await response.blob()
    const path = `assets/image-${index + 1}.${extensionFor(blob, original)}`
    zip.file(path, blob)
    downloaded.set(original, path)

    const section = exported.sections.find((item) => item.id === entry.sectionId)
    if (section) {
      const image = getAtPath(section, entry.path) as { src?: string } | undefined
      if (image) image.src = path
    }
  }

  const markup = renderToStaticMarkup(<PageRenderer spec={exported} />)
  const title = escapeHtml(exported.meta.title || projectName)
  const description = escapeHtml(exported.meta.description || '')
  const html = `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>${title}</title>
  <meta name="description" content="${description}" />
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Instrument+Serif:ital@0;1&family=Noto+Serif+SC:wght@400;500;600;700&family=Noto+Sans+SC:wght@400;500;600;700&display=swap" rel="stylesheet" />
  <style>html,body{margin:0;padding:0;background:#0d0d0f;min-height:100%;}${pageCss}</style>
</head>
<body>
${markup}
</body>
</html>`

  zip.file('index.html', html)
  const blob = await zip.generateAsync({ type: 'blob' })
  downloadBlob(blob, `${sanitizeFilename(projectName)}.zip`)
}
