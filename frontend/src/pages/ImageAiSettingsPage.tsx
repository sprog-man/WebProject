import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import {
  IconArrowLeft,
  IconCheck,
  IconImage,
  IconKey,
  IconLoader,
  IconLogOut,
  IconPlus,
  IconRefresh,
  IconSettings,
  IconSparkles,
  IconTrash,
  IconX,
} from '../components/Icons'
import { api, ApiError } from '../lib/api'
import type {
  ImageAiConfig,
  ImageAiConfigTestResult,
  ImageProviderPreset,
  ImageProviderType,
  SaveImageAiConfigInput,
} from '../types'

interface ImageConfigFormState {
  name: string
  providerType: ImageProviderType
  baseUrl: string
  model: string
  apiKey: string
  defaultSize: string
  defaultQuality: string
  defaultConfig: boolean
}

interface DialogState {
  mode: 'create' | 'edit'
  configId?: string
}

const SIZE_OPTIONS = ['1024x1024', '1536x1024', '1024x1536', '512x512']

function initials(name: string): string {
  const trimmed = name.trim()
  if (!trimmed) return 'A'
  return trimmed.slice(0, 2).toUpperCase()
}

function toast(type: 'success' | 'error', message: string) {
  window.dispatchEvent(new CustomEvent('aster:toast', { detail: { type, message } }))
}

export function ImageAiSettingsPage() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()
  const [presets, setPresets] = useState<ImageProviderPreset[]>([])
  const [configs, setConfigs] = useState<ImageAiConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [dialog, setDialog] = useState<DialogState | null>(null)
  const [form, setForm] = useState<ImageConfigFormState>({
    name: '',
    providerType: 'OPENAI_IMAGES',
    baseUrl: '',
    model: '',
    apiKey: '',
    defaultSize: '1024x1024',
    defaultQuality: '',
    defaultConfig: true,
  })
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [testingId, setTestingId] = useState<string | null>(null)
  const [testResults, setTestResults] = useState<Record<string, ImageAiConfigTestResult>>({})
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const defaultConfig = configs.find((config) => config.defaultConfig)

  const providerLabel = (type: ImageProviderType) =>
    presets.find((preset) => preset.type === type)?.label || type

  const load = async () => {
    setError('')
    try {
      const [nextPresets, nextConfigs] = await Promise.all([
        api.imageAiProviders(),
        api.imageAiConfigs(),
      ])
      setPresets(nextPresets)
      setConfigs(nextConfigs)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '图像 AI 配置加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  const openCreate = () => {
    const first = presets[0]
    setForm({
      name: first ? `${first.label} 图像配置` : '图像 AI 配置',
      providerType: first?.type || 'OPENAI_IMAGES',
      baseUrl: first?.defaultBaseUrl || 'https://apihub.agnes-ai.com/v1/images/generations',
      model: first?.defaultModel || 'agnes-image-2.5-flash',
      apiKey: '',
      defaultSize: '1024x1024',
      defaultQuality: '',
      defaultConfig: configs.length === 0,
    })
    setFormError('')
    setDialog({ mode: 'create' })
  }

  const openEdit = (config: ImageAiConfig) => {
    setForm({
      name: config.name,
      providerType: config.providerType,
      baseUrl: config.baseUrl,
      model: config.model,
      apiKey: '',
      defaultSize: config.defaultSize,
      defaultQuality: config.defaultQuality || '',
      defaultConfig: config.defaultConfig,
    })
    setFormError('')
    setDialog({ mode: 'edit', configId: config.id })
  }

  const changeProvider = (type: ImageProviderType) => {
    const preset = presets.find((item) => item.type === type)
    setForm((current) => ({
      ...current,
      providerType: type,
      baseUrl: preset?.defaultBaseUrl || current.baseUrl,
      model: preset?.defaultModel || current.model,
    }))
  }

  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!dialog) return
    const name = form.name.trim()
    const baseUrl = form.baseUrl.trim()
    const model = form.model.trim()
    if (!name) {
      setFormError('请填写配置名称')
      return
    }
    if (!baseUrl) {
      setFormError('请填写 Base URL')
      return
    }
    if (!model) {
      setFormError('请填写图像模型名')
      return
    }
    setSaving(true)
    setFormError('')
    try {
      const input: SaveImageAiConfigInput = {
        name,
        providerType: form.providerType,
        baseUrl,
        model,
        apiKey: form.apiKey.trim() || undefined,
        defaultSize: form.defaultSize,
        defaultQuality: form.defaultQuality.trim() || undefined,
        defaultConfig: form.defaultConfig,
      }
      if (dialog.mode === 'edit' && dialog.configId) {
        await api.updateImageAiConfig(dialog.configId, input)
        toast('success', '图像 AI 配置已更新')
      } else {
        await api.createImageAiConfig(input)
        toast('success', '图像 AI 配置已添加')
      }
      setDialog(null)
      await load()
    } catch (caught) {
      setFormError(caught instanceof ApiError ? caught.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  const test = async (config: ImageAiConfig) => {
    const confirmed = window.confirm(
      '测试连接会真实生成一张图片，可能产生费用。\n\n继续测试吗？',
    )
    if (!confirmed) return
    setTestingId(config.id)
    try {
      const result = await api.testImageAiConfig(config.id)
      setTestResults((current) => ({ ...current, [config.id]: result }))
      toast(
        result.ok ? 'success' : 'error',
        result.ok ? `${config.name} 测试图片生成成功` : result.message,
      )
    } catch (caught) {
      const message = caught instanceof ApiError ? caught.message : '图像连接测试失败'
      setTestResults((current) => ({
        ...current,
        [config.id]: {
          ok: false,
          message,
          latencyMs: 0,
          model: config.model,
          size: config.defaultSize,
          previewUrl: null,
          mimeType: null,
          width: null,
          height: null,
        },
      }))
      toast('error', message)
    } finally {
      setTestingId(null)
    }
  }

  const makeDefault = async (config: ImageAiConfig) => {
    try {
      await api.setDefaultImageAiConfig(config.id)
      toast('success', `已将「${config.name}」设为默认图像 AI`)
      await load()
    } catch (caught) {
      toast('error', caught instanceof ApiError ? caught.message : '设置失败')
    }
  }

  const remove = async (config: ImageAiConfig) => {
    if (!window.confirm(`删除「${config.name}」？删除后无法恢复。`)) return
    setDeletingId(config.id)
    try {
      await api.deleteImageAiConfig(config.id)
      setTestResults((current) => {
        const next = { ...current }
        delete next[config.id]
        return next
      })
      toast('success', '图像 AI 配置已删除')
      await load()
    } catch (caught) {
      toast('error', caught instanceof ApiError ? caught.message : '删除失败')
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <div className="ai-settings-page">
      <header className="dash-nav ai-settings-nav">
        <div className="dash-brand" onClick={() => navigate('/')} role="button" tabIndex={0}>
          <span className="brand-mark">
            <span className="brand-star">✦</span>
          </span>
          <span>Aster</span>
          <span className="dash-brand-tag">IMAGE PROVIDERS</span>
        </div>
        <nav className="dash-nav-links">
          <button type="button" onClick={() => navigate('/')}>
            工作台
          </button>
          <button type="button" onClick={() => navigate('/settings/ai')}>
            文本 AI
          </button>
          <span className="is-active">图像 AI</span>
        </nav>
        <div className="dash-user">
          <div className="dash-avatar">{initials(user?.displayName || user?.email || 'A')}</div>
          <div className="dash-user-copy">
            <strong>{user?.displayName || '设计师'}</strong>
            <span>{user?.email}</span>
          </div>
          <button type="button" className="icon-btn" onClick={() => void logout()} title="退出登录">
            <IconLogOut />
          </button>
        </div>
      </header>

      <main className="ai-settings-main">
        <button type="button" className="ai-back-link" onClick={() => navigate('/')}>
          <IconArrowLeft />
          返回工作台
        </button>

        <section className="ai-settings-hero">
          <div>
            <div className="dash-kicker">
              <IconImage />
              IMAGE GENERATION
            </div>
            <h1>
              让页面图片，
              <br />
              <em>也由 AI 完成。</em>
            </h1>
            <p>
              文本 AI 只负责提出图片提示词，真正的图片生成由这里配置的图像模型完成。
              生成任务在后台异步执行，完成后自动替换画布里的占位图。
            </p>
          </div>
          <div className="ai-settings-status">
            <span className="ai-status-label">当前默认图像模型</span>
            {defaultConfig ? (
              <>
                <strong>{defaultConfig.name}</strong>
                <span>{providerLabel(defaultConfig.providerType)} · {defaultConfig.model}</span>
                <span className="ai-status-ok">
                  <span className="status-dot" />
                  已接管图片生成
                </span>
              </>
            ) : (
              <>
                <strong>尚未配置</strong>
                <span>添加一个图像模型后才能生成图片</span>
                <span className="ai-status-idle">
                  <span className="status-dot" />
                  等待接入
                </span>
              </>
            )}
          </div>
        </section>

        <section className="ai-settings-section">
          <div className="ai-settings-head">
            <div>
              <span className="dash-section-kicker">YOUR IMAGE CONNECTIONS</span>
              <h2>我的图像 AI 配置</h2>
            </div>
            <div className="ai-settings-actions">
              <button type="button" className="btn btn-ghost" onClick={() => void load()}>
                <IconRefresh />
                刷新
              </button>
              <button type="button" className="btn btn-primary" onClick={openCreate}>
                <IconPlus />
                添加图像 AI
              </button>
            </div>
          </div>

          {loading ? (
            <div className="ai-config-grid">
              {[0, 1].map((item) => (
                <div className="ai-config-card ai-config-skeleton" key={item}>
                  <span className="skeleton-line" />
                  <span className="skeleton-line short" />
                  <span className="skeleton-line" />
                </div>
              ))}
            </div>
          ) : error ? (
            <div className="dash-error">
              <p>{error}</p>
              <button type="button" className="btn btn-dark" onClick={() => void load()}>
                重新加载
              </button>
            </div>
          ) : configs.length === 0 ? (
            <div className="ai-empty">
              <div className="ai-empty-icon">
                <IconImage />
              </div>
              <h3>还没有接入图像模型</h3>
              <p>
                配置一个兼容 OpenAI Images 协议的服务，文本 AI 写好的图片提示词就会真正变成页面图片。
                推荐先使用 Agnes 图像模型。
              </p>
              <button type="button" className="btn btn-primary" onClick={openCreate}>
                <IconPlus />
                添加第一个图像 AI
              </button>
            </div>
          ) : (
            <div className="ai-config-grid">
              {configs.map((config) => {
                const result = testResults[config.id]
                return (
                  <article className={`ai-config-card ${config.defaultConfig ? 'is-default' : ''}`} key={config.id}>
                    <div className="ai-config-card-head">
                      <div className="ai-provider-mark">
                        <IconImage />
                      </div>
                      <div className="ai-config-title">
                        <div>
                          <h3>{config.name}</h3>
                          {config.defaultConfig && <span className="ai-default-badge">默认</span>}
                        </div>
                        <span>{providerLabel(config.providerType)}</span>
                      </div>
                    </div>

                    <div className="ai-config-meta">
                      <div>
                        <span>模型</span>
                        <strong>{config.model}</strong>
                      </div>
                      <div>
                        <span>Base URL</span>
                        <strong title={config.baseUrl}>{config.baseUrl}</strong>
                      </div>
                      <div>
                        <span>默认尺寸 / 质量</span>
                        <strong>
                          {config.defaultSize}
                          {config.defaultQuality ? ` · ${config.defaultQuality}` : ''}
                        </strong>
                      </div>
                      <div>
                        <span>API Key</span>
                        <strong>{config.hasApiKey ? `•••• ${config.keyHint || ''}` : '未设置（本地服务可留空）'}</strong>
                      </div>
                    </div>

                    {result && (
                      <div className={`ai-test-result ${result.ok ? 'is-ok' : 'is-error'}`}>
                        {result.ok ? <IconCheck /> : <IconX />}
                        <span>{result.message}</span>
                        {result.ok && <em>{result.latencyMs}ms · {result.size}</em>}
                      </div>
                    )}

                    {result?.ok && result.previewUrl && (
                      <figure className="ai-test-preview">
                        <img src={result.previewUrl} alt="连接测试生成的真实图片" loading="lazy" />
                        <figcaption>
                          测试连接真实生成的图片
                          {result.width && result.height ? ` · ${result.width}×${result.height}` : ''}
                        </figcaption>
                      </figure>
                    )}

                    <div className="ai-config-card-actions">
                      <button
                        type="button"
                        className="btn btn-ghost"
                        onClick={() => void test(config)}
                        disabled={testingId === config.id}
                      >
                        {testingId === config.id ? <IconLoader className="spin" /> : <IconKey />}
                        {testingId === config.id ? '生成测试图' : '测试连接'}
                      </button>
                      {!config.defaultConfig && (
                        <button type="button" className="btn btn-ghost" onClick={() => void makeDefault(config)}>
                          设为默认
                        </button>
                      )}
                      <button type="button" className="ai-card-icon" onClick={() => openEdit(config)} title="编辑">
                        <IconSettings />
                      </button>
                      <button
                        type="button"
                        className="ai-card-icon danger"
                        onClick={() => void remove(config)}
                        disabled={deletingId === config.id}
                        title="删除"
                      >
                        {deletingId === config.id ? <IconLoader className="spin" /> : <IconTrash />}
                      </button>
                    </div>
                  </article>
                )
              })}

              <button type="button" className="ai-add-card" onClick={openCreate}>
                <span><IconPlus /></span>
                <strong>添加另一个图像 AI</strong>
                <small>可以为同一个账号保存多套图像模型配置</small>
              </button>
            </div>
          )}
        </section>

        <section className="ai-settings-note">
          <div className="ai-settings-note-icon">
            <IconKey />
          </div>
          <div>
            <strong>关于图像 Key 与费用</strong>
            <p>
              API Key 同样使用 AES-GCM 加密保存，接口只返回末四位。
              测试连接会真实生成一张图片，可能产生费用；生成失败时页面会保留原图，不影响文本 AI 的页面生成。
            </p>
          </div>
        </section>
      </main>

      {dialog && (
        <div
          className="modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget && !saving) setDialog(null)
          }}
        >
          <div className="modal-card ai-config-modal" role="dialog" aria-modal="true" aria-labelledby="image-config-dialog-title">
            <div className="modal-head">
              <div>
                <span className="dash-section-kicker">{dialog.mode === 'edit' ? 'EDIT IMAGE CONNECTION' : 'NEW IMAGE CONNECTION'}</span>
                <h2 id="image-config-dialog-title">{dialog.mode === 'edit' ? '编辑图像 AI 配置' : '添加图像 AI 配置'}</h2>
              </div>
              <button type="button" className="icon-btn" onClick={() => setDialog(null)} disabled={saving} title="关闭">
                <IconX />
              </button>
            </div>

            <form className="modal-form ai-config-form" onSubmit={(event) => void save(event)}>
              <div className="ai-form-grid">
                <label className="field">
                  <span className="field-label">配置名称</span>
                  <input
                    value={form.name}
                    onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                    placeholder="例如：我的 Agnes 图像模型"
                    maxLength={80}
                    autoFocus
                  />
                </label>
                <label className="field">
                  <span className="field-label">协议</span>
                  <select
                    value={form.providerType}
                    onChange={(event) => changeProvider(event.target.value as ImageProviderType)}
                  >
                    {presets.map((preset) => (
                      <option value={preset.type} key={preset.type}>{preset.label}</option>
                    ))}
                  </select>
                </label>
              </div>

              <label className="field">
                <span className="field-label">Base URL（图像端点）</span>
                <input
                  value={form.baseUrl}
                  onChange={(event) => setForm((current) => ({ ...current, baseUrl: event.target.value }))}
                  placeholder="https://apihub.agnes-ai.com/v1/images/generations"
                  spellCheck={false}
                />
              </label>

              <label className="field">
                <span className="field-label">图像模型名（API 模型 ID）</span>
                <input
                  value={form.model}
                  onChange={(event) => setForm((current) => ({ ...current, model: event.target.value }))}
                  placeholder="例如 agnes-image-2.5-flash"
                  spellCheck={false}
                />
              </label>

              <div className="ai-form-grid">
                <label className="field">
                  <span className="field-label">默认尺寸</span>
                  <select
                    value={form.defaultSize}
                    onChange={(event) => setForm((current) => ({ ...current, defaultSize: event.target.value }))}
                  >
                    {SIZE_OPTIONS.map((size) => (
                      <option value={size} key={size}>{size}</option>
                    ))}
                  </select>
                </label>
                <label className="field">
                  <span className="field-label">
                    质量参数
                    <em className="ai-field-hint">可选</em>
                  </span>
                  <input
                    value={form.defaultQuality}
                    onChange={(event) => setForm((current) => ({ ...current, defaultQuality: event.target.value }))}
                    placeholder="例如 standard / hd；留空则不发送"
                    spellCheck={false}
                  />
                </label>
              </div>

              <label className="field">
                <span className="field-label">
                  API Key
                  {dialog.mode === 'edit' && <em className="ai-field-hint">留空则保持原 Key</em>}
                </span>
                <input
                  type="password"
                  value={form.apiKey}
                  onChange={(event) => setForm((current) => ({ ...current, apiKey: event.target.value }))}
                  placeholder={dialog.mode === 'edit' ? '已保存，留空不修改' : 'sk-...（本地服务可留空）'}
                  autoComplete="new-password"
                />
              </label>

              <label className="ai-checkbox">
                <input
                  type="checkbox"
                  checked={form.defaultConfig}
                  onChange={(event) => setForm((current) => ({ ...current, defaultConfig: event.target.checked }))}
                />
                <span>
                  <strong>设为默认图像 AI</strong>
                  <small>所有项目的图片生成优先使用这套配置</small>
                </span>
              </label>

              <p className="modal-hint">
                首版接入 OpenAI Images 协议，兼容返回图片 URL 或 Base64 的服务。
                模型名保持可编辑，例如 <code>agnes-image-2.5-flash</code>。测试连接会生成一张测试图，可能产生费用。
              </p>

              {formError && <div className="form-error">{formError}</div>}

              <div className="modal-actions">
                <button type="button" className="btn btn-ghost" onClick={() => setDialog(null)} disabled={saving}>
                  取消
                </button>
                <button type="submit" className="btn btn-primary" disabled={saving}>
                  {saving ? <IconLoader className="spin" /> : <IconCheck />}
                  {saving ? '保存中' : '保存配置'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
