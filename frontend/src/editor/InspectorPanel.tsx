import { useEffect, useMemo, useRef, useState } from 'react'
import {
  IconCheck,
  IconImage,
  IconLayout,
  IconPalette,
  IconType,
  IconUpload,
  IconX,
} from '../components/Icons'
import { api, ApiError } from '../lib/api'
import { PRESETS, collectImages, findSection, getAtPath, sectionLabel } from '../lib/utils'
import { useEditorStore } from '../store/editor'
import type { ImageRef, InspectorTab, Selection } from '../types'

const TABS: Array<{ id: InspectorTab; label: string; icon: React.ReactNode }> = [
  { id: 'content', label: '内容', icon: <IconType /> },
  { id: 'style', label: '样式', icon: <IconPalette /> },
  { id: 'image', label: '图片', icon: <IconImage /> },
  { id: 'layout', label: '布局', icon: <IconLayout /> },
]

const ACCENTS = [
  '#ff6b3d',
  '#c8f169',
  '#75d4e8',
  '#e9c46a',
  '#9d8cff',
  '#ff9db4',
  '#f2efe8',
  '#2d6a4f',
]

const THEME_MODES = [
  { id: 'ink', label: '午夜', colors: ['#0d0d0f', '#f2efe8'] },
  { id: 'paper', label: '纸张', colors: ['#f1ece2', '#1a1916'] },
  { id: 'forest', label: '森林', colors: ['#0f1f19', '#eef1e6'] },
  { id: 'ocean', label: '深海', colors: ['#061a24', '#eaf8fb'] },
  { id: 'candy', label: '糖果', colors: ['#fff1f4', '#29151b'] },
  { id: 'mono', label: '极简', colors: ['#f4f4f1', '#111111'] },
] as const

function FieldEditor({
  label,
  value,
  multiline,
  placeholder,
  hint,
  onCommit,
}: {
  label: string
  value: string
  multiline?: boolean
  placeholder?: string
  hint?: string
  onCommit: (value: string) => void
}) {
  const [draft, setDraft] = useState(value)

  useEffect(() => {
    setDraft(value)
  }, [value])

  const commit = () => {
    if (draft !== value) onCommit(draft)
  }

  return (
    <label className="inspector-field">
      <span className="inspector-label">{label}</span>
      {multiline ? (
        <textarea
          value={draft}
          rows={4}
          placeholder={placeholder}
          onChange={(event) => setDraft(event.target.value)}
          onBlur={commit}
        />
      ) : (
        <input
          value={draft}
          placeholder={placeholder}
          onChange={(event) => setDraft(event.target.value)}
          onBlur={commit}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault()
              commit()
              event.currentTarget.blur()
            }
            if (event.key === 'Escape') {
              setDraft(value)
              event.currentTarget.blur()
            }
          }}
        />
      )}
      <span className="inspector-hint">{hint || '失焦后自动保存'}</span>
    </label>
  )
}

function RangeControl({
  label,
  value,
  min,
  max,
  step = 1,
  suffix = '',
  onChange,
}: {
  label: string
  value: number
  min: number
  max: number
  step?: number
  suffix?: string
  onChange: (value: number) => void
}) {
  const begin = useEditorStore((state) => state.beginInteraction)
  const end = useEditorStore((state) => state.endInteraction)
  return (
    <div className="range-control">
      <div className="range-head">
        <span className="inspector-label">{label}</span>
        <span className="range-value">
          {value}
          {suffix}
        </span>
      </div>
      <div className="range-row">
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={value}
          onPointerDown={begin}
          onPointerUp={end}
          onPointerCancel={end}
          onBlur={end}
          onChange={(event) => onChange(Number(event.target.value))}
        />
        <input
          type="number"
          min={min}
          max={max}
          step={step}
          value={value}
          onFocus={begin}
          onBlur={end}
          onChange={(event) => {
            const next = Number(event.target.value)
            if (Number.isFinite(next)) onChange(next)
          }}
        />
      </div>
    </div>
  )
}

function Segmented<T extends string>({
  value,
  options,
  onChange,
}: {
  value: T
  options: Array<{ value: T; label: string }>
  onChange: (value: T) => void
}) {
  return (
    <div className="segmented">
      {options.map((option) => (
        <button
          type="button"
          key={option.value}
          className={value === option.value ? 'is-active' : ''}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}

function useActiveImage(selection: Selection | null) {
  const spec = useEditorStore((state) => state.spec)
  const images = useMemo(() => (spec ? collectImages(spec) : []), [spec])
  if (selection?.kind === 'image') {
    const exact = images.find(
      (item) => item.sectionId === selection.sectionId && item.path === selection.path,
    )
    if (exact) return exact
  }
  return images[0]
}

export function InspectorPanel({ onClose }: { onClose?: () => void }) {
  const spec = useEditorStore((state) => state.spec)
  const selection = useEditorStore((state) => state.selection)
  const tab = useEditorStore((state) => state.tab)
  const projectId = useEditorStore((state) => state.projectId)
  const setTab = useEditorStore((state) => state.setTab)
  const select = useEditorStore((state) => state.select)
  const updateText = useEditorStore((state) => state.updateText)
  const updateImage = useEditorStore((state) => state.updateImage)
  const updateTheme = useEditorStore((state) => state.updateTheme)
  const updateMeta = useEditorStore((state) => state.updateMeta)
  const updateSectionLayout = useEditorStore((state) => state.updateSectionLayout)
  const begin = useEditorStore((state) => state.beginInteraction)
  const end = useEditorStore((state) => state.endInteraction)
  const activeImage = useActiveImage(selection)
  const imageList = useMemo(() => (spec ? collectImages(spec) : []), [spec])
  const selectedSection = spec && selection ? findSection(spec, selection.sectionId) : undefined
  const selectedText =
    selection?.kind === 'text' && selectedSection
      ? String(getAtPath(selectedSection, selection.path) ?? '')
      : ''
  const [uploading, setUploading] = useState(false)
  const fileRef = useRef<HTMLInputElement | null>(null)

  if (!spec) return <aside className="inspector-panel" />

  const theme = spec.theme
  const multilineField =
    selection?.kind === 'text' &&
    (selectedText.includes('\n') ||
      selectedText.length > 56 ||
      /subtitle|caption|description|note/i.test(selection.path))

  const uploadImage = async (file: File) => {
    if (!activeImage) return
    if (file.size > 8 * 1024 * 1024) {
      window.dispatchEvent(
        new CustomEvent('aster:toast', {
          detail: { type: 'error', message: '图片不能超过 8 MB' },
        }),
      )
      return
    }
    setUploading(true)
    try {
      const asset = await api.uploadAsset(file, projectId || undefined)
      updateImage(activeImage.sectionId, activeImage.path, {
        ...activeImage.image,
        src: asset.url,
        assetId: asset.id,
        alt: activeImage.image.alt || file.name.replace(/\.[^.]+$/, ''),
      })
      window.dispatchEvent(
        new CustomEvent('aster:toast', {
          detail: { type: 'success', message: '图片已替换' },
        }),
      )
    } catch (caught) {
      window.dispatchEvent(
        new CustomEvent('aster:toast', {
          detail: {
            type: 'error',
            message: caught instanceof ApiError ? caught.message : '图片上传失败',
          },
        }),
      )
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  const selectImage = (entry: (typeof imageList)[number]) => {
    select({
      sectionId: entry.sectionId,
      path: entry.path,
      kind: 'image',
      label: entry.label,
    })
    setTab('image')
  }

  return (
    <aside className="inspector-panel">
      <header className="inspector-head">
        <div>
          <span className="inspector-kicker">INSPECTOR</span>
          <strong>属性面板</strong>
        </div>
        {onClose && (
          <button type="button" className="icon-btn inspector-close" onClick={onClose} aria-label="关闭属性面板">
            <IconX />
          </button>
        )}
      </header>

      <div className="inspector-tabs" role="tablist">
        {TABS.map((item) => (
          <button
            type="button"
            key={item.id}
            className={tab === item.id ? 'is-active' : ''}
            onClick={() => setTab(item.id)}
            role="tab"
            aria-selected={tab === item.id}
          >
            {item.icon}
            <span>{item.label}</span>
          </button>
        ))}
      </div>

      <div className="inspector-scroll">
        {tab === 'content' && (
          <div className="inspector-section">
            {selection?.kind === 'text' && selectedSection ? (
              <>
                <div className="selection-chip">
                  <span className="selection-chip-dot" />
                  <div>
                    <strong>{selection.label}</strong>
                    <span>{sectionLabel(selectedSection.component)} · 文字</span>
                  </div>
                </div>
                <FieldEditor
                  label="文字内容"
                  value={selectedText}
                  multiline={multilineField}
                  onCommit={(value) => updateText(selection.sectionId, selection.path, value)}
                />
                <div className="inspector-tip">
                  <IconType />
                  <p>也可以直接在画布中点击文字修改。支持换行，按 Esc 取消本次修改。</p>
                </div>
              </>
            ) : selection?.kind === 'image' ? (
              <>
                <div className="selection-chip">
                  <span className="selection-chip-dot" />
                  <div>
                    <strong>{selection.label}</strong>
                    <span>{sectionLabel(selectedSection?.component ?? 'hero.split')} · 图片</span>
                  </div>
                </div>
                <div className="inspector-image-preview">
                  {activeImage ? <img src={activeImage.image.src} alt={activeImage.image.alt || ''} /> : null}
                </div>
                <button type="button" className="btn btn-dark full" onClick={() => setTab('image')}>
                  <IconImage />
                  打开图片面板
                </button>
                <FieldEditor
                  label="图片说明"
                  value={activeImage?.image.alt || ''}
                  onCommit={(value) => {
                    if (!activeImage) return
                    updateImage(activeImage.sectionId, activeImage.path, {
                      ...activeImage.image,
                      alt: value,
                    })
                  }}
                />
              </>
            ) : (
              <>
                <div className="selection-chip is-empty">
                  <span className="selection-chip-dot" />
                  <div>
                    <strong>未选中元素</strong>
                    <span>点击画布里的文字或图片</span>
                  </div>
                </div>
                <div className="inspector-block">
                  <div className="inspector-block-head">
                    <strong>页面信息</strong>
                    <span>PAGE</span>
                  </div>
                  <FieldEditor
                    label="页面标题"
                    value={spec.meta.title}
                    onCommit={(value) => updateMeta({ title: value })}
                  />
                  <FieldEditor
                    label="页面描述"
                    value={spec.meta.description || ''}
                    multiline
                    placeholder="一句话说明这个页面"
                    onCommit={(value) => updateMeta({ description: value })}
                  />
                </div>
                <div className="inspector-tip">
                  <IconType />
                  <p>没有选中元素时，这里可以修改页面的标题和描述。点击画布中的任意文字，会切换到对应字段。</p>
                </div>
              </>
            )}
          </div>
        )}

        {tab === 'style' && (
          <div className="inspector-section">
            <div className="inspector-block">
              <div className="inspector-block-head">
                <strong>主题氛围</strong>
                <span>THEME</span>
              </div>
              <div className="theme-grid">
                {THEME_MODES.map((mode) => (
                  <button
                    type="button"
                    key={mode.id}
                    className={`theme-tile ${theme.mode === mode.id ? 'is-active' : ''}`}
                    onClick={() => updateTheme({ mode: mode.id })}
                  >
                    <span className="theme-swatch">
                      <i style={{ background: mode.colors[0] }} />
                      <i style={{ background: mode.colors[1] }} />
                    </span>
                    <span>{mode.label}</span>
                    {theme.mode === mode.id && <IconCheck />}
                  </button>
                ))}
              </div>
            </div>

            <div className="inspector-block">
              <div className="inspector-block-head">
                <strong>强调色</strong>
                <span>ACCENT</span>
              </div>
              <div className="accent-grid">
                {ACCENTS.map((accent) => (
                  <button
                    type="button"
                    key={accent}
                    className={`accent-swatch ${theme.accent.toLowerCase() === accent.toLowerCase() ? 'is-active' : ''}`}
                    style={{ background: accent }}
                    onClick={() => updateTheme({ accent })}
                    aria-label={`选择颜色 ${accent}`}
                  >
                    {theme.accent.toLowerCase() === accent.toLowerCase() && <IconCheck />}
                  </button>
                ))}
                <label className="accent-custom" title="自定义颜色">
                  <input
                    type="color"
                    value={theme.accent}
                    onFocus={begin}
                    onBlur={end}
                    onChange={(event) => updateTheme({ accent: event.target.value })}
                  />
                  <span>+</span>
                </label>
              </div>
            </div>

            <div className="inspector-block">
              <div className="inspector-block-head">
                <strong>字体气质</strong>
                <span>TYPE</span>
              </div>
              <Segmented
                value={theme.font}
                options={[
                  { value: 'serif', label: '编辑感衬线' },
                  { value: 'sans', label: '现代无衬线' },
                ]}
                onChange={(font) => updateTheme({ font })}
              />
            </div>

            <div className="inspector-block">
              <div className="inspector-block-head">
                <strong>整体节奏</strong>
                <span>SCALE</span>
              </div>
              <RangeControl
                label="标题字号"
                value={theme.titleScale}
                min={60}
                max={160}
                suffix="%"
                onChange={(titleScale) => updateTheme({ titleScale })}
              />
              <RangeControl
                label="正文字号"
                value={theme.bodyScale}
                min={60}
                max={160}
                suffix="%"
                onChange={(bodyScale) => updateTheme({ bodyScale })}
              />
              <RangeControl
                label="圆角"
                value={theme.radius}
                min={0}
                max={40}
                suffix="px"
                onChange={(radius) => updateTheme({ radius })}
              />
              <RangeControl
                label="区块间距"
                value={theme.spacing}
                min={60}
                max={160}
                suffix="%"
                onChange={(spacing) => updateTheme({ spacing })}
              />
            </div>
          </div>
        )}

        {tab === 'image' && (
          <div className="inspector-section">
            {!activeImage ? (
              <div className="inspector-empty">
                <span className="inspector-empty-icon">
                  <IconImage />
                </span>
                <strong>这个页面还没有图片</strong>
                <p>回到对话面板，让 Aster 增加一个带图片的区块。</p>
              </div>
            ) : (
              <>
                <div className="selection-chip">
                  <span className="selection-chip-dot" />
                  <div>
                    <strong>{activeImage.label}</strong>
                    <span>{activeImage.image.assetId ? '已上传图片' : '内置视觉资产'}</span>
                  </div>
                </div>

                <div className="inspector-image-preview large">
                  <img src={activeImage.image.src} alt={activeImage.image.alt || ''} />
                </div>

                <input
                  ref={fileRef}
                  type="file"
                  accept="image/png,image/jpeg,image/webp,image/gif"
                  className="hidden-input"
                  onChange={(event) => {
                    const file = event.target.files?.[0]
                    if (file) void uploadImage(file)
                  }}
                />
                <button
                  type="button"
                  className="btn btn-dark full"
                  onClick={() => fileRef.current?.click()}
                  disabled={uploading}
                >
                  {uploading ? <span className="spinner light" /> : <IconUpload />}
                  {uploading ? '上传中…' : '上传并替换图片'}
                </button>
                <p className="inspector-hint center">PNG / JPG / WebP / GIF，最大 8 MB</p>

                <div className="inspector-block">
                  <div className="inspector-block-head">
                    <strong>内置图片</strong>
                    <span>PRESETS</span>
                  </div>
                  <div className="preset-grid">
                    {PRESETS.map((preset) => (
                      <button
                        type="button"
                        key={preset.src}
                        className={`preset-tile ${activeImage.image.src === preset.src ? 'is-active' : ''}`}
                        onClick={() =>
                          updateImage(activeImage.sectionId, activeImage.path, {
                            ...activeImage.image,
                            src: preset.src,
                            assetId: undefined,
                          })
                        }
                        title={preset.label}
                      >
                        <img src={preset.src} alt={preset.label} />
                        {activeImage.image.src === preset.src && <IconCheck />}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="inspector-block">
                  <div className="inspector-block-head">
                    <strong>展示方式</strong>
                    <span>FIT</span>
                  </div>
                  <Segmented
                    value={activeImage.image.fit || 'cover'}
                    options={[
                      { value: 'cover', label: '填满裁剪' },
                      { value: 'contain', label: '完整显示' },
                    ]}
                    onChange={(fit) =>
                      updateImage(activeImage.sectionId, activeImage.path, {
                        ...activeImage.image,
                        fit,
                      })
                    }
                  />
                  <FieldEditor
                    label="图片说明 (alt)"
                    value={activeImage.image.alt || ''}
                    onCommit={(alt) =>
                      updateImage(activeImage.sectionId, activeImage.path, {
                        ...activeImage.image,
                        alt,
                      })
                    }
                  />
                </div>

                <div className="inspector-block">
                  <div className="inspector-block-head">
                    <strong>页面图片</strong>
                    <span>{imageList.length}</span>
                  </div>
                  <div className="image-list">
                    {imageList.map((entry) => (
                      <button
                        type="button"
                        key={`${entry.sectionId}-${entry.path}`}
                        className={`image-list-item ${
                          activeImage.sectionId === entry.sectionId && activeImage.path === entry.path
                            ? 'is-active'
                            : ''
                        }`}
                        onClick={() => selectImage(entry)}
                      >
                        <img src={entry.image.src} alt="" />
                        <span>{entry.label}</span>
                      </button>
                    ))}
                  </div>
                </div>
              </>
            )}
          </div>
        )}

        {tab === 'layout' && (
          <div className="inspector-section">
            {selectedSection ? (
              <>
                <div className="selection-chip">
                  <span className="selection-chip-dot" />
                  <div>
                    <strong>{sectionLabel(selectedSection.component)}</strong>
                    <span>区块布局</span>
                  </div>
                </div>

                {selectedSection.component === 'hero.split' && (
                  <>
                    <div className="inspector-block">
                      <div className="inspector-block-head">
                        <strong>首屏版式</strong>
                        <span>VARIANT</span>
                      </div>
                      <Segmented
                        value={
                          ['center', 'full', 'stack'].includes(String(selectedSection.layout?.variant))
                            ? String(selectedSection.layout?.variant)
                            : 'split'
                        }
                        options={[
                          { value: 'split', label: '左右分栏' },
                          { value: 'center', label: '居中' },
                          { value: 'full', label: '全屏大图' },
                          { value: 'stack', label: '上下堆叠' },
                        ]}
                        onChange={(variant) =>
                          updateSectionLayout(selectedSection.id, { variant })
                        }
                      />
                    </div>
                    <div className="inspector-block">
                      <div className="inspector-block-head">
                        <strong>文字对齐</strong>
                        <span>ALIGN</span>
                      </div>
                      <Segmented
                        value={selectedSection.layout?.align === 'center' ? 'center' : 'left'}
                        options={[
                          { value: 'left', label: '左对齐' },
                          { value: 'center', label: '居中' },
                        ]}
                        onChange={(align) =>
                          updateSectionLayout(selectedSection.id, { align })
                        }
                      />
                    </div>
                    <div className="inspector-block">
                      <div className="inspector-block-head">
                        <strong>图片位置</strong>
                        <span>MEDIA</span>
                      </div>
                      <Segmented
                        value={selectedSection.layout?.mediaSide === 'left' ? 'left' : 'right'}
                        options={[
                          { value: 'right', label: '图片在右' },
                          { value: 'left', label: '图片在左' },
                        ]}
                        onChange={(mediaSide) =>
                          updateSectionLayout(selectedSection.id, { mediaSide })
                        }
                      />
                    </div>
                  </>
                )}

                {selectedSection.component === 'gallery.grid' && (
                  <>
                    <div className="inspector-block">
                      <div className="inspector-block-head">
                        <strong>画廊版式</strong>
                        <span>VARIANT</span>
                      </div>
                      <Segmented
                        value={
                          ['masonry', 'list'].includes(String(selectedSection.layout?.variant))
                            ? String(selectedSection.layout?.variant)
                            : 'grid'
                        }
                        options={[
                          { value: 'grid', label: '网格' },
                          { value: 'masonry', label: '错落' },
                          { value: 'list', label: '列表' },
                        ]}
                        onChange={(variant) =>
                          updateSectionLayout(selectedSection.id, { variant })
                        }
                      />
                    </div>
                    {selectedSection.layout?.variant !== 'list' && (
                      <div className="inspector-block">
                        <div className="inspector-block-head">
                          <strong>作品列数</strong>
                          <span>COLUMNS</span>
                        </div>
                        <Segmented
                          value={String(selectedSection.layout?.columns || 3)}
                          options={[
                            { value: '2', label: '两列' },
                            { value: '3', label: '三列' },
                          ]}
                          onChange={(columns) =>
                            updateSectionLayout(selectedSection.id, { columns: Number(columns) })
                          }
                        />
                      </div>
                    )}
                  </>
                )}

                {selectedSection.component === 'feature.cards' && (
                  <div className="inspector-block">
                    <div className="inspector-block-head">
                      <strong>卡片列数</strong>
                      <span>COLUMNS</span>
                    </div>
                    <Segmented
                      value={String(selectedSection.layout?.columns || 3)}
                      options={[
                        { value: '2', label: '两列' },
                        { value: '3', label: '三列' },
                        { value: '4', label: '四列' },
                      ]}
                      onChange={(columns) =>
                        updateSectionLayout(selectedSection.id, { columns: Number(columns) })
                      }
                    />
                  </div>
                )}

                {selectedSection.component === 'menu.list' && (
                  <div className="inspector-block">
                    <div className="inspector-block-head">
                      <strong>菜单列数</strong>
                      <span>COLUMNS</span>
                    </div>
                    <Segmented
                      value={String(selectedSection.layout?.columns || 2)}
                      options={[
                        { value: '1', label: '单列' },
                        { value: '2', label: '双列' },
                      ]}
                      onChange={(columns) =>
                        updateSectionLayout(selectedSection.id, { columns: Number(columns) })
                      }
                    />
                  </div>
                )}

                {!['hero.split', 'gallery.grid', 'feature.cards', 'menu.list'].includes(
                  selectedSection.component,
                ) && (
                  <div className="inspector-tip">
                    <IconLayout />
                    <p>这个区块暂时没有单独的布局选项。可以回到对话面板，让 Aster 调整整体间距或隐藏区块。</p>
                  </div>
                )}
              </>
            ) : (
              <div className="inspector-tip">
                <IconLayout />
                <p>点击画布中的区块内容，可以调整首屏对齐、图片位置或作品列数。</p>
              </div>
            )}

            <div className="inspector-block">
              <div className="inspector-block-head">
                <strong>区块间距</strong>
                <span>SPACE</span>
              </div>
              <RangeControl
                label="页面留白"
                value={theme.spacing}
                min={60}
                max={160}
                suffix="%"
                onChange={(spacing) => updateTheme({ spacing })}
              />
            </div>

            <div className="inspector-block">
              <div className="inspector-block-head">
                <strong>页面结构</strong>
                <span>{spec.sections.length}</span>
              </div>
              <div className="section-list">
                {spec.sections.map((section, index) => (
                  <div
                    key={section.id}
                    className={`section-list-item ${
                      selection?.sectionId === section.id ? 'is-active' : ''
                    }`}
                  >
                    <span>{String(index + 1).padStart(2, '0')}</span>
                    <strong>{sectionLabel(section.component)}</strong>
                    <em>{section.component}</em>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </aside>
  )
}
