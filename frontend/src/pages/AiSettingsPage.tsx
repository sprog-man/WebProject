import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import {
  IconArrowLeft,
  IconCheck,
  IconGlobe,
  IconKey,
  IconLoader,
  IconLogOut,
  IconPlus,
  IconRefresh,
  IconServer,
  IconSettings,
  IconSparkles,
  IconTrash,
  IconX,
} from '../components/Icons'
import { api, ApiError } from '../lib/api'
import type {
  AiConfig,
  AiConfigTestResult,
  AiProviderPreset,
  AiProviderType,
} from '../types'

interface ConfigFormState {
  name: string
  providerType: AiProviderType
  baseUrl: string
  model: string
  apiKey: string
  defaultConfig: boolean
}

interface DialogState {
  mode: 'create' | 'edit'
  configId?: string
}

function initials(name: string): string {
  const trimmed = name.trim()
  if (!trimmed) return 'A'
  return trimmed.slice(0, 2).toUpperCase()
}

function toast(type: 'success' | 'error', message: string) {
  window.dispatchEvent(
    new CustomEvent('aster:toast', {
      detail: { type, message },
    }),
  )
}

export function AiSettingsPage() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()
  const [presets, setPresets] = useState<AiProviderPreset[]>([])
  const [configs, setConfigs] = useState<AiConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [dialog, setDialog] = useState<DialogState | null>(null)
  const [form, setForm] = useState<ConfigFormState>({
    name: '',
    providerType: 'OPENAI_COMPATIBLE',
    baseUrl: '',
    model: '',
    apiKey: '',
    defaultConfig: true,
  })
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [testingId, setTestingId] = useState<string | null>(null)
  const [testResults, setTestResults] = useState<Record<string, AiConfigTestResult>>({})
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const defaultConfig = configs.find((config) => config.defaultConfig)

  const providerLabel = (type: AiProviderType) =>
    presets.find((preset) => preset.type === type)?.label || type

  const load = async () => {
    setError('')
    try {
      const [nextPresets, nextConfigs] = await Promise.all([
        api.aiProviders(),
        api.aiConfigs(),
      ])
      setPresets(nextPresets)
      setConfigs(nextConfigs)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'AI 配置加载失败')
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
      name: first ? `${first.label} 配置` : '',
      providerType: first?.type || 'OPENAI_COMPATIBLE',
      baseUrl: first?.defaultBaseUrl || '',
      model: first?.defaultModel || '',
      apiKey: '',
      defaultConfig: configs.length === 0,
    })
    setFormError('')
    setDialog({ mode: 'create' })
  }

  const openEdit = (config: AiConfig) => {
    setForm({
      name: config.name,
      providerType: config.providerType,
      baseUrl: config.baseUrl,
      model: config.model,
      apiKey: '',
      defaultConfig: config.defaultConfig,
    })
    setFormError('')
    setDialog({ mode: 'edit', configId: config.id })
  }

  const changeProvider = (type: AiProviderType) => {
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
      setFormError('请填写模型名')
      return
    }
    setSaving(true)
    setFormError('')
    try {
      const input = {
        name,
        providerType: form.providerType,
        baseUrl,
        model,
        apiKey: form.apiKey.trim() || undefined,
        defaultConfig: form.defaultConfig,
      }
      if (dialog.mode === 'edit' && dialog.configId) {
        await api.updateAiConfig(dialog.configId, input)
        toast('success', 'AI 配置已更新')
      } else {
        await api.createAiConfig(input)
        toast('success', 'AI 配置已添加')
      }
      setDialog(null)
      await load()
    } catch (caught) {
      setFormError(caught instanceof ApiError ? caught.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  const test = async (config: AiConfig) => {
    setTestingId(config.id)
    try {
      const result = await api.testAiConfig(config.id)
      setTestResults((current) => ({ ...current, [config.id]: result }))
      toast(result.ok ? 'success' : 'error', result.ok ? `${config.name} 连接成功` : result.message)
    } catch (caught) {
      const message = caught instanceof ApiError ? caught.message : '连接测试失败'
      setTestResults((current) => ({
        ...current,
        [config.id]: { ok: false, message, latencyMs: 0, model: config.model },
      }))
      toast('error', message)
    } finally {
      setTestingId(null)
    }
  }

  const makeDefault = async (config: AiConfig) => {
    try {
      await api.setDefaultAiConfig(config.id)
      toast('success', `已将「${config.name}」设为默认 AI`)
      await load()
    } catch (caught) {
      toast('error', caught instanceof ApiError ? caught.message : '设置失败')
    }
  }

  const remove = async (config: AiConfig) => {
    if (!window.confirm(`删除「${config.name}」？删除后无法恢复。`)) return
    setDeletingId(config.id)
    try {
      await api.deleteAiConfig(config.id)
      setTestResults((current) => {
        const next = { ...current }
        delete next[config.id]
        return next
      })
      toast('success', 'AI 配置已删除')
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
          <span className="dash-brand-tag">AI PROVIDERS</span>
        </div>
        <nav className="dash-nav-links">
          <button type="button" onClick={() => navigate('/')}>
            工作台
          </button>
          <span className="is-active">文本 AI</span>
          <button type="button" onClick={() => navigate('/settings/image-ai')}>
            图像 AI
          </button>
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
              <IconSparkles />
              BRING YOUR OWN MODEL
            </div>
            <h1>
              用你自己的 AI，
              <br />
              <em>继续做页面。</em>
            </h1>
            <p>
              统一接入 OpenAI 兼容接口、Anthropic 和 Gemini。配置一次，之后所有项目的对话都会优先使用你的默认 AI。
              API Key 加密保存在服务端，不会返回给浏览器。
            </p>
          </div>
          <div className="ai-settings-status">
            <span className="ai-status-label">当前默认</span>
            {defaultConfig ? (
              <>
                <strong>{defaultConfig.name}</strong>
                <span>{providerLabel(defaultConfig.providerType)} · {defaultConfig.model}</span>
                <span className="ai-status-ok">
                  <span className="status-dot" />
                  已接管对话
                </span>
              </>
            ) : (
              <>
                <strong>系统默认</strong>
                <span>未配置时使用本地演示模型</span>
                <span className="ai-status-idle">
                  <span className="status-dot" />
                  随时可以接入
                </span>
              </>
            )}
          </div>
        </section>

        <section className="ai-settings-section">
          <div className="ai-settings-head">
            <div>
              <span className="dash-section-kicker">YOUR CONNECTIONS</span>
              <h2>我的 AI 配置</h2>
            </div>
            <div className="ai-settings-actions">
              <button type="button" className="btn btn-ghost" onClick={() => void load()}>
                <IconRefresh />
                刷新
              </button>
              <button type="button" className="btn btn-primary" onClick={openCreate}>
                <IconPlus />
                添加 AI
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
                <IconServer />
              </div>
              <h3>还没有接入 AI</h3>
              <p>
                不配置也可以继续使用本地演示模型。添加一个 OpenAI 兼容接口，就能让 DeepSeek、OpenAI、Moonshot、通义、Ollama 等模型接管生成。
              </p>
              <button type="button" className="btn btn-primary" onClick={openCreate}>
                <IconPlus />
                添加第一个 AI
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
                        {config.providerType === 'OPENAI_COMPATIBLE' ? <IconGlobe /> : <IconSparkles />}
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
                        <span>API Key</span>
                        <strong>{config.hasApiKey ? `•••• ${config.keyHint || ''}` : '未设置（本地模型可留空）'}</strong>
                      </div>
                    </div>

                    {result && (
                      <div className={`ai-test-result ${result.ok ? 'is-ok' : 'is-error'}`}>
                        {result.ok ? <IconCheck /> : <IconX />}
                        <span>{result.message}</span>
                        {result.ok && <em>{result.latencyMs}ms</em>}
                      </div>
                    )}

                    <div className="ai-config-card-actions">
                      <button
                        type="button"
                        className="btn btn-ghost"
                        onClick={() => void test(config)}
                        disabled={testingId === config.id}
                      >
                        {testingId === config.id ? <IconLoader className="spin" /> : <IconKey />}
                        {testingId === config.id ? '测试中' : '测试连接'}
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
                <strong>添加另一个 AI</strong>
                <small>可以为同一个账号保存多套模型配置</small>
              </button>
            </div>
          )}
        </section>

        <section className="ai-settings-note">
          <div className="ai-settings-note-icon">
            <IconKey />
          </div>
          <div>
            <strong>关于 API Key</strong>
            <p>
              密钥使用 AES-GCM 加密后存入数据库，接口只会返回末四位提示。生产环境请在服务端设置
              <code> APP_AI_ENCRYPTION_KEY </code>，并定期轮换。
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
          <div className="modal-card ai-config-modal" role="dialog" aria-modal="true" aria-labelledby="ai-config-dialog-title">
            <div className="modal-head">
              <div>
                <span className="dash-section-kicker">{dialog.mode === 'edit' ? 'EDIT CONNECTION' : 'NEW CONNECTION'}</span>
                <h2 id="ai-config-dialog-title">{dialog.mode === 'edit' ? '编辑 AI 配置' : '添加 AI 配置'}</h2>
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
                    placeholder="例如：我的 DeepSeek"
                    maxLength={80}
                    autoFocus
                  />
                </label>
                <label className="field">
                  <span className="field-label">服务商</span>
                  <select
                    value={form.providerType}
                    onChange={(event) => changeProvider(event.target.value as AiProviderType)}
                  >
                    {presets.map((preset) => (
                      <option value={preset.type} key={preset.type}>{preset.label}</option>
                    ))}
                  </select>
                </label>
              </div>

              <label className="field">
                <span className="field-label">Base URL</span>
                <input
                  value={form.baseUrl}
                  onChange={(event) => setForm((current) => ({ ...current, baseUrl: event.target.value }))}
                  placeholder="https://api.deepseek.com"
                  spellCheck={false}
                />
              </label>

              <label className="field">
                <span className="field-label">模型名（API 模型 ID）</span>
                <input
                  value={form.model}
                  onChange={(event) => setForm((current) => ({ ...current, model: event.target.value }))}
                  placeholder="例如 agnes-2.5-flash，不要填展示名称"
                  spellCheck={false}
                />
              </label>

              <label className="field">
                <span className="field-label">
                  API Key
                  {dialog.mode === 'edit' && <em className="ai-field-hint">留空则保持原 Key</em>}
                </span>
                <input
                  type="password"
                  value={form.apiKey}
                  onChange={(event) => setForm((current) => ({ ...current, apiKey: event.target.value }))}
                  placeholder={dialog.mode === 'edit' ? '已保存，留空不修改' : 'sk-...（本地模型可留空）'}
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
                  <strong>设为默认 AI</strong>
                  <small>之后所有项目的对话优先使用这套配置</small>
                </span>
              </label>

              <p className="modal-hint">
                OpenAI 兼容模式支持 DeepSeek、OpenAI、Moonshot、通义千问、智谱、OpenRouter、Groq、Ollama、vLLM 等服务。
                模型名必须填服务商文档里的 API 模型 ID，例如 <code>agnes-2.5-flash</code>，不要填「Agnes 2.5 Flash」这类展示名称。
                本地 Ollama 可填写 <code>http://localhost:11434/v1</code> 并留空 Key。
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
