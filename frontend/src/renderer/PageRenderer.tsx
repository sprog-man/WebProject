import {
  useEffect,
  useRef,
  type ClipboardEvent,
  type CSSProperties,
  type ElementType,
  type KeyboardEvent,
  type MouseEvent,
  type ReactNode,
} from 'react'
import type { ImageRef, PageSpec, SectionSpec, Selection } from '../types'

interface PageRendererProps {
  spec: PageSpec
  editMode?: boolean
  selection?: Selection | null
  onSelect?: (selection: Selection) => void
  onCommitText?: (sectionId: string, path: string, value: string) => void
}

interface SectionProps {
  section: SectionSpec
  editMode: boolean
  selection: Selection | null
  onSelect?: (selection: Selection) => void
  onCommitText?: (sectionId: string, path: string, value: string) => void
}

interface EditableTextProps {
  as?: ElementType
  value: string
  sectionId: string
  path: string
  label: string
  className?: string
  multiline?: boolean
  editMode: boolean
  selected: boolean
  onSelect?: (selection: Selection) => void
  onCommitText?: (sectionId: string, path: string, value: string) => void
  children?: ReactNode
}

function EditableText({
  as: Tag = 'span',
  value,
  sectionId,
  path,
  label,
  className = '',
  multiline = false,
  editMode,
  selected,
  onSelect,
  onCommitText,
}: EditableTextProps) {
  const ref = useRef<HTMLElement | null>(null)

  useEffect(() => {
    if (!ref.current) return
    if (document.activeElement === ref.current) return
    if (ref.current.innerText !== value) {
      ref.current.innerText = value
    }
  }, [value])

  const select = () => {
    if (!editMode) return
    onSelect?.({ sectionId, path, kind: 'text', label })
  }

  const commit = () => {
    if (!ref.current || !editMode) return
    let next = ref.current.innerText.replace(/\r/g, '')
    if (!multiline) next = next.replace(/\n+/g, ' ').trim()
    else next = next.replace(/\n{3,}/g, '\n\n').replace(/\n+$/, '')
    if (next.trim() === '' && value.trim() !== '') {
      ref.current.innerText = value
      return
    }
    if (next !== value) {
      onCommitText?.(sectionId, path, next)
    }
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      if (ref.current) ref.current.innerText = value
      ref.current?.blur()
      return
    }
    if (event.key === 'Enter' && !multiline) {
      event.preventDefault()
      ref.current?.blur()
    }
  }

  return (
    <Tag
      ref={ref as never}
      className={`page-editable ${selected && editMode ? 'is-selected' : ''} ${className}`.trim()}
      contentEditable={editMode || undefined}
      suppressContentEditableWarning
      spellCheck={false}
      data-path={path}
      aria-label={editMode ? `编辑${label}` : undefined}
      onFocus={select}
      onClick={(event: MouseEvent<HTMLElement>) => {
        if (editMode) {
          event.stopPropagation()
          select()
        }
      }}
      onBlur={commit}
      onKeyDown={handleKeyDown}
      onPaste={(event: ClipboardEvent<HTMLElement>) => {
        if (!editMode) return
        event.preventDefault()
        const text = event.clipboardData.getData('text/plain')
        document.execCommand('insertText', false, text)
      }}
      style={{ whiteSpace: multiline ? 'pre-line' : undefined }}
    >
      {value}
    </Tag>
  )
}

interface EditableImageProps {
  image: ImageRef
  sectionId: string
  path: string
  label: string
  editMode: boolean
  selected: boolean
  onSelect?: (selection: Selection) => void
}

function EditableImage({
  image,
  sectionId,
  path,
  label,
  editMode,
  selected,
  onSelect,
}: EditableImageProps) {
  const select = () => {
    if (!editMode) return
    onSelect?.({ sectionId, path, kind: 'image', label })
  }

  return (
    <div
      className={`page-img-wrap ${selected && editMode ? 'is-selected' : ''}`}
      onClick={(event) => {
        if (!editMode) return
        event.stopPropagation()
        select()
      }}
      onKeyDown={(event) => {
        if (!editMode) return
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          select()
        }
      }}
      role={editMode ? 'button' : undefined}
      tabIndex={editMode ? 0 : undefined}
      aria-label={editMode ? `更换${label}` : undefined}
    >
      <img
        src={image.src}
        alt={image.alt || label}
        draggable={false}
        style={{ objectFit: image.fit || 'cover' }}
      />
      {editMode && (
        <button type="button" className="page-image-edit" onClick={select}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
            <rect x="3" y="5" width="18" height="14" rx="2" />
            <circle cx="8.5" cy="10" r="1.5" />
            <path d="m21 16-4.5-4.5L9 19" />
          </svg>
          更换图片
        </button>
      )}
    </div>
  )
}

function NavSection({ section, editMode, selection, onSelect, onCommitText }: SectionProps) {
  const props = section.props
  const isSelected = (path: string) => selection?.sectionId === section.id && selection.path === path
  return (
    <header className="page-nav">
      <EditableText
        as="div"
        value={props.brand || ''}
        sectionId={section.id}
        path="props.brand"
        label="品牌名"
        className="page-brand"
        editMode={editMode}
        selected={isSelected('props.brand')}
        onSelect={onSelect}
        onCommitText={onCommitText}
      />
      <nav className="page-nav-links" aria-label="页面导航">
        {(props.links || []).map((link: string, index: number) => (
          <span key={`${link}-${index}`}>{link}</span>
        ))}
      </nav>
      <EditableText
        as="div"
        value={props.note || ''}
        sectionId={section.id}
        path="props.note"
        label="导航备注"
        className="page-nav-note"
        editMode={editMode}
        selected={isSelected('props.note')}
        onSelect={onSelect}
        onCommitText={onCommitText}
      />
    </header>
  )
}

function HeroSection({ section, editMode, selection, onSelect, onCommitText }: SectionProps) {
  const props = section.props
  const align = section.layout?.align === 'center' ? 'center' : 'left'
  const mediaSide = section.layout?.mediaSide === 'left' ? 'left' : 'right'
  const isSelected = (path: string) => selection?.sectionId === section.id && selection.path === path
  return (
    <section
      className={`page-hero ${align === 'center' ? 'is-center' : ''} ${mediaSide === 'left' ? 'media-left' : ''}`}
    >
      <div className="page-hero-copy">
        <EditableText
          as="div"
          value={props.eyebrow || ''}
          sectionId={section.id}
          path="props.eyebrow"
          label="首屏眉题"
          className="page-eyebrow"
          editMode={editMode}
          selected={isSelected('props.eyebrow')}
          onSelect={onSelect}
          onCommitText={onCommitText}
        />
        <EditableText
          as="h1"
          value={props.title || ''}
          sectionId={section.id}
          path="props.title"
          label="首屏标题"
          className="page-hero-title"
          multiline
          editMode={editMode}
          selected={isSelected('props.title')}
          onSelect={onSelect}
          onCommitText={onCommitText}
        />
        <EditableText
          as="p"
          value={props.subtitle || ''}
          sectionId={section.id}
          path="props.subtitle"
          label="首屏副标题"
          className="page-hero-subtitle"
          multiline
          editMode={editMode}
          selected={isSelected('props.subtitle')}
          onSelect={onSelect}
          onCommitText={onCommitText}
        />
        <div className="page-hero-actions">
          <span className="page-btn">
            <EditableText
              value={props.ctaLabel || ''}
              sectionId={section.id}
              path="props.ctaLabel"
              label="主按钮文案"
              editMode={editMode}
              selected={isSelected('props.ctaLabel')}
              onSelect={onSelect}
              onCommitText={onCommitText}
            />
          </span>
          <span className="page-btn secondary">
            <EditableText
              value={props.secondaryLabel || ''}
              sectionId={section.id}
              path="props.secondaryLabel"
              label="次按钮文案"
              editMode={editMode}
              selected={isSelected('props.secondaryLabel')}
              onSelect={onSelect}
              onCommitText={onCommitText}
            />
          </span>
        </div>
      </div>
      <div className="page-hero-media">
        <EditableImage
          image={props.image || { src: '/presets/visual-01.svg', alt: '' }}
          sectionId={section.id}
          path="props.image"
          label="首屏主图"
          editMode={editMode}
          selected={isSelected('props.image')}
          onSelect={onSelect}
        />
        <span className="page-media-label">{props.mediaLabel || 'Aurora / 2026'}</span>
      </div>
    </section>
  )
}

function GallerySection({ section, editMode, selection, onSelect, onCommitText }: SectionProps) {
  const props = section.props
  const items: any[] = Array.isArray(props.items) ? props.items : []
  const columns = Number(section.layout?.columns) === 2 ? 2 : 3
  const isSelected = (path: string) => selection?.sectionId === section.id && selection.path === path
  return (
    <section className="page-gallery">
      <div className="page-gallery-head">
        <div>
          <EditableText
            as="div"
            value={props.eyebrow || ''}
            sectionId={section.id}
            path="props.eyebrow"
            label="作品区眉题"
            className="page-eyebrow"
            editMode={editMode}
            selected={isSelected('props.eyebrow')}
            onSelect={onSelect}
            onCommitText={onCommitText}
          />
          <EditableText
            as="h2"
            value={props.title || ''}
            sectionId={section.id}
            path="props.title"
            label="作品区标题"
            className="page-gallery-title"
            multiline
            editMode={editMode}
            selected={isSelected('props.title')}
            onSelect={onSelect}
            onCommitText={onCommitText}
          />
        </div>
        <EditableText
          as="p"
          value={props.subtitle || ''}
          sectionId={section.id}
          path="props.subtitle"
          label="作品区说明"
          className="page-gallery-subtitle"
          multiline
          editMode={editMode}
          selected={isSelected('props.subtitle')}
          onSelect={onSelect}
          onCommitText={onCommitText}
        />
      </div>
      <div className="page-gallery-grid" style={{ '--columns': columns } as CSSProperties}>
        {items.map((item, index) => {
          const base = `props.items.${index}`
          return (
            <article className="page-gallery-card" key={index}>
              <div className="page-gallery-card-media">
                <EditableImage
                  image={item.image || { src: '/presets/visual-02.svg', alt: '' }}
                  sectionId={section.id}
                  path={`${base}.image`}
                  label={`作品图 ${index + 1}`}
                  editMode={editMode}
                  selected={isSelected(`${base}.image`)}
                  onSelect={onSelect}
                />
              </div>
              <div className="page-gallery-card-body">
                <EditableText
                  as="div"
                  value={item.tag || ''}
                  sectionId={section.id}
                  path={`${base}.tag`}
                  label={`作品 ${index + 1} 分类`}
                  className="page-gallery-tag"
                  editMode={editMode}
                  selected={isSelected(`${base}.tag`)}
                  onSelect={onSelect}
                  onCommitText={onCommitText}
                />
                <EditableText
                  as="h3"
                  value={item.title || ''}
                  sectionId={section.id}
                  path={`${base}.title`}
                  label={`作品 ${index + 1} 标题`}
                  className="page-gallery-card-title"
                  editMode={editMode}
                  selected={isSelected(`${base}.title`)}
                  onSelect={onSelect}
                  onCommitText={onCommitText}
                />
                <EditableText
                  as="p"
                  value={item.caption || ''}
                  sectionId={section.id}
                  path={`${base}.caption`}
                  label={`作品 ${index + 1} 描述`}
                  className="page-gallery-caption"
                  multiline
                  editMode={editMode}
                  selected={isSelected(`${base}.caption`)}
                  onSelect={onSelect}
                  onCommitText={onCommitText}
                />
              </div>
            </article>
          )
        })}
      </div>
    </section>
  )
}

function StatsSection({ section, editMode, selection, onSelect, onCommitText }: SectionProps) {
  const items: any[] = Array.isArray(section.props.items) ? section.props.items : []
  const isSelected = (path: string) => selection?.sectionId === section.id && selection.path === path
  return (
    <section className="page-stats">
      {items.map((item, index) => {
        const base = `props.items.${index}`
        return (
          <div className="page-stat" key={index}>
            <EditableText
              as="span"
              value={item.value || ''}
              sectionId={section.id}
              path={`${base}.value`}
              label={`数据 ${index + 1} 数值`}
              className="page-stat-value"
              editMode={editMode}
              selected={isSelected(`${base}.value`)}
              onSelect={onSelect}
              onCommitText={onCommitText}
            />
            <EditableText
              as="span"
              value={item.label || ''}
              sectionId={section.id}
              path={`${base}.label`}
              label={`数据 ${index + 1} 说明`}
              className="page-stat-label"
              editMode={editMode}
              selected={isSelected(`${base}.label`)}
              onSelect={onSelect}
              onCommitText={onCommitText}
            />
          </div>
        )
      })}
    </section>
  )
}

function CtaSection({ section, editMode, selection, onSelect, onCommitText }: SectionProps) {
  const props = section.props
  const isSelected = (path: string) => selection?.sectionId === section.id && selection.path === path
  return (
    <section className="page-cta">
      <EditableText
        as="div"
        value={props.eyebrow || ''}
        sectionId={section.id}
        path="props.eyebrow"
        label="行动区眉题"
        className="page-eyebrow"
        editMode={editMode}
        selected={isSelected('props.eyebrow')}
        onSelect={onSelect}
        onCommitText={onCommitText}
      />
      <EditableText
        as="h2"
        value={props.title || ''}
        sectionId={section.id}
        path="props.title"
        label="行动区标题"
        className="page-cta-title"
        multiline
        editMode={editMode}
        selected={isSelected('props.title')}
        onSelect={onSelect}
        onCommitText={onCommitText}
      />
      <EditableText
        as="p"
        value={props.subtitle || ''}
        sectionId={section.id}
        path="props.subtitle"
        label="行动区说明"
        className="page-cta-subtitle"
        multiline
        editMode={editMode}
        selected={isSelected('props.subtitle')}
        onSelect={onSelect}
        onCommitText={onCommitText}
      />
      <span className="page-btn">
        <EditableText
          value={props.ctaLabel || ''}
          sectionId={section.id}
          path="props.ctaLabel"
          label="行动区按钮文案"
          editMode={editMode}
          selected={isSelected('props.ctaLabel')}
          onSelect={onSelect}
          onCommitText={onCommitText}
        />
      </span>
    </section>
  )
}

function FooterSection({ section, editMode, selection, onSelect, onCommitText }: SectionProps) {
  const props = section.props
  const isSelected = (path: string) => selection?.sectionId === section.id && selection.path === path
  return (
    <footer className="page-footer">
      <div>
        <EditableText
          as="div"
          value={props.brand || ''}
          sectionId={section.id}
          path="props.brand"
          label="页脚品牌"
          className="page-footer-brand"
          editMode={editMode}
          selected={isSelected('props.brand')}
          onSelect={onSelect}
          onCommitText={onCommitText}
        />
        <EditableText
          as="div"
          value={props.note || ''}
          sectionId={section.id}
          path="props.note"
          label="页脚备注"
          className="page-footer-note"
          editMode={editMode}
          selected={isSelected('props.note')}
          onSelect={onSelect}
          onCommitText={onCommitText}
        />
      </div>
      <EditableText
        as="div"
        value={props.copyright || ''}
        sectionId={section.id}
        path="props.copyright"
        label="版权信息"
        className="page-footer-copy"
        editMode={editMode}
        selected={isSelected('props.copyright')}
        onSelect={onSelect}
        onCommitText={onCommitText}
      />
    </footer>
  )
}

function SectionRenderer(props: SectionProps) {
  switch (props.section.component) {
    case 'nav.basic':
      return <NavSection {...props} />
    case 'hero.split':
      return <HeroSection {...props} />
    case 'gallery.grid':
      return <GallerySection {...props} />
    case 'stats.row':
      return <StatsSection {...props} />
    case 'cta.banner':
      return <CtaSection {...props} />
    case 'footer.basic':
      return <FooterSection {...props} />
    default:
      return null
  }
}

export function PageRenderer({
  spec,
  editMode = false,
  selection = null,
  onSelect,
  onCommitText,
}: PageRendererProps) {
  const theme = spec.theme
  const style = {
    '--accent': theme.accent,
    '--title-scale': String(theme.titleScale / 100),
    '--body-scale': String(theme.bodyScale / 100),
    '--radius': `${theme.radius}px`,
    '--space': String(theme.spacing / 100),
  } as CSSProperties

  return (
    <div
      className={`page-root ${editMode ? 'page-edit-mode' : ''}`}
      data-mode={theme.mode}
      data-font={theme.font}
      style={style}
    >
      <div className="page">
        {spec.sections.map((section) => (
          <SectionRenderer
            key={section.id}
            section={section}
            editMode={editMode}
            selection={selection}
            onSelect={onSelect}
            onCommitText={onCommitText}
          />
        ))}
      </div>
    </div>
  )
}
