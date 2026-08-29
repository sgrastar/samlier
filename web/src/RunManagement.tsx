import { FormEvent, useEffect, useState } from 'react'
import { api, type PendingInteraction } from './api'

export function RunManagement({ runId, csrfToken }: { runId: string; csrfToken?: string }) {
  const [interactions, setInteractions] = useState<PendingInteraction[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')
  const [planId, setPlanId] = useState('')
  const [profile, setProfile] = useState('')
  const [notice, setNotice] = useState('')
  const [mode, setMode] = useState<'selfhosted' | 'hosted'>('selfhosted')

  const refresh = async () => {
    const [nextInteractions, run, plans, health] = await Promise.all([
      api.interactions(runId), api.run(runId), api.plans(), api.health(),
    ])
    setInteractions(nextInteractions)
    setPlanId(run.planId)
    setProfile(plans.find(value => value.plan.id === run.planId)?.plan.profile ?? '')
    setMode(health.mode)
  }

  useEffect(() => { void refresh().catch(cause => setError((cause as Error).message)) }, [runId])

  const attest = async (event: FormEvent<HTMLFormElement>, interaction: PendingInteraction) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const value = String(data.get('value') ?? '')
    const note = String(data.get('note') ?? '')
    setBusy(interaction.caseId)
    setError('')
    try {
      await api.attest(runId, interaction.caseId, value, note, csrfToken)
      await refresh()
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setBusy('')
    }
  }

  const configure = async (event: FormEvent<HTMLFormElement>, interaction: PendingInteraction) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const value = String(data.get('value') ?? '')
    const note = value === 'confirmed' ? '' : String(data.get('note') ?? '')
    setBusy(interaction.caseId)
    setError('')
    try {
      await api.configure(runId, interaction.caseId, value, note, csrfToken)
      await refresh()
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setBusy('')
    }
  }

  const completeBrowser = async (interaction: PendingInteraction) => {
    setBusy(interaction.caseId)
    setError('')
    try {
      await api.completeBrowser(runId, interaction.caseId, csrfToken)
      await refresh()
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setBusy('')
    }
  }

  const startM1 = async () => {
    setBusy('quick-check')
    setError('')
    try {
      await api.quickCheck(runId, csrfToken)
      await refresh()
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setBusy('')
    }
  }

  const startMilestone = async (milestone: 'M2' | 'M3') => {
    setBusy(milestone)
    setError('')
    try {
      await api.startMilestone(runId, milestone, csrfToken)
      await refresh()
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setBusy('')
    }
  }

  const runEcpProbe = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    setBusy('ecp-probe')
    setError('')
    setNotice('')
    try {
      await api.ecpProbe(runId, String(data.get('username') ?? ''), String(data.get('password') ?? ''), csrfToken)
      form.reset()
      setNotice('The baseline ECP exchange, five channel-binding controls, and the SAML-EC session-key probe were recorded. M3 can now evaluate the transcript.')
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setBusy('')
    }
  }

  const publish = async () => {
    setBusy('publish')
    setError('')
    try {
      const result = await api.publish(runId, csrfToken)
      setNotice(`Published at ${result.publicUrl}`)
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setBusy('')
    }
  }

  return <section className="management panel">
    <div className="section-heading"><div><p className="eyebrow">Evidence workflow</p><h2>Pending interactions</h2></div><div className="actions">
      <button disabled={busy === 'quick-check'} onClick={() => void startM1()}>Start or resume M1</button>
      <button disabled={busy === 'M2'} onClick={() => void startMilestone('M2')}>Start or resume M2</button>
      <button disabled={busy === 'M3'} onClick={() => void startMilestone('M3')}>Start or resume M3</button>
      <button onClick={() => void refresh()}>Refresh</button>
    </div></div>
    {error && <aside role="alert">{error}</aside>}
    {notice && <aside role="status">{notice}</aside>}
    {profile === 'IDP_FULL' && <form className="interaction" onSubmit={event => void runEcpProbe(event)}>
      <fieldset disabled={busy === 'ecp-probe'}>
        <legend>ECP, channel-binding, and SAML-EC probes</legend>
        <p>Credentials are held in memory for this send only. They are never written to case state, the outbox, or the transcript.</p>
        <label>Username<input required name="username" autoComplete="username" /></label>
        <label>Password<input required name="password" type="password" autoComplete="current-password" /></label>
        <button type="submit">Run seven ECP probes before M3</button>
      </fieldset>
    </form>}
    {interactions.length === 0 ? <p className="quiet-success">No pending interactions.</p> :
      <div className="interaction-list">{interactions.map(interaction => <article key={interaction.caseId} className="interaction">
        <header><strong>{interaction.caseId}</strong><span>{interaction.kind}</span></header>
        {interaction.promptEn && <pre>{resolvePrompt(interaction.promptEn, planId, runId)}</pre>}
        {interaction.kind === 'BROWSER' && <div className="actions">
          {interaction.startUrl && <a className="button" href={interaction.startUrl}>Open focused browser step</a>}
          <button disabled={busy === interaction.caseId} onClick={() => void completeBrowser(interaction)}>
            Browser steps completed
          </button>
        </div>}
        {interaction.kind === 'ATTESTATION' && <form onSubmit={event => void attest(event, interaction)}>
          <fieldset disabled={busy === interaction.caseId}>
            <legend>Evidence conclusion</legend>
            {interaction.answerValues.map(value => <label className="radio" key={value}>
              <input required type="radio" name="value" value={value} />{humanize(value)}
            </label>)}
            <label>Evidence note<textarea name="note" maxLength={4000} placeholder="Identify the observed UI, log, trace, or other evidence." /></label>
            <button type="submit">Record attestation</button>
          </fieldset>
        </form>}
        {interaction.kind === 'CONFIGURATION' && <form onSubmit={event => void configure(event, interaction)}>
          <fieldset disabled={busy === interaction.caseId}>
            <legend>Configuration status</legend>
            {interaction.answerValues.map(value => <label className="radio" key={value}>
              <input required type="radio" name="value" value={value} />{humanize(value)}
            </label>)}
            <label>Unavailability note<textarea name="note" maxLength={4000} placeholder="Required unless the configuration was confirmed." /></label>
            <button type="submit">Continue case</button>
          </fieldset>
        </form>}
        <small>Expires {new Date(interaction.expiresAt).toLocaleString()}</small>
      </article>)}</div>}
    <div className="actions">
      <a className="button" href={`/reports/${runId}`}>Open current result</a>
      <a className="button" href={`/api/runs/${runId}/result.json`} download>Export result.json</a>
      <a className="button" href={`/api/runs/${runId}/report.html`} download>Export report.html</a>
      {mode === 'hosted' && <button disabled={busy === 'publish'} onClick={() => void publish()}>Publish hosted result</button>}
    </div>
  </section>
}

function resolvePrompt(value: string, planId: string, runId: string) {
  return value.replaceAll('<plan-id>', planId || '<plan-id>').replaceAll('<run-id>', runId)
}

function humanize(value: string) {
  return value.replaceAll('_', ' ').replace(/^./, first => first.toUpperCase())
}
