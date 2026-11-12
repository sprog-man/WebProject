import type {
  AiConfig,
  AiConfigTestResult,
  AiProviderPreset,
  AssetResponse,
  ChatMessage,
  CreateImageJobInput,
  ImageAiConfig,
  ImageAiConfigTestResult,
  ImageJobListResponse,
  ImageProviderPreset,
  PageSpec,
  ProjectDetail,
  ProjectSummary,
  SaveAiConfigInput,
  SaveDraftResponse,
  SaveImageAiConfigInput,
  SendMessageResponse,
  User,
} from '../types'

export class ApiError extends Error {
  status: number
  code: string

  constructor(message: string, status = 0, code = 'NETWORK_ERROR') {
    super(message)
    this.status = status
    this.code = code
  }
}

let csrfToken: string | null = null
let csrfHeader = 'X-XSRF-TOKEN'
let csrfPromise: Promise<string> | null = null

function readCookie(name: string): string | null {
  const target = `${name}=`
  for (const part of document.cookie.split(';')) {
    const item = part.trim()
    if (item.startsWith(target)) {
      return decodeURIComponent(item.slice(target.length))
    }
  }
  return null
}

async function fetchCsrf(): Promise<string> {
  const response = await fetch('/api/auth/csrf', {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) {
    throw new ApiError('无法初始化安全会话', response.status, 'CSRF_ERROR')
  }
  const data = (await response.json()) as {
    token: string
    headerName: string
  }
  csrfToken = data.token
  csrfHeader = data.headerName || 'X-XSRF-TOKEN'
  return data.token
}

async function ensureCsrf(): Promise<string> {
  if (csrfToken) return csrfToken
  const cookie = readCookie('XSRF-TOKEN')
  if (cookie) {
    csrfToken = cookie
    return cookie
  }
  if (!csrfPromise) {
    csrfPromise = fetchCsrf().finally(() => {
      csrfPromise = null
    })
  }
  return csrfPromise
}

async function parseResponse<T>(response: Response): Promise<T> {
  const contentType = response.headers.get('content-type') || ''
  if (!response.ok) {
    let message = `请求失败 (${response.status})`
    let code = 'REQUEST_FAILED'
    if (contentType.includes('application/json')) {
      try {
        const body = await response.json()
        message = body.message || message
        code = body.code || code
      } catch {
        // ignore
      }
    } else {
      try {
        const text = await response.text()
        if (text && text.length < 200) message = text
      } catch {
        // ignore
      }
    }
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent('aster:unauthorized'))
    }
    throw new ApiError(message, response.status, code)
  }
  if (response.status === 204) return undefined as T
  if (contentType.includes('application/json')) {
    return (await response.json()) as T
  }
  return (await response.text()) as T
}

interface RequestOptions {
  method?: string
  body?: unknown
  retryOnCsrf?: boolean
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method || 'GET'
  const headers: Record<string, string> = {
    Accept: 'application/json',
  }
  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }
  if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    headers[csrfHeader] = await ensureCsrf()
  }

  let response: Response
  try {
    response = await fetch(path, {
      method,
      credentials: 'include',
      headers,
      body:
        options.body instanceof FormData
          ? options.body
          : options.body !== undefined
            ? JSON.stringify(options.body)
            : undefined,
    })
  } catch {
    throw new ApiError('网络连接失败，请确认服务已启动', 0, 'NETWORK_ERROR')
  }

  if (response.status === 403 && options.retryOnCsrf !== false) {
    csrfToken = null
    await ensureCsrf()
    return request<T>(path, { ...options, retryOnCsrf: false })
  }
  return parseResponse<T>(response)
}

export const api = {
  init() {
    return ensureCsrf()
  },

  async login(email: string, password: string): Promise<User> {
    return request<User>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    })
  },

  async register(email: string, password: string, displayName: string): Promise<User> {
    return request<User>('/api/auth/register', {
      method: 'POST',
      body: { email, password, displayName },
    })
  },

  async logout(): Promise<void> {
    return request<void>('/api/auth/logout', { method: 'POST' })
  },

  async me(): Promise<User> {
    return request<User>('/api/auth/me')
  },

  async listProjects(): Promise<ProjectSummary[]> {
    return request<ProjectSummary[]>('/api/projects')
  },

  async getProject(projectId: string): Promise<ProjectDetail> {
    return request<ProjectDetail>(`/api/projects/${projectId}`)
  },

  async createProject(name: string): Promise<ProjectDetail> {
    return request<ProjectDetail>('/api/projects', {
      method: 'POST',
      body: { name },
    })
  },

  async renameProject(projectId: string, name: string): Promise<ProjectSummary> {
    return request<ProjectSummary>(`/api/projects/${projectId}`, {
      method: 'PATCH',
      body: { name },
    })
  },

  async deleteProject(projectId: string): Promise<void> {
    return request<void>(`/api/projects/${projectId}`, { method: 'DELETE' })
  },

  async saveDraft(projectId: string, spec: PageSpec, version: number): Promise<SaveDraftResponse> {
    return request<SaveDraftResponse>(`/api/projects/${projectId}/draft`, {
      method: 'PUT',
      body: { spec, version },
    })
  },

  async sendMessage(projectId: string, content: string): Promise<SendMessageResponse> {
    return request<SendMessageResponse>(`/api/projects/${projectId}/messages`, {
      method: 'POST',
      body: { content },
    })
  },

  async uploadAsset(file: File, projectId?: string): Promise<AssetResponse> {
    const formData = new FormData()
    formData.append('file', file)
    if (projectId) formData.append('projectId', projectId)
    return request<AssetResponse>('/api/assets', {
      method: 'POST',
      body: formData,
    })
  },

  async aiProviders(): Promise<AiProviderPreset[]> {
    return request<AiProviderPreset[]>('/api/ai/providers')
  },

  async aiConfigs(): Promise<AiConfig[]> {
    return request<AiConfig[]>('/api/ai/configs')
  },

  async createAiConfig(input: SaveAiConfigInput): Promise<AiConfig> {
    return request<AiConfig>('/api/ai/configs', {
      method: 'POST',
      body: input,
    })
  },

  async updateAiConfig(configId: string, input: SaveAiConfigInput): Promise<AiConfig> {
    return request<AiConfig>(`/api/ai/configs/${configId}`, {
      method: 'PUT',
      body: input,
    })
  },

  async deleteAiConfig(configId: string): Promise<void> {
    return request<void>(`/api/ai/configs/${configId}`, { method: 'DELETE' })
  },

  async setDefaultAiConfig(configId: string): Promise<AiConfig> {
    return request<AiConfig>(`/api/ai/configs/${configId}/default`, { method: 'POST' })
  },

  async testAiConfig(configId: string): Promise<AiConfigTestResult> {
    return request<AiConfigTestResult>(`/api/ai/configs/${configId}/test`, { method: 'POST' })
  },

  async imageAiProviders(): Promise<ImageProviderPreset[]> {
    return request<ImageProviderPreset[]>('/api/image-ai/providers')
  },

  async imageAiConfigs(): Promise<ImageAiConfig[]> {
    return request<ImageAiConfig[]>('/api/image-ai/configs')
  },

  async createImageAiConfig(input: SaveImageAiConfigInput): Promise<ImageAiConfig> {
    return request<ImageAiConfig>('/api/image-ai/configs', {
      method: 'POST',
      body: input,
    })
  },

  async updateImageAiConfig(configId: string, input: SaveImageAiConfigInput): Promise<ImageAiConfig> {
    return request<ImageAiConfig>(`/api/image-ai/configs/${configId}`, {
      method: 'PUT',
      body: input,
    })
  },

  async deleteImageAiConfig(configId: string): Promise<void> {
    return request<void>(`/api/image-ai/configs/${configId}`, { method: 'DELETE' })
  },

  async setDefaultImageAiConfig(configId: string): Promise<ImageAiConfig> {
    return request<ImageAiConfig>(`/api/image-ai/configs/${configId}/default`, { method: 'POST' })
  },

  async testImageAiConfig(configId: string): Promise<ImageAiConfigTestResult> {
    return request<ImageAiConfigTestResult>(`/api/image-ai/configs/${configId}/test`, {
      method: 'POST',
    })
  },

  async listImageJobs(projectId: string): Promise<ImageJobListResponse> {
    return request<ImageJobListResponse>(`/api/projects/${projectId}/image-jobs`)
  },

  async createImageJobs(
    projectId: string,
    items: CreateImageJobInput[],
  ): Promise<ImageJobListResponse> {
    return request<ImageJobListResponse>(`/api/projects/${projectId}/image-jobs`, {
      method: 'POST',
      body: { items },
    })
  },
}

export function messageTime(message: ChatMessage): string {
  try {
    return new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(message.createdAt))
  } catch {
    return ''
  }
}
