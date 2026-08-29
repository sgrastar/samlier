import { FormEvent, useEffect, useMemo, useState } from 'react'
import { api, type Plan, type PlanInput, type Profile, type Run } from './api'
import { ResultReport } from './ResultReport'
import { ManagementBootstrap } from './ManagementBootstrap'
import { RunManagement } from './RunManagement'

const initialInput: PlanInput = {
  name: '',
  profile: 'IDP_CORE',
  targetKind: 'IDP',
  targetEntityId: '',
  metadataSourceKind: 'URL',
  metadataSourceLocation: '',
  suiteMetadataDelivery: 'MANUAL',
  declaredFeatures: {},
  parameters: { clockSkewToleranceSeconds: 180, metadataRefreshWaitSeconds: 300, testUserHint: '' },
  interaction: { allowBrowserSteps: true, allowAttestation: true },
  authorizedTarget: false,
}

export function App() {
  const reportRunId = window.location.pathname.match(/^\/reports\/(run_[0-9A-HJKMNP-TV-Z]{26})$/)?.[1]
  if (reportRunId) return <ResultReport runId={reportRunId} />
  const manageRunId = window.location.pathname.match(/^\/manage\/(run_[0-9A-HJKMNP-TV-Z]{26})$/)?.[1]
  if (manageRunId) return <ManagementBootstrap runId={manageRunId} />
  const browserRunId = window.location.pathname.match(/^\/browser\/(run_[0-9A-HJKMNP-TV-Z]{26})\/[A-Za-z0-9-]+$/)?.[1]
  if (browserRunId) return <main className="report"><RunManagement
    runId={browserRunId}
    csrfToken={window.sessionStorage.getItem(`samlier.csrf.${browserRunId}`) ?? undefined}
  /></main>

  const [plans, setPlans] = useState<Plan[]>([])
  const [selectedId, setSelectedId] = useState<string>()
  const [runs, setRuns] = useState<Run[]>([])
  const [input, setInput] = useState(initialInput)
  const [message, setMessage] = useState('')
  const [mode, setMode] = useState<'selfhosted' | 'hosted'>('selfhosted')
  const [managementUrl, setManagementUrl] = useState<string>()
  const selected = useMemo(() => plans.find((plan) => plan.plan.id === selectedId), [plans, selectedId])

  const refreshPlans = async () => {
    const value = await api.plans()
    setPlans(value)
    if (!selectedId && value[0]) setSelectedId(value[0].plan.id)
  }

  useEffect(() => {
    void api.health().then(async health => {
      setMode(health.mode)
      try {
        await refreshPlans()
      } catch (error) {
        if (health.mode === 'selfhosted') throw error
      }
    }).catch(error => setMessage(error.message))
  }, [])
  useEffect(() => {
    if (!selectedId) return
    void api.runs(selectedId).then(setRuns).catch(error => setMessage(error.message))
  }, [selectedId])

  const create = async (event: FormEvent) => {
    event.preventDefault()
    try {
      const created = await api.createPlan(input)
      setPlans(current => [created.plan, ...current])
      setSelectedId(created.plan.plan.id)
      setRuns(created.initialRun ? [created.initialRun.run] : [])
      setManagementUrl(created.initialRun?.managementUrl ?? undefined)
      setInput(initialInput)
      setMessage(created.initialRun?.managementUrl
        ? 'Test Plan and initial Run created. Save the protected management link shown below.'
        : 'Test Plan created. Register its metadata in the target before starting a Run.')
    } catch (error) { setMessage((error as Error).message) }
  }

  const createRun = async () => {
    if (!selected) return
    try {
      const csrfToken = runs.map(run => window.sessionStorage.getItem(`samlier.csrf.${run.id}`)).find(Boolean) ?? undefined
      const created = await api.createRun(selected.plan.id, csrfToken)
      setRuns(current => [created.run, ...current])
      setManagementUrl(created.managementUrl ?? undefined)
      if (created.managementUrl) {
        setMessage('Run created. Save the protected management link shown below.')
      } else {
        const report = await api.preflight(created.run.id)
        setMessage(`Preflight completed: ${JSON.stringify(report)}`)
        setRuns(await api.runs(selected.plan.id))
      }
    } catch (error) { setMessage((error as Error).message) }
  }

  return <main>
    <header>
      <p className="eyebrow">SAML CONFORMANCE TEST SUITE</p>
      <h1>Samlier</h1>
      <p className="lede">Run evidence-backed SAML interoperability checks, review unresolved obligations, and export a traceable result. Operational quick checks remain separate from conformance results.</p>
    </header>

    {message && <aside role="status">{message}</aside>}
    {managementUrl && <aside role="status" className="management-link">
      <strong>One-time management link</strong>
      <p>Save this link before opening it. The secret fragment is removed from browser history during exchange.</p>
      <a href={managementUrl}>Open protected Run</a>
    </aside>}

    <section className="grid">
      <form onSubmit={create} className="panel">
        <h2>New Test Plan</h2>
        <label>Name<input required value={input.name} onChange={e => setInput({ ...input, name: e.target.value })} /></label>
        <label>Profile<select value={input.profile} onChange={e => {
          const profile = e.target.value as Profile
          setInput({ ...input, profile, targetKind: profile.startsWith('IDP') ? 'IDP' : 'SP' })
        }}>
          <option value="IDP_CORE">IdP Core</option><option value="IDP_FULL">IdP Full</option>
          <option value="SP_CORE">SP Core</option><option value="SP_FULL">SP Full</option>
        </select></label>
        <label>Target entityID<input required type="url" value={input.targetEntityId} onChange={e => setInput({ ...input, targetEntityId: e.target.value })} /></label>
        <label>Target metadata URL<input required type="url" value={input.metadataSourceLocation} onChange={e => setInput({ ...input, metadataSourceLocation: e.target.value })} /></label>
        <label>Suite metadata delivery<select value={input.suiteMetadataDelivery} onChange={e => setInput({ ...input, suiteMetadataDelivery: e.target.value as PlanInput['suiteMetadataDelivery'] })}>
          <option value="MANUAL">Manual</option><option value="HTTP_URL">HTTP URL</option><option value="MDQ">MDQ</option>
        </select></label>
        <label className="radio"><input required type="checkbox" checked={input.authorizedTarget}
          onChange={e => setInput({ ...input, authorizedTarget: e.target.checked })} />
          I own or am authorized to test this target.</label>
        <button type="submit">Create plan</button>
      </form>

      <section className="panel">
        <h2>Test Plans</h2>
        {plans.length === 0 ? <p>No plans yet.</p> : <ul className="plan-list">{plans.map(plan =>
          <li key={plan.plan.id}><button className={selectedId === plan.plan.id ? 'selected' : ''} onClick={() => setSelectedId(plan.plan.id)}>
            <strong>{plan.plan.name}</strong><span>{plan.plan.profile.replace('_', ' ')}</span>
          </button></li>)}</ul>}
      </section>
    </section>

    {selected && <section className="panel detail">
      <div><p className="eyebrow">ACTIVE TEST PEER</p><h2>{selected.plan.name}</h2></div>
      <dl>
        <dt>Entity ID</dt><dd><code>{selected.entityId}</code></dd>
        <dt>Metadata</dt><dd><a href={selected.metadataUrl}>{selected.metadataUrl}</a></dd>
        <dt>MDQ</dt><dd><code>{selected.mdqUrl}</code></dd>
        <dt>Secondary IdP entity ID</dt><dd><code>{selected.secondaryIdpEntityId}</code></dd>
        <dt>Secondary IdP metadata</dt><dd><a href={selected.secondaryIdpMetadataUrl}>{selected.secondaryIdpMetadataUrl}</a></dd>
      </dl>
      {mode === 'selfhosted' && <div className="actions"><button onClick={createRun}>Create Run and preflight</button></div>}
      <h3>Runs</h3>
      {runs.map(run => <article className="run" key={run.id}>
        <div><strong>{run.status}</strong><small>{run.id}</small></div><span>{run.targetToSuiteReachability}</span>
        {selected.plan.profile.startsWith('IDP') && <a className="button" href={`/p/${selected.plan.id}/start/m0-roundtrip?run=${run.id}`}>Start IdP round trip</a>}
        {selected.plan.profile.startsWith('SP') && <p>Start login at the target SP after importing the Test Peer metadata.</p>}
        {run.status === 'COMPLETED' && <a href={`/reports/${run.id}`}>Open result</a>}
        {run.status === 'COMPLETED' && <a href={`/manage/${run.id}`}>Manage evidence</a>}
      </article>)}
    </section>}
  </main>
}
