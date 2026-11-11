import type { SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement>

function Svg({ children, ...props }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      {children}
    </svg>
  )
}

export function LogoMark({ className = '' }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 40 40" fill="none" aria-hidden="true">
      <rect width="40" height="40" rx="11" fill="currentColor" />
      <path
        d="M20 7.5l3.5 9L32.5 20l-9 3.5L20 32.5l-3.5-9L7.5 20l9-3.5L20 7.5z"
        fill="#ff6b3d"
      />
    </svg>
  )
}

export const IconArrowLeft = (p: IconProps) => (
  <Svg {...p}>
    <path d="M19 12H5" />
    <path d="m12 19-7-7 7-7" />
  </Svg>
)

export const IconArrowRight = (p: IconProps) => (
  <Svg {...p}>
    <path d="M5 12h14" />
    <path d="m12 5 7 7-7 7" />
  </Svg>
)

export const IconArrowUpRight = (p: IconProps) => (
  <Svg {...p}>
    <path d="M7 17 17 7" />
    <path d="M7 7h10v10" />
  </Svg>
)

export const IconPlus = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 5v14" />
    <path d="M5 12h14" />
  </Svg>
)

export const IconSparkles = (p: IconProps) => (
  <Svg {...p}>
    <path d="m12 3 1.6 4.4L18 9l-4.4 1.6L12 15l-1.6-4.4L6 9l4.4-1.6L12 3z" />
    <path d="m19 15 .8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15z" />
    <path d="m5 14 .6 1.8L7.5 16l-1.9.7L5 18.5l-.6-1.8L2.5 16l1.9-.2L5 14z" />
  </Svg>
)

export const IconSend = (p: IconProps) => (
  <Svg {...p}>
    <path d="m22 2-7 20-4-9-9-4 20-7z" />
    <path d="M22 2 11 13" />
  </Svg>
)

export const IconUndo = (p: IconProps) => (
  <Svg {...p}>
    <path d="M9 14 4 9l5-5" />
    <path d="M4 9h10a6 6 0 0 1 0 12h-1" />
  </Svg>
)

export const IconRedo = (p: IconProps) => (
  <Svg {...p}>
    <path d="m15 14 5-5-5-5" />
    <path d="M20 9H10a6 6 0 0 0 0 12h1" />
  </Svg>
)

export const IconMonitor = (p: IconProps) => (
  <Svg {...p}>
    <rect x="2.5" y="4" width="19" height="13" rx="2" />
    <path d="M8 21h8" />
    <path d="M12 17v4" />
  </Svg>
)

export const IconTablet = (p: IconProps) => (
  <Svg {...p}>
    <rect x="5" y="2.5" width="14" height="19" rx="2.5" />
    <path d="M11 18.5h2" />
  </Svg>
)

export const IconSmartphone = (p: IconProps) => (
  <Svg {...p}>
    <rect x="7" y="2" width="10" height="20" rx="2.5" />
    <path d="M11 5.5h2" />
  </Svg>
)

export const IconPencil = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 20h9" />
    <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z" />
  </Svg>
)

export const IconType = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 7V4h16v3" />
    <path d="M9 20h6" />
    <path d="M12 4v16" />
  </Svg>
)

export const IconPalette = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 3a9 9 0 0 0 0 18h1.5a2 2 0 0 0 0-4H12a2 2 0 0 1 0-4h4a5 5 0 0 0 5-5 5 5 0 0 0-5-5h-4z" />
    <circle cx="7.5" cy="10" r="1" fill="currentColor" stroke="none" />
    <circle cx="9.5" cy="6.5" r="1" fill="currentColor" stroke="none" />
    <circle cx="14" cy="6.5" r="1" fill="currentColor" stroke="none" />
  </Svg>
)

export const IconImage = (p: IconProps) => (
  <Svg {...p}>
    <rect x="3" y="3" width="18" height="18" rx="2" />
    <circle cx="8.5" cy="8.5" r="1.5" />
    <path d="m21 15-5-5L5 21" />
  </Svg>
)

export const IconLayout = (p: IconProps) => (
  <Svg {...p}>
    <rect x="3" y="3" width="18" height="18" rx="2" />
    <path d="M3 9h18" />
    <path d="M9 21V9" />
  </Svg>
)

export const IconDownload = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 3v12" />
    <path d="m7 10 5 5 5-5" />
    <path d="M5 21h14" />
  </Svg>
)

export const IconCheck = (p: IconProps) => (
  <Svg {...p}>
    <path d="m20 6-11 11-5-5" />
  </Svg>
)

export const IconLoader = (p: IconProps) => (
  <Svg {...p}>
    <path d="M21 12a9 9 0 1 1-6.2-8.6" />
  </Svg>
)

export const IconX = (p: IconProps) => (
  <Svg {...p}>
    <path d="M18 6 6 18" />
    <path d="m6 6 12 12" />
  </Svg>
)

export const IconLogOut = (p: IconProps) => (
  <Svg {...p}>
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <path d="m16 17 5-5-5-5" />
    <path d="M21 12H9" />
  </Svg>
)

export const IconChevronDown = (p: IconProps) => (
  <Svg {...p}>
    <path d="m6 9 6 6 6-6" />
  </Svg>
)

export const IconChevronRight = (p: IconProps) => (
  <Svg {...p}>
    <path d="m9 18 6-6-6-6" />
  </Svg>
)

export const IconUpload = (p: IconProps) => (
  <Svg {...p}>
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <path d="m7 8 5-5 5 5" />
    <path d="M12 3v12" />
  </Svg>
)

export const IconEye = (p: IconProps) => (
  <Svg {...p}>
    <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" />
    <circle cx="12" cy="12" r="3" />
  </Svg>
)

export const IconTrash = (p: IconProps) => (
  <Svg {...p}>
    <path d="M3 6h18" />
    <path d="M8 6V4h8v2" />
    <path d="M19 6 18 21H6L5 6" />
    <path d="M10 11v6" />
    <path d="M14 11v6" />
  </Svg>
)

export const IconRefresh = (p: IconProps) => (
  <Svg {...p}>
    <path d="M21 12a9 9 0 1 1-2.6-6.4" />
    <path d="M21 3v6h-6" />
  </Svg>
)

export const IconMessage = (p: IconProps) => (
  <Svg {...p}>
    <path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v8z" />
  </Svg>
)

export const IconMenu = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 6h16" />
    <path d="M4 12h16" />
    <path d="M4 18h16" />
  </Svg>
)

export const IconGlobe = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M3 12h18" />
    <path d="M12 3a14 14 0 0 1 0 18" />
    <path d="M12 3a14 14 0 0 0 0 18" />
  </Svg>
)

export const IconSettings = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-1.42 1.42-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-1.03 1.56V20h-2v-.08a1.7 1.7 0 0 0-1.03-1.56 1.7 1.7 0 0 0-1.88.34l-.06.06-1.42-1.42.06-.06A1.7 1.7 0 0 0 9.42 15a1.7 1.7 0 0 0-1.56-1.03H7v-2h.86A1.7 1.7 0 0 0 9.42 11a1.7 1.7 0 0 0-.34-1.88l-.06-.06 1.42-1.42.06.06a1.7 1.7 0 0 0 1.88.34A1.7 1.7 0 0 0 13.41 6.5V6h2v.5a1.7 1.7 0 0 0 1.03 1.56 1.7 1.7 0 0 0 1.88-.34l.06-.06 1.42 1.42-.06.06A1.7 1.7 0 0 0 19.4 11c.22.63.82 1.03 1.49 1.03H21v2h-.11c-.67 0-1.27.4-1.49.97z" />
  </Svg>
)

export const IconKey = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="8" cy="15" r="4" />
    <path d="m11 12 9-9" />
    <path d="m17 6 3 3" />
    <path d="m14 9 3 3" />
  </Svg>
)

export const IconServer = (p: IconProps) => (
  <Svg {...p}>
    <rect x="3" y="4" width="18" height="7" rx="2" />
    <rect x="3" y="13" width="18" height="7" rx="2" />
    <path d="M7 7.5h.01" />
    <path d="M7 16.5h.01" />
    <path d="M11 7.5h6" />
    <path d="M11 16.5h6" />
  </Svg>
)
