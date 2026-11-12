export type ThemeMode = 'ink' | 'paper' | 'forest' | 'ocean' | 'candy' | 'mono'
export type FontStyle = 'serif' | 'sans'

export interface ThemeSpec {
  mode: ThemeMode
  accent: string
  titleScale: number
  bodyScale: number
  radius: number
  font: FontStyle
  spacing: number
}

export interface ImageRef {
  src: string
  alt?: string
  fit?: 'cover' | 'contain'
  assetId?: string
  prompt?: string
}

export interface SectionSpec {
  id: string
  component:
    | 'nav.basic'
    | 'hero.split'
    | 'gallery.grid'
    | 'stats.row'
    | 'feature.cards'
    | 'menu.list'
    | 'quote.block'
    | 'cta.banner'
    | 'footer.basic'
  props: Record<string, any>
  layout?: Record<string, any>
}

export interface PageSpec {
  version: number
  meta: {
    title: string
    description?: string
  }
  theme: ThemeSpec
  sections: SectionSpec[]
  fieldMeta?: Record<string, { source?: 'ai' | 'user'; locked?: boolean }>
}

export interface User {
  id: string
  email: string
  displayName: string
  createdAt?: string | null
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  revisionNumber?: number | null
  createdAt: string
}

export interface ProjectSummary {
  id: string
  name: string
  version: number
  spec: PageSpec
  createdAt: string
  updatedAt: string
}

export interface ProjectDetail {
  id: string
  name: string
  version: number
  spec: PageSpec
  messages: ChatMessage[]
  latestRevision: number | null
  createdAt: string
  updatedAt: string
}

export interface SaveDraftResponse {
  version: number
  updatedAt: string
}

export interface SendMessageResponse {
  userMessage: ChatMessage
  assistantMessage: ChatMessage
  spec: PageSpec
  version: number
  revisionNumber: number | null
  changed: boolean
}

export interface AssetResponse {
  id: string
  url: string
  name: string
  mimeType: string
  size: number
  width: number | null
  height: number | null
}

export type AiProviderType = 'OPENAI_COMPATIBLE' | 'ANTHROPIC' | 'GEMINI'

export interface AiProviderPreset {
  type: AiProviderType
  label: string
  defaultBaseUrl: string
  defaultModel: string
  description: string
}

export interface AiConfig {
  id: string
  name: string
  providerType: AiProviderType
  baseUrl: string
  model: string
  keyHint: string | null
  hasApiKey: boolean
  defaultConfig: boolean
  createdAt: string
  updatedAt: string
}

export interface SaveAiConfigInput {
  name: string
  providerType: AiProviderType
  baseUrl: string
  model: string
  apiKey?: string
  defaultConfig: boolean
}

export interface AiConfigTestResult {
  ok: boolean
  message: string
  latencyMs: number
  model: string
}

export type ImageProviderType = 'OPENAI_IMAGES'

export interface ImageProviderPreset {
  type: ImageProviderType
  label: string
  defaultBaseUrl: string
  defaultModel: string
  description: string
}

export interface ImageAiConfig {
  id: string
  name: string
  providerType: ImageProviderType
  baseUrl: string
  model: string
  keyHint: string | null
  hasApiKey: boolean
  defaultSize: string
  defaultQuality: string | null
  defaultConfig: boolean
  createdAt: string
  updatedAt: string
}

export interface SaveImageAiConfigInput {
  name: string
  providerType: ImageProviderType
  baseUrl: string
  model: string
  apiKey?: string
  defaultSize: string
  defaultQuality?: string
  defaultConfig: boolean
}

export interface ImageAiConfigTestResult {
  ok: boolean
  message: string
  latencyMs: number
  model: string
  size: string
  previewUrl: string | null
  mimeType: string | null
  width: number | null
  height: number | null
}

export type ImageJobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SUPERSEDED'

export interface ImageJob {
  id: string
  sectionId: string
  path: string
  prompt: string
  size: string
  status: ImageJobStatus
  assetId: string | null
  assetUrl: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
  finishedAt: string | null
}

export interface ImageJobListResponse {
  jobs: ImageJob[]
  specVersion: number
}

export interface CreateImageJobInput {
  sectionId: string
  path: string
  prompt: string
}

export type SelectionKind = 'text' | 'image'

export interface Selection {
  sectionId: string
  path: string
  kind: SelectionKind
  label: string
}

export type InspectorTab = 'content' | 'style' | 'image' | 'layout'
export type DeviceMode = 'desktop' | 'tablet' | 'mobile'
export type SaveStatus = 'saved' | 'dirty' | 'saving' | 'error'
