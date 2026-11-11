import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import {
  IconArrowLeft,
  IconDownload,
  IconLayout,
  IconLoader,
  IconMessage,
  IconSparkles,
} from '../components/Icons'
import { api, ApiError } from '../lib/api'
import { exportStaticSite } from '../lib/export'
import { useEditorStore } from '../store/editor'
import { CanvasPanel } from '../editor/CanvasPanel'
import { ChatPanel } from '../editor/ChatPanel'
import { InspectorPanel } from '../editor/InspectorPanel'

function initials(name: string): string {
  const trimmed = name.trim()
  return trimmed ? trimmed.slice(0, 2).toUpperCase() : 'A'
}

function EditorLoading() {
  return (
    <div className="editor-page editor-loading">
      <div className="editor-loading-head">
        <span className="brand-mark">
          <span className="brand-star">✦</span>
        </span>
        <span>Aster</span>
      </div>
      <div className="editor-loading-body">
        <span className="spinner dark" />
        <span>正在打开页面</span>
      </div>
    </div>
  )
}

export function EditorPage() {
  const { projectId } = useParams<{ projectId: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const spec = useEditorStore((state) => state.spec)
  const projectName = useEditorStore((state) => state.projectName)
  const loading = useEditorStore((state) => state.loading)
  const selection = useEditorStore((state) => state.selection)
  const saveStatus = useEditorStore((state) => state.saveStatus)
  const loadProject = useEditorStore((state) => state.loadProject)
  const reset = useEditorStore((state) => state.reset)
  const save = useEditorStore((state) => state.save)
  const undo = useEditorStore((state) => state.undo)
  const redo = useEditorStore((state) => state.redo)
  const [chatOpen, setChatOpen] = useState(false)
  const [inspectorOpen, setInspectorOpen] = useState(false)
  const [exporting, setExporting] = useState(false)

  useEffect(() => {
    if (!projectId) return
    let cancelled = false
    reset()
    api
      .getProject(projectId)
      .then((project) => {
        if (!cancelled) loadProject(project)
      })
      .catch((caught) => {
        if (cancelled) return
        window.dispatchEvent(
          new CustomEvent('aster:toast', {
            detail: {
              type: 'error',
              message: caught instanceof ApiError ? caught.message : '页面加载失败',
            },
          }),
        )
        navigate('/', { replace: true })
      })
    return () => {
      cancelled = true
    }
  }, [projectId, loadProject, navigate, reset])

  useEffect(() => {
    document.title = projectName ? `${projectName} · Aster` : 'Aster · 与 AI 一起做页面'
  }, [projectName])

  useEffect(() => {
    if (!selection) return
    if (window.matchMedia('(max-width: 1279px)').matches) {
      setInspectorOpen(true)
      setChatOpen(false)
    }
  }, [selection])

  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      const isTyping =
        target?.tagName === 'INPUT' ||
        target?.tagName === 'TEXTAREA' ||
        target?.isContentEditable
      const meta = event.metaKey || event.ctrlKey
      if (!meta) return
      if (event.key.toLowerCase() === 's') {
        event.preventDefault()
        void save()
        return
      }
      if (isTyping) return
      if (event.key.toLowerCase() === 'z') {
        event.preventDefault()
        if (event.shiftKey) redo()
        else undo()
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [redo, save, undo])

  useEffect(() => {
    if (saveStatus !== 'dirty' && saveStatus !== 'saving') return
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [saveStatus])

  const goBack = async () => {
    if (saveStatus === 'dirty' || saveStatus === 'saving') {
      const ok = await save()
      if (!ok) return
    }
    navigate('/')
  }

  const handleExport = async () => {
    if (!spec || exporting) return
    if (!spec.sections.length) {
      window.dispatchEvent(
        new CustomEvent('aster:toast', {
          detail: { type: 'info', message: '页面还没有内容，先和 Aster 聊一句' },
        }),
      )
      return
    }
    setExporting(true)
    try {
      if (saveStatus !== 'saved') {
        const ok = await save()
        if (!ok) return
      }
      await exportStaticSite(spec, projectName)
      window.dispatchEvent(
        new CustomEvent('aster:toast', {
          detail: { type: 'success', message: '静态页面已打包下载' },
        }),
      )
    } catch (caught) {
      window.dispatchEvent(
        new CustomEvent('aster:toast', {
          detail: {
            type: 'error',
            message: caught instanceof Error ? caught.message : '导出失败',
          },
        }),
      )
    } finally {
      setExporting(false)
    }
  }

  if (loading || !spec) return <EditorLoading />

  return (
    <div className="editor-page">
      <header className="editor-topbar">
        <div className="editor-topbar-left">
          <button type="button" className="icon-btn back-btn" onClick={() => void goBack()} title="返回项目列表">
            <IconArrowLeft />
          </button>
          <button type="button" className="editor-brand" onClick={() => navigate('/')}>
            <span className="brand-mark small">
              <span className="brand-star">✦</span>
            </span>
            <span>Aster</span>
          </button>
          <span className="editor-topbar-sep">/</span>
          <div className="editor-project">
            <strong>{projectName || '未命名页面'}</strong>
            <span>与 AI 一起编辑</span>
          </div>
        </div>

        <div className="editor-topbar-center">
          <span className={`topbar-save ${saveStatus}`}>
            <span className="status-dot" />
            {saveStatus === 'saved'
              ? '所有修改已保存'
              : saveStatus === 'saving'
                ? '正在保存'
                : saveStatus === 'error'
                  ? '保存失败'
                  : '有未保存修改'}
          </span>
        </div>

        <div className="editor-topbar-right">
          <button
            type="button"
            className="btn btn-ghost topbar-mobile-btn topbar-chat-btn"
            onClick={() => {
              setChatOpen((value) => !value)
              setInspectorOpen(false)
            }}
          >
            <IconMessage />
            对话
          </button>
          <button
            type="button"
            className="btn btn-ghost topbar-mobile-btn topbar-inspector-btn"
            onClick={() => {
              setInspectorOpen((value) => !value)
              setChatOpen(false)
            }}
          >
            <IconLayout />
            属性
          </button>
          <button
            type="button"
            className="btn btn-dark export-btn"
            onClick={() => void handleExport()}
            disabled={exporting}
          >
            {exporting ? <IconLoader className="spin" /> : <IconDownload />}
            {exporting ? '打包中' : '导出静态页'}
          </button>
          <div className="editor-avatar" title={user?.displayName || user?.email}>
            {initials(user?.displayName || user?.email || 'A')}
          </div>
        </div>
      </header>

      <div className={`editor-shell ${chatOpen ? 'chat-open' : ''} ${inspectorOpen ? 'inspector-open' : ''}`}>
        <ChatPanel onClose={() => setChatOpen(false)} />
        <CanvasPanel
          onOpenChat={() => {
            setChatOpen(true)
            setInspectorOpen(false)
          }}
        />
        <InspectorPanel onClose={() => setInspectorOpen(false)} />
        {(chatOpen || inspectorOpen) && (
          <button
            type="button"
            className="editor-drawer-backdrop"
            aria-label="关闭面板"
            onClick={() => {
              setChatOpen(false)
              setInspectorOpen(false)
            }}
          />
        )}
      </div>
    </div>
  )
}
