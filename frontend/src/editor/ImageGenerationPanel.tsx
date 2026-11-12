import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { IconCheck, IconImage, IconLoader, IconSparkles, IconX } from '../components/Icons'
import { api, ApiError } from '../lib/api'
import { collectImages } from '../lib/utils'
import { useEditorStore } from '../store/editor'
import type { CreateImageJobInput, ImageJob } from '../types'

function toast(type: 'success' | 'error' | 'info', message: string) {
  window.dispatchEvent(new CustomEvent('aster:toast', { detail: { type, message } }))
}

function statusLabel(job: ImageJob): string {
  switch (job.status) {
    case 'PENDING':
      return '排队中'
    case 'RUNNING':
      return '生成中'
    case 'SUCCEEDED':
      return '已生成'
    case 'FAILED':
      return '生成失败'
    case 'SUPERSEDED':
      return '未覆盖'
    default:
      return job.status
  }
}

export function ImageGenerationPanel() {
  const navigate = useNavigate()
  const projectId = useEditorStore((state) => state.projectId)
  const spec = useEditorStore((state) => state.spec)
  const imageJobs = useEditorStore((state) => state.imageJobs)
  const imagePanelOpen = useEditorStore((state) => state.imagePanelOpen)
  const setImagePanelOpen = useEditorStore((state) => state.setImagePanelOpen)
  const refreshImageJobs = useEditorStore((state) => state.refreshImageJobs)
  const startImageGeneration = useEditorStore((state) => state.startImageGeneration)
  const updateImage = useEditorStore((state) => state.updateImage)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [prompts, setPrompts] = useState<Record<string, string>>({})
  const [hasConfig, setHasConfig] = useState<boolean | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const entries = useMemo(() => (spec ? collectImages(spec) : []), [spec])
  const signature = entries
    .map((entry) => `${entry.sectionId}:${entry.path}:${entry.image.prompt || ''}`)
    .join('|')

  useEffect(() => {
    if (!imagePanelOpen) return
    const defaults = new Set<string>()
    if (entries[0]) defaults.add(`${entries[0].sectionId}:${entries[0].path}`)
    setSelected(defaults)
    const nextPrompts: Record<string, string> = {}
    for (const entry of entries) {
      nextPrompts[`${entry.sectionId}:${entry.path}`] = entry.image.prompt || ''
    }
    setPrompts(nextPrompts)
    // Default selection is intentionally reset only when the panel opens, not on every poll.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [imagePanelOpen, signature])

  useEffect(() => {
    if (!imagePanelOpen) return
    void refreshImageJobs()
    api
      .imageAiConfigs()
      .then((configs) => setHasConfig(configs.some((config) => config.defaultConfig) || configs.length > 0))
      .catch(() => setHasConfig(null))
  }, [imagePanelOpen, refreshImageJobs])

  if (!spec || entries.length === 0) return null

  const keyFor = (sectionId: string, path: string) => `${sectionId}:${path}`
  const jobFor = (sectionId: string, path: string) =>
    imageJobs.find((job) => job.sectionId === sectionId && job.path === path)

  const trackedJobs = entries
    .map((entry) => jobFor(entry.sectionId, entry.path))
    .filter((job): job is ImageJob => Boolean(job))
  const activeCount = trackedJobs.filter(
    (job) => job.status === 'PENDING' || job.status === 'RUNNING',
  ).length
  const finishedCount = trackedJobs.filter(
    (job) => job.status === 'SUCCEEDED' || job.status === 'FAILED' || job.status === 'SUPERSEDED',
  ).length
  const progressPercent =
    trackedJobs.length === 0 ? 0 : Math.round((finishedCount / trackedJobs.length) * 100)

  const toggle = (key: string) => {
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  const generate = async () => {
    if (submitting) return
    const chosen = entries.filter((entry) => selected.has(keyFor(entry.sectionId, entry.path)))
    if (chosen.length === 0) {
      toast('info', '先勾选要生成的图片')
      return
    }
    const items: CreateImageJobInput[] = []
    for (const entry of chosen) {
      const key = keyFor(entry.sectionId, entry.path)
      const prompt = (prompts[key] ?? entry.image.prompt ?? '').trim()
      if (!prompt) {
        toast('info', `「${entry.label}」还没有提示词`)
        return
      }
      if (prompt !== (entry.image.prompt || '')) {
        updateImage(entry.sectionId, entry.path, { ...entry.image, prompt })
      }
      items.push({ sectionId: entry.sectionId, path: entry.path, prompt })
    }
    setSubmitting(true)
    const ok = await startImageGeneration(items)
    setSubmitting(false)
    if (!ok) return
    toast('success', '图片任务已提交，完成后会自动替换')
  }

  if (!imagePanelOpen) {
    return (
      <button
        type="button"
        className="image-gen-toggle"
        onClick={() => setImagePanelOpen(true)}
        title="生成页面图片"
      >
        <IconSparkles />
        AI 图片
        {imageJobs.some((job) => job.status === 'PENDING' || job.status === 'RUNNING') && (
          <span className="image-gen-toggle-dot" />
        )}
      </button>
    )
  }

  return (
    <aside className="image-gen-panel" aria-label="AI 图片生成">
      <header className="image-gen-head">
        <div>
          <span className="image-gen-kicker">AI IMAGE</span>
          <strong>生成页面图片</strong>
        </div>
        <button
          type="button"
          className="icon-btn"
          onClick={() => setImagePanelOpen(false)}
          aria-label="收起图片生成面板"
        >
          <IconX />
        </button>
      </header>

      <div className="image-gen-body">
        {hasConfig === false && (
          <div className="image-gen-warning">
            <IconImage />
            <p>还没有配置图像模型。先添加一个图像 AI，才能生成真实图片。</p>
            <button type="button" className="btn btn-ghost" onClick={() => navigate('/settings/image-ai')}>
              去配置
            </button>
          </div>
        )}

        <p className="image-gen-note">
          文本 AI 会为每张图写好提示词，也可以在这里修改。默认只勾选首屏，确认后再生成。每张图片都可能产生费用，生成失败会保留当前图片。
        </p>

        {activeCount > 0 && (
          <div className="image-gen-progress" aria-live="polite">
            <div className="image-gen-progress-label">
              <span>AI 正在生成图片</span>
              <strong>
                {finishedCount}/{trackedJobs.length}
              </strong>
            </div>
            <div className="image-gen-progress-track">
              <span style={{ width: `${progressPercent}%` }} />
            </div>
            <small>可以继续编辑页面，完成后会自动替换对应图片；失败会保留原图并显示原因。</small>
          </div>
        )}

        <div className="image-gen-list">
          {entries.map((entry) => {
            const key = keyFor(entry.sectionId, entry.path)
            const job = jobFor(entry.sectionId, entry.path)
            const active = job?.status === 'PENDING' || job?.status === 'RUNNING'
            const prompt = prompts[key] ?? entry.image.prompt ?? ''
            return (
              <article className={`image-gen-item ${selected.has(key) ? 'is-selected' : ''}`} key={key}>
                <label className="image-gen-item-head">
                  <input
                    type="checkbox"
                    checked={selected.has(key)}
                    disabled={active}
                    onChange={() => toggle(key)}
                  />
                  <span className="image-gen-thumb">
                    <img src={entry.image.src} alt="" />
                  </span>
                  <span className="image-gen-item-title">
                    <strong>{entry.label}</strong>
                    <small>{entry.sectionId} · {entry.path}</small>
                  </span>
                  {job && (
                    <span className={`image-gen-status is-${job.status.toLowerCase()}`}>
                      {job.status === 'RUNNING' || job.status === 'PENDING' ? (
                        <IconLoader className="spin tiny" />
                      ) : job.status === 'SUCCEEDED' ? (
                        <IconCheck />
                      ) : (
                        <IconX />
                      )}
                      {statusLabel(job)}
                    </span>
                  )}
                </label>

                <textarea
                  value={prompt}
                  onChange={(event) =>
                    setPrompts((current) => ({ ...current, [key]: event.target.value }))
                  }
                  placeholder="描述主体、构图、光线、材质和色彩。建议使用英文。"
                  maxLength={1500}
                  rows={4}
                  disabled={active}
                />

                {job?.errorMessage && (
                  <p className={`image-gen-error ${job.status === 'SUPERSEDED' ? 'is-muted' : ''}`}>
                    {job.errorMessage}
                  </p>
                )}
              </article>
            )
          })}
        </div>
      </div>

      <footer className="image-gen-actions">
        <span>
          {activeCount > 0 ? `${activeCount} 张生成中` : `${selected.size} 张已选`}
        </span>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => void generate()}
          disabled={submitting || selected.size === 0 || hasConfig === false || !projectId}
        >
          {submitting ? <IconLoader className="spin" /> : <IconSparkles />}
          {submitting ? '提交中' : '生成选中图片'}
        </button>
      </footer>
    </aside>
  )
}
