import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import {
  IconEye,
  IconLoader,
  IconMonitor,
  IconPencil,
  IconRedo,
  IconSmartphone,
  IconSparkles,
  IconTablet,
  IconUndo,
} from '../components/Icons'
import { PageRenderer } from '../renderer/PageRenderer'
import { useEditorStore } from '../store/editor'
import type { DeviceMode } from '../types'
import { ImageGenerationPanel } from './ImageGenerationPanel'

const DEVICES: Array<{ id: DeviceMode; label: string; icon: React.ReactNode }> = [
  { id: 'desktop', label: '桌面', icon: <IconMonitor /> },
  { id: 'tablet', label: '平板', icon: <IconTablet /> },
  { id: 'mobile', label: '手机', icon: <IconSmartphone /> },
]

function SaveIndicator() {
  const saveStatus = useEditorStore((state) => state.saveStatus)
  if (saveStatus === 'saving') {
    return (
      <span className="save-indicator is-saving">
        <span className="spinner dark tiny" />
        保存中
      </span>
    )
  }
  if (saveStatus === 'error') {
    return (
      <span className="save-indicator is-error">
        <span className="status-dot error" />
        保存失败
      </span>
    )
  }
  if (saveStatus === 'dirty') {
    return (
      <span className="save-indicator is-dirty">
        <span className="status-dot warm" />
        等待保存
      </span>
    )
  }
  return (
    <span className="save-indicator is-saved">
      <span className="status-dot" />
      已自动保存
    </span>
  )
}

export function CanvasPanel({ onOpenChat }: { onOpenChat?: () => void }) {
  const spec = useEditorStore((state) => state.spec)
  const selection = useEditorStore((state) => state.selection)
  const editMode = useEditorStore((state) => state.editMode)
  const device = useEditorStore((state) => state.device)
  const sending = useEditorStore((state) => state.sending)
  const imageJobs = useEditorStore((state) => state.imageJobs)
  const projectName = useEditorStore((state) => state.projectName)
  const historyLength = useEditorStore((state) => state.history.length)
  const futureLength = useEditorStore((state) => state.future.length)
  const setDevice = useEditorStore((state) => state.setDevice)
  const select = useEditorStore((state) => state.select)
  const updateText = useEditorStore((state) => state.updateText)
  const undo = useEditorStore((state) => state.undo)
  const redo = useEditorStore((state) => state.redo)
  const toggleEditMode = useEditorStore((state) => state.toggleEditMode)

  const stageRef = useRef<HTMLDivElement | null>(null)
  const frameRef = useRef<HTMLDivElement | null>(null)
  const [stageWidth, setStageWidth] = useState(900)
  const [frameHeight, setFrameHeight] = useState(0)

  useEffect(() => {
    const node = stageRef.current
    if (!node) return
    const update = () => setStageWidth(node.clientWidth)
    update()
    const observer = new ResizeObserver(update)
    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    const node = frameRef.current
    if (!node) return
    const update = () => setFrameHeight(node.offsetHeight)
    update()
    const observer = new ResizeObserver(update)
    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  const { targetWidth, zoom } = useMemo(() => {
    const target = device === 'desktop' ? 1180 : device === 'tablet' ? 834 : 390
    const horizontalPadding = stageWidth < 760 ? 24 : 56
    const available = Math.max(260, stageWidth - horizontalPadding)
    return { targetWidth: target, zoom: Math.min(1, available / target) }
  }, [device, stageWidth])

  const frameStyle = {
    width: targetWidth,
    transform: `scale(${zoom})`,
  } as CSSProperties
  const shellStyle = {
    width: targetWidth * zoom,
    height: frameHeight ? frameHeight * zoom : undefined,
  }

  if (!spec) {
    return <section className="canvas-panel" />
  }

  return (
    <section className="canvas-panel">
      <div className="canvas-toolbar">
        <div className="canvas-toolbar-group">
          <div className="device-switch" role="group" aria-label="预览尺寸">
            {DEVICES.map((item) => (
              <button
                type="button"
                key={item.id}
                className={device === item.id ? 'is-active' : ''}
                onClick={() => setDevice(item.id)}
                title={item.label}
                aria-label={item.label}
              >
                {item.icon}
              </button>
            ))}
          </div>
          <span className="toolbar-divider" />
          <button
            type="button"
            className="icon-btn toolbar-btn"
            onClick={undo}
            disabled={historyLength === 0}
            title="撤销 (Ctrl/Cmd + Z)"
          >
            <IconUndo />
          </button>
          <button
            type="button"
            className="icon-btn toolbar-btn"
            onClick={redo}
            disabled={futureLength === 0}
            title="重做 (Shift + Ctrl/Cmd + Z)"
          >
            <IconRedo />
          </button>
        </div>

        <div className="canvas-toolbar-group">
          <button
            type="button"
            className={`edit-toggle ${editMode ? 'is-on' : ''}`}
            onClick={toggleEditMode}
            title={editMode ? '切换到预览' : '切换到编辑'}
          >
            {editMode ? <IconPencil /> : <IconEye />}
            {editMode ? '编辑中' : '预览'}
            <span className={`edit-toggle-track ${editMode ? 'is-on' : ''}`}>
              <span />
            </span>
          </button>
        </div>
      </div>

      <div
        ref={stageRef}
        className={`canvas-stage ${editMode ? 'is-editing' : ''} ${sending ? 'is-busy' : ''}`}
        onClick={() => select(null)}
      >
        <div className="canvas-device-label">
          <span>{device === 'desktop' ? 'Desk' : device === 'tablet' ? 'Tablet' : 'Mobile'}</span>
          <span>
            {targetWidth} px · {Math.round(zoom * 100)}%
          </span>
        </div>

        <div className="page-frame-shell" style={shellStyle}>
          <div
            ref={frameRef}
            className={`page-frame page-frame-${device} ${sending ? 'is-thinking' : ''}`}
            style={frameStyle}
            onClick={(event) => event.stopPropagation()}
          >
            {spec.sections.length > 0 ? (
              <PageRenderer
                spec={spec}
                editMode={editMode}
                selection={selection}
                onSelect={select}
                onCommitText={updateText}
                imageJobs={imageJobs}
              />
            ) : (
              <div className="canvas-empty">
                <span className="canvas-empty-mark">
                  <IconSparkles />
                </span>
                <span className="canvas-empty-kicker">EMPTY CANVAS</span>
                <h2>从一句话开始</h2>
                <p>告诉 Aster 你想做什么页面，第一版会直接出现在这里。之后可以点选任何文字、图片或数字继续修改。</p>
                {onOpenChat && (
                  <button type="button" className="btn btn-primary" onClick={onOpenChat}>
                    <IconSparkles />
                    开始描述页面
                  </button>
                )}
              </div>
            )}
          </div>
        </div>

        {sending && (
          <div className="canvas-thinking-badge">
            <IconLoader className="spin" />
            Aster 正在调整画布
          </div>
        )}
      </div>

      <ImageGenerationPanel />

      <div className="canvas-statusbar">
        <div className="canvas-status-left">
          <span className="status-dot" />
          <span>{projectName || '未命名页面'}</span>
          <span className="status-sep">/</span>
          <span>{spec.sections.length} 个区块</span>
        </div>
        <div className="canvas-status-right">
          {!editMode && (
            <button type="button" className="status-action" onClick={toggleEditMode}>
              <IconPencil />
              回到编辑
            </button>
          )}
          <span className="canvas-hint">
            {editMode ? '点击画布里的文字或图片直接修改' : '当前为预览模式'}
          </span>
          <SaveIndicator />
        </div>
      </div>
    </section>
  )
}
