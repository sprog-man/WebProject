import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useAuth } from '../auth/AuthContext'
import {
  IconArrowLeft,
  IconMessage,
  IconSend,
  IconSparkles,
  IconX,
} from '../components/Icons'
import { useEditorStore } from '../store/editor'

const EMPTY_PROMPTS = [
  '为一家独立咖啡品牌做官网首屏，安静、有手作感',
  '做一个 AI 产品的发布页，有未来感但不要冰冷',
  '为摄影工作室做作品集，留白多一点',
]

const EDIT_PROMPTS = [
  '把强调色换成更暖的橘红',
  '标题更大一点',
  '首屏居中排版',
  '隐藏底部的数据条',
  '换一版，更克制一点',
]

function initials(name: string): string {
  const trimmed = name.trim()
  return trimmed ? trimmed.slice(0, 2).toUpperCase() : 'U'
}

export function ChatPanel({ onClose }: { onClose?: () => void }) {
  const { user } = useAuth()
  const messages = useEditorStore((state) => state.messages)
  const sending = useEditorStore((state) => state.sending)
  const spec = useEditorStore((state) => state.spec)
  const projectName = useEditorStore((state) => state.projectName)
  const sendMessage = useEditorStore((state) => state.sendMessage)
  const [input, setInput] = useState('')
  const scrollRef = useRef<HTMLDivElement | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)

  const isEmpty = !spec?.sections.length
  const prompts = isEmpty ? EMPTY_PROMPTS : EDIT_PROMPTS

  useEffect(() => {
    const node = scrollRef.current
    if (!node) return
    node.scrollTo({ top: node.scrollHeight, behavior: 'smooth' })
  }, [messages, sending])

  useEffect(() => {
    const node = textareaRef.current
    if (!node) return
    node.style.height = 'auto'
    node.style.height = `${Math.min(node.scrollHeight, 132)}px`
  }, [input])

  const submit = async () => {
    const value = input.trim()
    if (!value || sending) return
    setInput('')
    await sendMessage(value)
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      void submit()
    }
  }

  return (
    <aside className="chat-panel">
      <header className="chat-head">
        <div className="chat-head-left">
          <span className="chat-avatar">
            <IconSparkles />
          </span>
          <div>
            <strong>Aster</strong>
            <span>你的 AI 设计搭档</span>
          </div>
        </div>
        <div className="chat-head-actions">
          <span className="chat-online">
            <span className="status-dot" />
            在线
          </span>
          {onClose && (
            <button type="button" className="icon-btn chat-close" onClick={onClose} aria-label="关闭对话">
              <IconX />
            </button>
          )}
        </div>
      </header>

      <div className="chat-context">
        <span className="chat-context-icon">
          <IconMessage />
        </span>
        <div>
          <span>正在编辑</span>
          <strong>{projectName || '未命名页面'}</strong>
        </div>
        <span className="chat-context-count">{spec?.sections.length || 0} 区块</span>
      </div>

      <div className="chat-messages" ref={scrollRef}>
        {messages.length === 0 && (
          <div className="chat-welcome">
            <span className="chat-welcome-mark">
              <IconSparkles />
            </span>
            <h3>先说说你想做什么</h3>
            <p>不用写提示词，像和设计师聊天一样描述品牌、气质和想让用户看到什么。</p>
          </div>
        )}

        {messages.map((message) => (
          <div className={`chat-message chat-message-${message.role}`} key={message.id}>
            {message.role === 'assistant' ? (
              <span className="message-avatar assistant">
                <IconSparkles />
              </span>
            ) : (
              <span className="message-avatar user">
                {initials(user?.displayName || user?.email || 'U')}
              </span>
            )}
            <div className="message-stack">
              <div className="message-meta">
                <strong>{message.role === 'assistant' ? 'Aster' : '你'}</strong>
                {message.role === 'assistant' && message.revisionNumber && (
                  <span className="revision-pill">画布 v{message.revisionNumber}</span>
                )}
              </div>
              <div className="message-bubble">{message.content}</div>
            </div>
          </div>
        ))}

        {sending && (
          <div className="chat-message chat-message-assistant">
            <span className="message-avatar assistant">
              <IconSparkles />
            </span>
            <div className="message-stack">
              <div className="message-meta">
                <strong>Aster</strong>
                <span className="thinking-label">正在调整画布</span>
              </div>
              <div className="typing-bubble" aria-label="Aster 正在思考">
                <span />
                <span />
                <span />
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="chat-composer-wrap">
        <div className="quick-prompts">
          {prompts.map((prompt) => (
            <button
              type="button"
              className="quick-prompt"
              key={prompt}
              onClick={() => setInput(prompt)}
              disabled={sending}
            >
              {prompt}
            </button>
          ))}
        </div>
        <div className={`chat-composer ${sending ? 'is-disabled' : ''}`}>
          <textarea
            ref={textareaRef}
            value={input}
            rows={1}
            placeholder={isEmpty ? '描述你想要的页面…' : '想改哪里？直接说…'}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={handleKeyDown}
            disabled={sending}
          />
          <button
            type="button"
            className="send-btn"
            onClick={() => void submit()}
            disabled={sending || !input.trim()}
            aria-label="发送"
          >
            {sending ? <span className="spinner light" /> : <IconSend />}
          </button>
        </div>
        <div className="composer-hint">
          <span>Enter 发送 · Shift + Enter 换行</span>
          <span className="composer-hint-right">
            <IconArrowLeft /> 手工修改会被保留
          </span>
        </div>
      </div>
    </aside>
  )
}
