import { useEffect, useState } from 'react'
import { IconCheck, IconX } from './Icons'

interface ToastItem {
  id: number
  type: 'success' | 'error' | 'info'
  message: string
}

let toastId = 0

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent<{ type?: ToastItem['type']; message?: string }>).detail
      const message = detail?.message
      if (!message) return
      const id = ++toastId
      setItems((current) => [
        ...current.slice(-2),
        { id, type: detail.type || 'info', message },
      ])
      window.setTimeout(() => {
        setItems((current) => current.filter((item) => item.id !== id))
      }, 4200)
    }
    window.addEventListener('aster:toast', handler)
    return () => window.removeEventListener('aster:toast', handler)
  }, [])

  return (
    <>
      {children}
      <div className="toast-stack" aria-live="polite">
        {items.map((item) => (
          <div className={`toast toast-${item.type}`} key={item.id}>
            <span className="toast-icon">
              {item.type === 'error' ? <IconX /> : <IconCheck />}
            </span>
            <span>{item.message}</span>
            <button
              type="button"
              className="toast-close"
              onClick={() => setItems((current) => current.filter((entry) => entry.id !== item.id))}
              aria-label="关闭提示"
            >
              <IconX />
            </button>
          </div>
        ))}
      </div>
    </>
  )
}
