import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from '../auth/AuthContext'
import { IconArrowRight, IconSparkles } from '../components/Icons'
import { ApiError } from '../lib/api'

const loginSchema = z.object({
  email: z.string().trim().email('请输入有效的邮箱地址'),
  password: z.string().min(1, '请输入密码'),
})

const registerSchema = z.object({
  email: z.string().trim().email('请输入有效的邮箱地址'),
  password: z.string().min(8, '密码至少需要 8 位'),
  displayName: z.string().trim().min(1, '请输入昵称').max(40, '昵称不能超过 40 个字符'),
})

type Mode = 'login' | 'register'

export function LoginPage() {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const switchMode = (next: Mode) => {
    setMode(next)
    setError('')
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    const result =
      mode === 'login'
        ? loginSchema.safeParse({ email, password })
        : registerSchema.safeParse({ email, password, displayName })
    if (!result.success) {
      setError(result.error.issues[0]?.message || '请检查输入')
      return
    }

    setSubmitting(true)
    try {
      if (mode === 'login') {
        await login(email.trim(), password)
      } else {
        await register(email.trim(), password, displayName.trim())
        window.dispatchEvent(
          new CustomEvent('aster:toast', {
            detail: { type: 'success', message: '账号已创建，示例项目也准备好了' },
          }),
        )
      }
      navigate('/', { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '连接失败，请稍后再试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-visual">
        <div className="login-visual-grid" />
        <div className="login-visual-orbit orbit-a" />
        <div className="login-visual-orbit orbit-b" />
        <div className="login-brand">
          <span className="login-brand-mark">
            <span className="brand-star">✦</span>
          </span>
          <span>Aster</span>
        </div>

        <div className="login-visual-copy">
          <div className="login-kicker">
            <IconSparkles />
            AI 页面工作台
          </div>
          <h1>
            把想法说清楚，
            <br />
            剩下的交给画布。
          </h1>
          <p>对话生成、直接点击修改、自动保存、随时导出。页面不是一次性的结果，而是一块可以继续雕刻的材料。</p>
        </div>

        <div className="login-art">
          <div className="login-art-card card-main">
            <img src="/presets/visual-01.svg" alt="" />
            <div className="login-art-card-body">
              <span>午夜画廊 / 2026</span>
              <strong>让灵感，先于语言抵达。</strong>
            </div>
          </div>
          <div className="login-art-card card-small card-a">
            <img src="/presets/visual-03.svg" alt="" />
          </div>
          <div className="login-art-card card-small card-b">
            <img src="/presets/visual-04.svg" alt="" />
          </div>
          <div className="login-art-pill">
            <span className="status-dot" />
            可以直接改文字、颜色和图片
          </div>
        </div>

        <div className="login-visual-foot">
          <span>ASTER / WEB PRODUCE</span>
          <span>DESIGNED FOR ITERATION</span>
        </div>
      </section>

      <section className="login-panel">
        <div className="login-panel-head">
          <span className="login-panel-brand">
            <span className="brand-star">✦</span> Aster
          </span>
          <span className="login-panel-tag">与 AI 一起做页面</span>
        </div>

        <div className="auth-card">
          <div className="auth-tabs" role="tablist">
            <button
              type="button"
              className={mode === 'login' ? 'is-active' : ''}
              onClick={() => switchMode('login')}
              role="tab"
              aria-selected={mode === 'login'}
            >
              登录
            </button>
            <button
              type="button"
              className={mode === 'register' ? 'is-active' : ''}
              onClick={() => switchMode('register')}
              role="tab"
              aria-selected={mode === 'register'}
            >
              创建账号
            </button>
          </div>

          <div className="auth-heading">
            <h2>{mode === 'login' ? '继续你的页面' : '从第一版开始'}</h2>
            <p>
              {mode === 'login'
                ? '登录后回到你的项目，之前的对话和手工修改都在。'
                : '注册后会得到一个可以直接编辑的示例项目，不用从空白开始。'}
            </p>
          </div>

          <form onSubmit={handleSubmit} className="auth-form" noValidate>
            {mode === 'register' && (
              <label className="field">
                <span className="field-label">昵称</span>
                <input
                  type="text"
                  value={displayName}
                  onChange={(event) => setDisplayName(event.target.value)}
                  placeholder="怎么称呼你"
                  autoComplete="nickname"
                  maxLength={40}
                />
              </label>
            )}
            <label className="field">
              <span className="field-label">邮箱</span>
              <input
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="you@example.com"
                autoComplete="email"
              />
            </label>
            <label className="field">
              <span className="field-label">密码</span>
              <input
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder={mode === 'register' ? '至少 8 位' : '输入密码'}
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              />
            </label>

            {error && <div className="form-error">{error}</div>}

            <button type="submit" className="btn btn-primary auth-submit" disabled={submitting}>
              {submitting ? (
                <span className="spinner" />
              ) : (
                <>
                  {mode === 'login' ? '进入工作台' : '创建并打开示例'}
                  <IconArrowRight />
                </>
              )}
            </button>
          </form>

          <div className="auth-footnote">
            <span className="tiny-dot" />
            {mode === 'login' ? '还没有账号？' : '已经有账号？'}
            <button type="button" onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}>
              {mode === 'login' ? '创建一个' : '直接登录'}
            </button>
          </div>
        </div>

        <p className="login-legal">继续即表示你同意本地演示环境的使用条款。当前未配置 DeepSeek 时会自动使用本地演示模型。</p>
      </section>
    </main>
  )
}
