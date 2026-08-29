import { FormEvent, useEffect, useState } from 'react'
import { api, type PendingInteraction } from './api'

export function RunManagement({ runId, csrfToken }: { runId: string; csrfToken?: string }) {
  const [interactions, setInteractions] = useState<PendingInteraction[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')

  const refresh = async () => {
    setInteractions(await api.interactions(runId))
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

  return <section className="management panel">
    <div className="section-heading"><div><p className="eyebrow">Evidence workflow</p><h2>Pending interactions</h2></div><button onClick={() => void refresh()}>Refresh</button></div>
    {error && <aside role="alert">{error}</aside>}
    {interactions.length === 0 ? <p className="quiet-success">No pending interactions.</p> :
      <div className="interaction-list">{interactions.map(interaction => <article key={interaction.caseId} className="interaction">
        <header><strong>{interaction.caseId}</strong><span>{interaction.kind}</span></header>
        {interaction.promptEn && <pre>{interaction.promptEn}</pre>}
        {interaction.kind === 'BROWSER' && interaction.startUrl && <a className="button" href={interaction.startUrl}>Open browser step</a>}
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
        <small>Expires {new Date(interaction.expiresAt).toLocaleString()}</small>
      </article>)}</div>}
    <p><a href={`/reports/${runId}`}>Open current result</a></p>
  </section>
}

function humanize(value: string) {
  return value.replaceAll('_', ' ').replace(/^./, first => first.toUpperCase())
}
