import { useEffect, useState } from 'react'
import { api } from './api'
import { RunManagement } from './RunManagement'

/** Consumes the fragment locally, removes it before any network work, then exchanges it for a cookie. */
export function ManagementBootstrap({ runId }: { runId: string }) {
  const [state, setState] = useState<'exchanging' | 'ready' | 'error'>('exchanging')
  const [message, setMessage] = useState('')
  const [csrfToken, setCsrfToken] = useState<string>()

  useEffect(() => {
    const token = new URLSearchParams(window.location.hash.slice(1)).get('t') ?? ''
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}`)
    if (!token) {
      void api.health().then(health => {
        if (health.mode !== 'selfhosted') throw new Error('The management link has no access token.')
        setState('ready')
      }).catch(cause => {
        setState('error')
        setMessage((cause as Error).message)
      })
      return
    }
    void api.managementSession(runId, token).then(session => {
      window.sessionStorage.setItem(`samlier.csrf.${runId}`, session.csrfToken)
      setCsrfToken(session.csrfToken)
      setState('ready')
    }).catch(cause => {
      setState('error')
      setMessage((cause as Error).message)
    })
  }, [runId])

  return <main className="report">
    <p className="eyebrow">Samlier management</p>
    {state === 'exchanging' && <><h1>Opening Run</h1><p role="status">Exchanging the one-time link for a protected browser session…</p></>}
    {state === 'ready' && <><h1>Run unlocked</h1><p role="status">The secret has been removed from the address bar. Evidence decisions remain separate from Suite verdict calculation.</p><RunManagement runId={runId} csrfToken={csrfToken} /></>}
    {state === 'error' && <><h1>Access denied</h1><aside role="alert">{message}</aside></>}
  </main>
}
