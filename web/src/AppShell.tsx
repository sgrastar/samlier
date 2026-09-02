import { ReactNode, useEffect, useState } from 'react'

type CurrentSurface = 'plans' | 'run' | 'report' | 'access'

export function AppShell({
  children,
  current = 'plans',
  mode = 'selfhosted',
  runId,
}: {
  children: ReactNode
  current?: CurrentSurface
  mode?: 'selfhosted' | 'hosted'
  runId?: string
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [theme, setTheme] = useState<'light' | 'dark'>(() => preferredTheme())

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try { window.localStorage.setItem('samlier.theme', theme) } catch { /* private mode */ }
  }, [theme])

  return <div className="app-frame">
    <header className="app-topbar">
      <a className="wordmark" href="/">Samlier <small>SAML CONFORMANCE</small></a>
      <button
        className="topbar-nav-toggle"
        type="button"
        aria-label="Toggle navigation"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen(open => !open)}
      >Menu</button>
      <nav className={`app-nav${menuOpen ? ' open' : ''}`} aria-label="Primary navigation">
        <a className={current === 'plans' ? 'current' : ''} href="/">Test Plans</a>
        {runId && <a className={current === 'run' ? 'current' : ''} href={`/manage/${runId}`}>Run Workspace</a>}
        {runId && <a className={current === 'report' ? 'current' : ''} href={`/reports/${runId}`}>Result Report</a>}
      </nav>
      <div className="app-topbar-spacer" />
      <span className="mode-chip"><span className="semantic-dot status-live" />{mode === 'hosted' ? 'Hosted' : 'Self-hosted'}</span>
      <button
        className="theme-toggle"
        type="button"
        onClick={() => setTheme(value => value === 'dark' ? 'light' : 'dark')}
        aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
      >{theme === 'dark' ? 'Light' : 'Dark'}</button>
    </header>
    {children}
  </div>
}

export function preferredTheme(): 'light' | 'dark' {
  try {
    const saved = window.localStorage.getItem('samlier.theme')
    if (saved === 'light' || saved === 'dark') return saved
  } catch { /* private mode */ }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function applyPreferredTheme() {
  document.documentElement.dataset.theme = preferredTheme()
}
