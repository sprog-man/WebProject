import { create } from 'zustand'
import { immer } from 'zustand/middleware/immer'
import { api, ApiError } from '../lib/api'
import { cloneSpec, collectImages, findSection, getAtPath, setAtPath } from '../lib/utils'
import type {
  ChatMessage,
  CreateImageJobInput,
  DeviceMode,
  ImageJob,
  ImageRef,
  InspectorTab,
  PageSpec,
  ProjectDetail,
  SaveStatus,
  Selection,
} from '../types'

interface EditorState {
  projectId: string | null
  projectName: string
  spec: PageSpec | null
  version: number
  messages: ChatMessage[]
  selection: Selection | null
  tab: InspectorTab
  editMode: boolean
  device: DeviceMode
  saveStatus: SaveStatus
  sending: boolean
  loading: boolean
  history: PageSpec[]
  future: PageSpec[]
  changeCounter: number
  savedCounter: number
  interactionSnapshot: PageSpec | null
  lastSavedAt: string | null
  imageJobs: ImageJob[]
  imageJobsLoading: boolean
  imagePanelOpen: boolean

  loadProject: (project: ProjectDetail) => void
  reset: () => void
  select: (selection: Selection | null) => void
  setTab: (tab: InspectorTab) => void
  toggleEditMode: () => void
  setDevice: (device: DeviceMode) => void
  setProjectName: (name: string) => void
  updateText: (sectionId: string, path: string, value: string) => void
  updateImage: (sectionId: string, path: string, image: ImageRef) => void
  updateSectionLayout: (sectionId: string, patch: Record<string, unknown>) => void
  updateTheme: (patch: Partial<PageSpec['theme']>) => void
  updateMeta: (patch: Partial<PageSpec['meta']>) => void
  beginInteraction: () => void
  endInteraction: () => void
  undo: () => void
  redo: () => void
  save: () => Promise<boolean>
  sendMessage: (content: string) => Promise<void>
  setImagePanelOpen: (open: boolean) => void
  loadImageJobs: () => Promise<void>
  refreshImageJobs: () => Promise<void>
  startImageGeneration: (items: CreateImageJobInput[]) => Promise<boolean>
}

let autosaveTimer: number | undefined
let inFlightSave: Promise<boolean> | null = null

function toast(type: 'success' | 'error' | 'info', message: string) {
  window.dispatchEvent(new CustomEvent('aster:toast', { detail: { type, message } }))
}

function recordHistory(state: EditorState, force = false) {
  if (!state.spec) return
  if (state.interactionSnapshot && !force) return
  state.history.push(cloneSpec(state.spec))
  if (state.history.length > 60) state.history.shift()
  state.future = []
}

function markUser(state: EditorState, sectionId: string, path: string) {
  if (!state.spec) return
  if (!state.spec.fieldMeta) state.spec.fieldMeta = {}
  state.spec.fieldMeta[`${sectionId}.${path}`] = { source: 'user' }
}

function touch(state: EditorState) {
  state.changeCounter += 1
  state.saveStatus = 'dirty'
}

function hasPendingImagePrompt(spec: PageSpec): boolean {
  return collectImages(spec).some(
    (entry) =>
      Boolean(entry.image.prompt?.trim()) &&
      !entry.image.assetId &&
      entry.image.src.startsWith('/presets/'),
  )
}

function applyCompletedJobs(spec: PageSpec, jobs: ImageJob[]): boolean {
  let changed = false
  for (const job of jobs) {
    if (job.status !== 'SUCCEEDED' || !job.assetUrl || !job.assetId) continue
    const section = findSection(spec, job.sectionId)
    if (!section) continue
    const image = getAtPath(section, job.path) as ImageRef | undefined
    if (!image || typeof image !== 'object') continue
    if ((image.prompt || '').trim() !== job.prompt.trim()) continue
    if (job.baseSrc && image.src !== job.baseSrc) continue
    if (image.src === job.assetUrl && image.assetId === job.assetId) continue
    image.src = job.assetUrl
    image.assetId = job.assetId
    changed = true
  }
  return changed
}

function queueAutosave() {
  if (autosaveTimer) window.clearTimeout(autosaveTimer)
  autosaveTimer = window.setTimeout(() => {
    const state = useEditorStore.getState()
    if (state.projectId && state.spec) {
      void state.save()
    }
  }, 900)
}

export const useEditorStore = create<EditorState>()(
  immer((set, get) => ({
    projectId: null,
    projectName: '',
    spec: null,
    version: 0,
    messages: [],
    selection: null,
    tab: 'content',
    editMode: true,
    device: 'desktop',
    saveStatus: 'saved',
    sending: false,
    loading: true,
    history: [],
    future: [],
    changeCounter: 0,
    savedCounter: 0,
    interactionSnapshot: null,
    lastSavedAt: null,
    imageJobs: [],
    imageJobsLoading: false,
    imagePanelOpen: false,

    loadProject: (project) => {
      if (autosaveTimer) window.clearTimeout(autosaveTimer)
      set((state) => {
        state.projectId = project.id
        state.projectName = project.name
        state.spec = project.spec
        state.version = project.version
        state.messages = project.messages
        state.selection = null
        state.tab = 'content'
        state.editMode = true
        state.saveStatus = 'saved'
        state.sending = false
        state.loading = false
        state.history = []
        state.future = []
        state.changeCounter = 0
        state.savedCounter = 0
        state.interactionSnapshot = null
        state.lastSavedAt = project.updatedAt
        state.imageJobs = []
        state.imageJobsLoading = false
        state.imagePanelOpen = false
      })
    },

    reset: () => {
      if (autosaveTimer) window.clearTimeout(autosaveTimer)
      inFlightSave = null
      set((state) => {
        state.projectId = null
        state.projectName = ''
        state.spec = null
        state.version = 0
        state.messages = []
        state.selection = null
        state.tab = 'content'
        state.editMode = true
        state.device = 'desktop'
        state.saveStatus = 'saved'
        state.sending = false
        state.loading = true
        state.history = []
        state.future = []
        state.changeCounter = 0
        state.savedCounter = 0
        state.interactionSnapshot = null
        state.lastSavedAt = null
        state.imageJobs = []
        state.imageJobsLoading = false
        state.imagePanelOpen = false
      })
    },

    select: (selection) => {
      set((state) => {
        state.selection = selection
        if (selection) {
          state.tab = selection.kind === 'image' ? 'image' : 'content'
        }
      })
    },

    setTab: (tab) => {
      set((state) => {
        state.tab = tab
      })
    },

    toggleEditMode: () => {
      set((state) => {
        state.editMode = !state.editMode
        if (!state.editMode) state.selection = null
      })
    },

    setDevice: (device) => {
      set((state) => {
        state.device = device
      })
    },

    setProjectName: (name) => {
      set((state) => {
        state.projectName = name
      })
    },

    updateText: (sectionId, path, value) => {
      set((state) => {
        if (!state.spec) return
        const section = findSection(state.spec, sectionId)
        if (!section) return
        if (getAtPath(section, path) === value) return
        recordHistory(state)
        setAtPath(section, path, value)
        markUser(state, sectionId, path)
        touch(state)
      })
      queueAutosave()
    },

    updateImage: (sectionId, path, image) => {
      set((state) => {
        if (!state.spec) return
        const section = findSection(state.spec, sectionId)
        if (!section) return
        recordHistory(state)
        const current = getAtPath(section, path) as ImageRef | undefined
        if (current && typeof current === 'object') {
          Object.assign(current, image)
        } else {
          setAtPath(section, path, image)
        }
        markUser(state, sectionId, path)
        touch(state)
      })
      queueAutosave()
    },

    updateSectionLayout: (sectionId, patch) => {
      set((state) => {
        if (!state.spec) return
        const section = findSection(state.spec, sectionId)
        if (!section) return
        recordHistory(state)
        section.layout = { ...(section.layout || {}), ...patch }
        touch(state)
      })
      queueAutosave()
    },

    updateTheme: (patch) => {
      set((state) => {
        if (!state.spec) return
        recordHistory(state)
        Object.assign(state.spec.theme, patch)
        touch(state)
      })
      queueAutosave()
    },

    updateMeta: (patch) => {
      set((state) => {
        if (!state.spec) return
        recordHistory(state)
        Object.assign(state.spec.meta, patch)
        touch(state)
      })
      queueAutosave()
    },

    beginInteraction: () => {
      set((state) => {
        if (!state.spec || state.interactionSnapshot) return
        state.interactionSnapshot = cloneSpec(state.spec)
      })
    },

    endInteraction: () => {
      set((state) => {
        if (!state.spec || !state.interactionSnapshot) return
        if (JSON.stringify(state.interactionSnapshot) !== JSON.stringify(state.spec)) {
          state.history.push(state.interactionSnapshot)
          if (state.history.length > 60) state.history.shift()
          state.future = []
        }
        state.interactionSnapshot = null
      })
    },

    undo: () => {
      set((state) => {
        if (!state.spec || state.history.length === 0) return
        const previous = state.history.pop()!
        state.future.push(cloneSpec(state.spec))
        state.spec = previous
        state.interactionSnapshot = null
        state.changeCounter += 1
        state.saveStatus = 'dirty'
      })
      queueAutosave()
    },

    redo: () => {
      set((state) => {
        if (!state.spec || state.future.length === 0) return
        const next = state.future.pop()!
        state.history.push(cloneSpec(state.spec))
        state.spec = next
        state.interactionSnapshot = null
        state.changeCounter += 1
        state.saveStatus = 'dirty'
      })
      queueAutosave()
    },

    save: async () => {
      if (inFlightSave) {
        await inFlightSave
      }
      const current = get()
      if (!current.projectId || !current.spec) return false
      if (current.changeCounter === current.savedCounter && current.saveStatus !== 'error') {
        return true
      }

      const projectId = current.projectId
      const spec = current.spec
      const version = current.version
      const counter = current.changeCounter

      set((state) => {
        state.saveStatus = 'saving'
      })

      const promise = (async () => {
        try {
          const response = await api.saveDraft(projectId, spec, version)
          set((state) => {
            state.version = response.version
            state.savedCounter = counter
            state.lastSavedAt = response.updatedAt
            state.saveStatus = state.changeCounter === counter ? 'saved' : 'dirty'
          })
          if (get().changeCounter !== counter) queueAutosave()
          return true
        } catch (error) {
          const message =
            error instanceof ApiError ? error.message : '自动保存失败，请稍后再试'
          set((state) => {
            state.saveStatus = 'error'
          })
          toast('error', message)
          return false
        } finally {
          inFlightSave = null
        }
      })()

      inFlightSave = promise
      return promise
    },

    sendMessage: async (content) => {
      const state = get()
      if (!state.projectId || !state.spec || state.sending) return
      const trimmed = content.trim()
      if (!trimmed) return

      for (let i = 0; i < 3; i += 1) {
        const before = get()
        if (before.changeCounter === before.savedCounter && before.saveStatus === 'saved') break
        const ok = await get().save()
        if (!ok) return
      }

      const projectId = get().projectId
      if (!projectId) return
      const tempId = `temp-${Date.now()}`
      const tempMessage: ChatMessage = {
        id: tempId,
        role: 'user',
        content: trimmed,
        createdAt: new Date().toISOString(),
      }

      set((draft) => {
        draft.sending = true
        draft.messages.push(tempMessage)
      })

      try {
        const response = await api.sendMessage(projectId, trimmed)
        const pendingImages = hasPendingImagePrompt(response.spec)
        set((draft) => {
          draft.messages = draft.messages.filter((message) => message.id !== tempId)
          draft.messages.push(response.userMessage, response.assistantMessage)
          if (response.changed) {
            recordHistory(draft, true)
            draft.spec = response.spec
            draft.changeCounter += 1
            draft.savedCounter = draft.changeCounter
            draft.saveStatus = 'saved'
          } else if (draft.spec) {
            draft.spec = response.spec
          }
          draft.version = response.version
          draft.sending = false
          draft.selection = null
          draft.tab = 'content'
          draft.lastSavedAt = new Date().toISOString()
          if (pendingImages) draft.imagePanelOpen = true
        })
        if (pendingImages) void get().refreshImageJobs()
      } catch (error) {
        set((draft) => {
          draft.messages = draft.messages.filter((message) => message.id !== tempId)
          draft.sending = false
        })
        toast('error', error instanceof ApiError ? error.message : 'Aster 暂时没有回应，请稍后再试')
      }
    },

    setImagePanelOpen: (open) => {
      set((state) => {
        state.imagePanelOpen = open
      })
    },

    loadImageJobs: async () => {
      const projectId = get().projectId
      if (!projectId) return
      set((state) => {
        state.imageJobsLoading = true
      })
      // 首次加载也走同一套同步逻辑，避免只更新版本号却不应用已完成的任务。
      await get().refreshImageJobs()
    },

    refreshImageJobs: async () => {
      const projectId = get().projectId
      if (!projectId) return
      try {
        const response = await api.listImageJobs(projectId)
        let changed = false
        set((state) => {
          state.imageJobs = response.jobs
          state.imageJobsLoading = false
          if (state.spec) {
            const next = cloneSpec(state.spec)
            if (applyCompletedJobs(next, response.jobs)) {
              state.history.push(cloneSpec(state.spec))
              if (state.history.length > 60) state.history.shift()
              state.future = []
              state.spec = next
              state.changeCounter += 1
              state.saveStatus = 'dirty'
              changed = true
            }
          }
          state.version = response.specVersion
        })
        if (changed) queueAutosave()
      } catch {
        set((state) => {
          state.imageJobsLoading = false
        })
      }
    },

    startImageGeneration: async (items) => {
      const state = get()
      const projectId = state.projectId
      if (!projectId || state.sending) return false
      if (items.length === 0) {
        toast('info', '先勾选要生成的图片')
        return false
      }
      for (let i = 0; i < 3; i += 1) {
        const before = get()
        if (before.changeCounter === before.savedCounter && before.saveStatus === 'saved') break
        const ok = await before.save()
        if (!ok) return false
      }
      try {
        const response = await api.createImageJobs(projectId, items)
        set((draft) => {
          draft.imagePanelOpen = true
          const known = new Set(response.jobs.map((job) => job.id))
          draft.imageJobs = [...response.jobs, ...draft.imageJobs.filter((job) => !known.has(job.id))]
          draft.version = response.specVersion
        })
        toast('success', `已提交 ${response.jobs.length} 张图片生成任务`)
        void get().refreshImageJobs()
        return true
      } catch (error) {
        toast('error', error instanceof ApiError ? error.message : '图片生成任务提交失败')
        return false
      }
    },
  })),
)
