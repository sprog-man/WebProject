import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import {
  IconArrowRight,
  IconArrowUpRight,
  IconChevronRight,
  IconLogOut,
  IconPlus,
  IconRefresh,
  IconSettings,
  IconSparkles,
  IconTrash,
  IconX,
} from '../components/Icons'
import { api, ApiError } from '../lib/api'
import { formatRelativeTime, sectionLabel } from '../lib/utils'
import { PageRenderer } from '../renderer/PageRenderer'
import type { ProjectSummary } from '../types'

function initials(name: string): string {
  const trimmed = name.trim()
  if (!trimmed) return 'A'
  return trimmed.slice(0, 2).toUpperCase()
}

function ProjectCard({
  project,
  onOpen,
  onDelete,
}: {
  project: ProjectSummary
  onOpen: () => void
  onDelete: () => void
}) {
  const sectionCount = project.spec?.sections?.length || 0
  return (
    <article
      className="project-card"
      onClick={onOpen}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onOpen()
        }
      }}
      role="button"
      tabIndex={0}
    >
      <div className="project-thumb">
        <div className="project-thumb-inner" aria-hidden="true">
          <PageRenderer spec={project.spec} />
        </div>
        <div className="project-thumb-shade" />
        <span className="project-open-hint">
          打开编辑器 <IconArrowUpRight />
        </span>
        <button
          type="button"
          className="project-delete"
          title="删除页面"
          onClick={(event) => {
            event.stopPropagation()
            onDelete()
          }}
        >
          <IconTrash />
        </button>
      </div>
      <div className="project-card-body">
        <div>
          <h3>{project.name}</h3>
          <p>
            {sectionCount > 0
              ? project.spec.sections.map((section) => sectionLabel(section.component)).join(' · ')
              : '空白页面，等待第一次对话'}
          </p>
        </div>
        <div className="project-card-meta">
          <span>{formatRelativeTime(project.updatedAt)}</span>
          <IconChevronRight />
        </div>
      </div>
    </article>
  )
}

export function DashboardPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [projects, setProjects] = useState<ProjectSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [newName, setNewName] = useState('')
  const [creating, setCreating] = useState(false)

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      setProjects(await api.listProjects())
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '项目加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  const totalSections = useMemo(
    () => projects.reduce((sum, project) => sum + (project.spec?.sections?.length || 0), 0),
    [projects],
  )

  const createProject = async (event: FormEvent) => {
    event.preventDefault()
    const name = newName.trim()
    if (!name) return
    setCreating(true)
    try {
      const project = await api.createProject(name)
      navigate(`/projects/${project.id}`)
    } catch (caught) {
      window.dispatchEvent(
        new CustomEvent('aster:toast', {
          detail: {
            type: 'error',
            message: caught instanceof ApiError ? caught.message : '页面创建失败',
          },
        }),
      )
    } finally {
      setCreating(false)
    }
  }

  const deleteProject = async (project: ProjectSummary) => {
    if (!window.confirm(`删除「${project.name}」？这个操作不可撤销。`)) return
    try {
      await api.deleteProject(project.id)
      setProjects((current) => current.filter((item) => item.id !== project.id))
      window.dispatchEvent(
        new CustomEvent('aster:toast', {
          detail: { type: 'success', message: '页面已删除' },
        }),
      )
    } catch (caught) {
      window.dispatchEvent(
        new CustomEvent('aster:toast', {
          detail: {
            type: 'error',
            message: caught instanceof ApiError ? caught.message : '删除失败',
          },
        }),
      )
    }
  }

  return (
    <div className="dashboard-page">
      <header className="dash-nav">
        <div className="dash-brand" onClick={() => navigate('/')} role="button" tabIndex={0}>
          <span className="brand-mark">
            <span className="brand-star">✦</span>
          </span>
          <span>Aster</span>
          <span className="dash-brand-tag">WEB PRODUCE</span>
        </div>
        <nav className="dash-nav-links">
          <span className="is-active">工作台</span>
          <button type="button" onClick={() => navigate('/settings/ai')}>
            AI 设置
          </button>
          <button type="button" onClick={() => navigate('/settings/image-ai')}>
            图像 AI
          </button>
          <span>模板</span>
          <span>使用指南</span>
        </nav>
        <div className="dash-user">
          <div className="dash-avatar">{initials(user?.displayName || user?.email || 'A')}</div>
          <div className="dash-user-copy">
            <strong>{user?.displayName || '设计师'}</strong>
            <span>{user?.email}</span>
          </div>
          <button
            type="button"
            className="icon-btn dash-ai-settings-btn"
            onClick={() => navigate('/settings/ai')}
            title="AI 设置"
          >
            <IconSettings />
          </button>
          <button type="button" className="icon-btn" onClick={() => void logout()} title="退出登录">
            <IconLogOut />
          </button>
        </div>
      </header>

      <main className="dash-main">
        <section className="dash-hero">
          <div className="dash-hero-copy">
            <div className="dash-kicker">
              <IconSparkles />
              AI NATIVE PAGE STUDIO
            </div>
            <h1>
              把想法说清楚，
              <br />
              <em>剩下的交给画布。</em>
            </h1>
            <p>
              先生成一版能看的页面，再直接点选修改文字、图片、颜色和布局。
              每次微调都不需要重新生成整页。
            </p>
            <div className="dash-hero-actions">
              <button type="button" className="btn btn-primary btn-lg" onClick={() => setCreateOpen(true)}>
                <IconPlus />
                新建页面
              </button>
              <div className="dash-hero-stat">
                <strong>{String(projects.length).padStart(2, '0')}</strong>
                <span>个页面 · {totalSections} 个可编辑区块</span>
              </div>
            </div>
          </div>
          <div className="dash-hero-art" aria-hidden="true">
            <div className="hero-art-orbit" />
            <div className="hero-art-card hero-art-card-main">
              <img src="/presets/visual-01.svg" alt="" />
              <div>
                <span>EDITABLE / 01</span>
                <strong>让灵感，先于语言抵达。</strong>
              </div>
            </div>
            <div className="hero-art-card hero-art-card-small">
              <img src="/presets/visual-04.svg" alt="" />
            </div>
            <div className="hero-art-badge">
              <span className="status-dot" />
              不重新生成，也能改
            </div>
            <div className="hero-art-type">Aa</div>
          </div>
        </section>

        <section className="dash-projects">
          <div className="dash-section-head">
            <div>
              <span className="dash-section-kicker">YOUR PAGES</span>
              <h2>最近编辑</h2>
            </div>
            <div className="dash-section-actions">
              <button type="button" className="btn btn-ghost" onClick={() => void load()}>
                <IconRefresh />
                刷新
              </button>
              <button type="button" className="btn btn-dark" onClick={() => setCreateOpen(true)}>
                <IconPlus />
                新页面
              </button>
            </div>
          </div>

          {loading ? (
            <div className="project-grid">
              {[0, 1, 2].map((item) => (
                <div className="project-card project-skeleton" key={item}>
                  <div className="project-thumb" />
                  <div className="project-card-body">
                    <span className="skeleton-line" />
                    <span className="skeleton-line short" />
                  </div>
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
          ) : projects.length === 0 ? (
            <div className="dash-empty">
              <div className="dash-empty-mark">
                <IconSparkles />
              </div>
              <h3>还没有页面</h3>
              <p>创建第一页，告诉 Aster 你想做什么。</p>
              <button type="button" className="btn btn-primary" onClick={() => setCreateOpen(true)}>
                <IconPlus />
                新建页面
              </button>
            </div>
          ) : (
            <div className="project-grid">
              {projects.map((project) => (
                <ProjectCard
                  key={project.id}
                  project={project}
                  onOpen={() => navigate(`/projects/${project.id}`)}
                  onDelete={() => void deleteProject(project)}
                />
              ))}
              <button type="button" className="project-new-card" onClick={() => setCreateOpen(true)}>
                <span className="project-new-icon">
                  <IconPlus />
                </span>
                <strong>新建一个页面</strong>
                <span>从一句描述开始</span>
              </button>
            </div>
          )}
        </section>
      </main>

      {createOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setCreateOpen(false)}>
          <div
            className="modal-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="modal-head">
              <div>
                <span className="dash-section-kicker">NEW PAGE</span>
                <h2 id="create-title">给新页面起个名字</h2>
              </div>
              <button type="button" className="icon-btn" onClick={() => setCreateOpen(false)}>
                <IconX />
              </button>
            </div>
            <form onSubmit={createProject} className="modal-form">
              <label className="field">
                <span className="field-label">页面名称</span>
                <input
                  autoFocus
                  value={newName}
                  onChange={(event) => setNewName(event.target.value)}
                  placeholder="例如：Mono 咖啡品牌官网"
                  maxLength={120}
                />
              </label>
              <p className="modal-hint">
                创建后会进入编辑器。先和 Aster 聊一句，第一版页面就会出现在画布上。
              </p>
              <div className="modal-actions">
                <button type="button" className="btn btn-ghost" onClick={() => setCreateOpen(false)}>
                  取消
                </button>
                <button type="submit" className="btn btn-primary" disabled={creating || !newName.trim()}>
                  {creating ? <span className="spinner" /> : <>创建并开始 <IconArrowRight /></>}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
