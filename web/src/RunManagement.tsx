import { FormEvent, useEffect, useState } from 'react'
import {
  api, type BootstrapContract, type MetadataLab, type PendingInteraction, type Plan,
  type ProtocolEvidenceStatus,
} from './api'

export function RunManagement({ runId, csrfToken, focusCaseId }: {
  runId: string
  csrfToken?: string
  focusCaseId?: string
}) {
  const [interactions, setInteractions] = useState<PendingInteraction[]>([])
  const [bootstrapContracts, setBootstrapContracts] = useState<BootstrapContract[]>([])
  const [metadataLab, setMetadataLab] = useState<MetadataLab>()
  const [protocolEvidence, setProtocolEvidence] = useState<ProtocolEvidenceStatus>()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')
  const [planId, setPlanId] = useState('')
  const [profile, setProfile] = useState('')
  const [plan, setPlan] = useState<Plan>()
  const [notice, setNotice] = useState('')
  const [mode, setMode] = useState<'selfhosted' | 'hosted'>('selfhosted')
  const visibleInteractions = focusCaseId
    ? interactions.filter(interaction => interaction.caseId === focusCaseId)
    : interactions.filter(interaction => interaction.kind !== 'CONFIGURATION')
  const configurationInteractions = interactions.filter(interaction => interaction.kind === 'CONFIGURATION')

  const refresh = async () => {
    const [nextInteractions, contracts, lab, evidence, run, plans, health] = await Promise.all([
      api.interactions(runId), api.bootstrapContracts(runId), api.metadataLab(runId),
      api.protocolEvidence(runId),
      api.run(runId), api.plans(), api.health(),
    ])
    setInteractions(nextInteractions)
    setBootstrapContracts(contracts)
    setMetadataLab(lab)
    setProtocolEvidence(evidence)
    setPlanId(run.planId)
    const selectedPlan = plans.find(value => value.plan.id === run.planId)
    setPlan(selectedPlan)
    setProfile(selectedPlan?.plan.profile ?? '')
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

  const runPreflight = async () => {
    setBusy('preflight')
    setError('')
    try {
      const report = await api.preflight(runId, csrfToken)
      setNotice(`Preflight completed: ${JSON.stringify(report)}`)
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

  const selectMetadataVariant = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const variant = String(new FormData(event.currentTarget).get('variant') ?? '')
    setBusy('metadata-variant')
    setError('')
    try {
      const selected = await api.selectMetadataVariant(runId, variant, csrfToken)
      setMetadataLab(selected)
      setNotice(`Metadata fixture selected: ${selected.selectedVariant}. The stable metadata URL did not change.`)
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setBusy('')
    }
  }

  const evaluateProtocolEvidence = async () => {
    setBusy('protocol-evidence')
    setError('')
    try {
      const evaluation = await api.evaluateProtocolEvidence(runId, csrfToken)
      const completed = evaluation.completed.map(value => `${value.caseId}: ${humanize(value.outcome)}`)
      setNotice(completed.length > 0
        ? `Evaluated recorded protocol evidence: ${completed.join(', ')}.`
        : 'No evidence-driven case is ready. Complete the listed fixture fetches and correlated SAML attempts first.')
      await refresh()
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setBusy('')
    }
  }

  const interactionCard = (interaction: PendingInteraction) => <article key={interaction.caseId} className="interaction">
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
        <legend>Manual fallback for this case</legend>
        {interaction.answerValues.map(value => <label className="radio" key={value}>
          <input required type="radio" name="value" value={value} />{humanize(value)}
        </label>)}
        <label>Unavailability note<textarea name="note" maxLength={4000} placeholder="Required unless the configuration was confirmed." /></label>
        <button type="submit">Continue case</button>
      </fieldset>
    </form>}
    <small>Expires {new Date(interaction.expiresAt).toLocaleString()}</small>
  </article>

  return <section className="management panel">
    {plan && <section className="peer-registration">
      <p className="eyebrow">Test Peer registration</p>
      <h2>{plan.plan.name}</h2>
      <dl>
        <dt>Entity ID</dt><dd><code>{plan.entityId}</code></dd>
        <dt>Metadata</dt><dd><a href={plan.metadataUrl}>{plan.metadataUrl}</a></dd>
        <dt>MDQ</dt><dd><code>{plan.mdqUrl}</code></dd>
        <dt>Secondary IdP metadata</dt><dd><a href={plan.secondaryIdpMetadataUrl}>{plan.secondaryIdpMetadataUrl}</a></dd>
      </dl>
    </section>}
    {!focusCaseId && bootstrapContracts.length > 0 && <section className="bootstrap-contracts">
      <p className="eyebrow">One-time environment bootstrap</p>
      <h2>Shared setup contracts</h2>
      <p>These contracts replace repeated product-specific setup questions. Samlier uses standard SAML metadata and protocol traffic; it does not call a vendor Admin API.</p>
      <div className="contract-list">{bootstrapContracts.map(contract => <article className="contract" key={contract.id}>
        <header><div><strong>{contract.title}</strong><p>{contract.description}</p></div><span>{humanize(contract.readiness)}</span></header>
        <p>{contract.setupInstruction}</p>
        {contract.setupUrl && <dl><dt>Stable setup URL</dt><dd><code>{contract.setupUrl}</code></dd></dl>}
        {contract.kind === 'STANDARD_METADATA' && metadataLab && <form className="fixture-selector" onSubmit={selectMetadataVariant}>
          <label>Suite metadata fixture<select name="variant" defaultValue={metadataLab.selectedVariant}>
            {metadataLab.availableVariants.map(variant => <option key={variant} value={variant}>{humanize(variant)}</option>)}
          </select></label>
          <button disabled={busy === 'metadata-variant'} type="submit">Select behind stable URL</button>
        </form>}
        {contract.kind === 'STANDARD_METADATA' && protocolEvidence && protocolEvidence.eligibleCases > 0 && <div className="protocol-evidence">
          <p><strong>{protocolEvidence.eligibleCases}</strong> currently implemented case{protocolEvidence.eligibleCases === 1 ? '' : 's'} can derive outcomes directly from metadata fetches and correlated SAML traffic; <strong>{protocolEvidence.readyCases}</strong> ready now.</p>
          <p>Because a public metadata fetch does not identify its caller, use this action only after you triggered the target's normal refresh or re-import and attempted the listed SAML flows.</p>
          <button disabled={busy === 'protocol-evidence' || protocolEvidence.readyCases === 0}
            onClick={() => void evaluateProtocolEvidence()}>
            Refreshes and attempts completed — evaluate evidence
          </button>
          <details><summary>Protocol observation progress</summary><ul>
            {protocolEvidence.cases.map(value => <li key={value.caseId}>
              <code>{value.caseId}</code>: {value.completedObservations.length}/{value.requiredObservations.length} required observations
            </li>)}
          </ul></details>
        </div>}
        <details><summary>{contract.pendingCases} cases share this environment</summary>
          <p>Open an individual case only when the standard protocol driver cannot obtain its evidence. Confirming setup is not itself a verdict.</p>
          <ul className="case-links">{contract.caseIds.map(caseId => <li key={caseId}>
            <a href={`/browser/${runId}/${caseId}`}>{caseId}</a>
          </li>)}</ul>
        </details>
      </article>)}</div>
    </section>}
    <div className="section-heading"><div><p className="eyebrow">Evidence workflow</p><h2>Pending interactions</h2></div><div className="actions">
      <button disabled={busy === 'preflight'} onClick={() => void runPreflight()}>Run preflight</button>
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
    {plan && profile.startsWith('IDP') && <div className="actions">
      <a className="button" href={`/p/${plan.plan.id}/start/m0-roundtrip?run=${runId}`}>Start IdP round trip</a>
    </div>}
    {plan && profile.startsWith('SP') && <p>Start login at the target SP after importing the Test Peer metadata.</p>}
    {focusCaseId && <div className="actions"><a className="button" href={`/manage/${runId}`}>Back to Run management</a></div>}
    {visibleInteractions.length === 0 ? <p className="quiet-success">
      {focusCaseId ? `No pending interaction for ${focusCaseId}.`
        : configurationInteractions.length > 0
          ? 'Configuration prerequisites are grouped under the bootstrap contracts above.'
          : 'No pending interactions.'}
    </p> :
      <div className="interaction-list">{visibleInteractions.map(interactionCard)}</div>}
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
