import { FormEvent, useEffect, useState } from 'react'
import {
  api, type ActiveProbeStatus, type BootstrapContract, type MetadataLab, type PendingInteraction, type Plan,
  type ProtocolEvidenceStatus, type CampaignReport,
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
  const [activeProbe, setActiveProbe] = useState<ActiveProbeStatus>()
  const [campaigns, setCampaigns] = useState<CampaignReport>()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')
  const [planId, setPlanId] = useState('')
  const [profile, setProfile] = useState('')
  const [plan, setPlan] = useState<Plan>()
  const [notice, setNotice] = useState('')
  const [mode, setMode] = useState<'selfhosted' | 'hosted'>('selfhosted')
  const [sectionCaseChoices, setSectionCaseChoices] = useState<Record<string, string>>({})
  const selfCheckSections = !focusCaseId && campaigns
    ? campaigns.campaigns.filter(campaign => campaign.evidenceClass === 'SELF_ATTESTED')
      .map(campaign => ({
        campaign,
        interactions: interactions.filter(interaction => campaign.remainingCaseIds.includes(interaction.caseId)),
      }))
      .filter(section => section.interactions.length > 0)
    : []
  const groupedSelfCheckCases = new Set(selfCheckSections.flatMap(section =>
    section.interactions.map(interaction => interaction.caseId)))
  const visibleInteractions = focusCaseId
    ? interactions.filter(interaction => interaction.caseId === focusCaseId)
    : interactions.filter(interaction => interaction.kind !== 'CONFIGURATION'
      && !groupedSelfCheckCases.has(interaction.caseId))
  const configurationInteractions = interactions.filter(interaction => interaction.kind === 'CONFIGURATION')

  const refresh = async () => {
    const [nextInteractions, contracts, lab, evidence, probe, campaignReport, run, plans, health] = await Promise.all([
      api.interactions(runId), api.bootstrapContracts(runId), api.metadataLab(runId),
      api.protocolEvidence(runId), api.activeProbe(runId),
      api.campaigns(runId),
      api.run(runId), api.plans(), api.health(),
    ])
    setInteractions(nextInteractions)
    setBootstrapContracts(contracts)
    setMetadataLab(lab)
    setProtocolEvidence(evidence)
    setActiveProbe(probe)
    if (Array.isArray(campaignReport?.plans)) setCampaigns(campaignReport)
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

  const submitSelfCheckSection = async (
    event: FormEvent<HTMLFormElement>,
    section: (typeof selfCheckSections)[number],
  ) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const note = String(data.get('shared-note') ?? '')
    const sectionConclusion = String(data.get('section-conclusion') ?? '')
    if (!sectionConclusion) {
      setError(`Choose one evidence conclusion for ${section.campaign.title}.`)
      return
    }
    if (sectionConclusion !== 'unable_to_verify' && !note.trim()) {
      setError(`Identify the shared evidence for ${section.campaign.title}.`)
      return
    }
    setBusy(section.campaign.id)
    setError('')
    try {
      for (const interaction of section.interactions) {
        const conclusion = String(data.get(`override:${interaction.caseId}`) ?? '') || sectionConclusion
        if (interaction.kind === 'CONFIGURATION') {
          const configuration = conclusion === 'unable_to_verify' ? 'capability_undetermined' : 'confirmed'
          await api.configure(runId, interaction.caseId, configuration,
            configuration === 'confirmed' ? '' : note, csrfToken)
          if (configuration === 'confirmed') {
            await api.attest(runId, interaction.caseId,
              conclusion === 'satisfied' ? 'evidence_satisfies' : 'evidence_violates', note, csrfToken)
          }
        } else if (interaction.kind === 'ATTESTATION') {
          await api.attest(runId, interaction.caseId, attestationValue(interaction, conclusion), note, csrfToken)
        }
      }
      setNotice(`Recorded the ${section.campaign.title} evidence section. Each case retained its own server-defined outcome mapping.`)
      await refresh()
    } catch (cause) {
      setError((cause as Error).message)
      await refresh().catch(() => undefined)
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

  const abortActiveProbe = async () => {
    setBusy('active-probe-abort')
    setError('')
    try {
      await api.abortActiveProbe(runId, csrfToken)
      setNotice('No correlated SAML Response was returned. This fixture was marked unavailable, never as a target failure; any remaining scenario controls will continue.')
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

  const confirmProtocolEvidenceAttempts = async () => {
    setBusy('protocol-attempts')
    setError('')
    try {
      const evaluation = await api.confirmProtocolEvidenceAttempts(runId, csrfToken)
      const completed = evaluation.completed.map(value => `${value.caseId}: ${humanize(value.outcome)}`)
      setNotice(completed.length > 0
        ? `Completed the metadata campaign from recorded evidence: ${completed.join(', ')}.`
        : 'No pending metadata campaign cases were found.')
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
      {interaction.completionMode === 'TRANSCRIPT'
        ? <p>Waiting for the required correlated Transcript evidence. No completion answer is needed.</p>
        : <button disabled={busy === interaction.caseId} onClick={() => void completeBrowser(interaction)}>
            Browser steps completed
          </button>}
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
        <legend>{interaction.completionMode === 'TRANSCRIPT_OR_OPERATOR'
          ? 'Automatic protocol evidence with manual unavailability fallback'
          : 'Manual configuration input'}</legend>
        {interaction.completionMode === 'TRANSCRIPT_OR_OPERATOR' && <p>
          This case completes automatically when all required correlated Transcript evidence is present.
          Use the answers below only when the target capability or required setup cannot be exercised.
        </p>}
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
          <p>Samlier normally evaluates these cases automatically as Transcript evidence arrives. Because a public metadata fetch does not identify its caller, use the recovery action only after you triggered the target's normal refresh or re-import and attempted the listed SAML flows.</p>
          <button disabled={busy === 'protocol-evidence' || protocolEvidence.readyCases === 0}
            onClick={() => void evaluateProtocolEvidence()}>
            Re-evaluate recorded evidence
          </button>
          <button disabled={busy === 'protocol-attempts'}
            onClick={() => void confirmProtocolEvidenceAttempts()}>
            Confirm all listed refreshes and flows were attempted
          </button>
          <p><small>This confirms only that you performed the listed operations. It is not a verdict questionnaire; Samlier derives every outcome from the recorded fetches and SAML traffic.</small></p>
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
    {!focusCaseId && campaigns && <section className="campaign-overview">
      <p className="eyebrow">Run plans</p>
      <h2>Choose evidence depth, not individual cases</h2>
      <p>Cases share Transcripts, metadata fetches, and configuration campaigns. Counts below are deliberate user actions, not case counts.</p>
      <div className="contract-list">{campaigns.plans.map(value => <article className="contract" key={value.plan}>
        <header><div><strong>{humanize(value.plan)}</strong><p>{planDescription(value.plan)}</p></div>
          <span>{value.budgetMet ? 'WITHIN BUDGET' : 'OVER BUDGET'}</span></header>
        <dl>
          <dt>Cases</dt><dd>{value.cases}</dd>
          <dt>Actions</dt><dd>{value.deliberateUserActions} total / {value.remainingUserActions} remaining (budget {value.actionBudget})</dd>
          <dt>Estimated time</dt><dd>{value.estimatedMinutesMin}–{value.estimatedMinutesMax} minutes</dd>
          <dt>Action mix</dt><dd>{value.loginActions} login, {value.configurationActions} configuration, {value.metadataRefreshActions} metadata refresh</dd>
          {value.plan === 'FULL' && <><dt>Self-check sections</dt><dd>{value.selfAttestationSections}</dd></>}
        </dl>
      </article>)}</div>
      <p><strong>Externally verified:</strong> {campaigns.externallyVerifiedCases} · <strong>Self-attested:</strong> {campaigns.selfAttestedCases} · <strong>Not verified:</strong> {campaigns.notVerifiedCases}</p>
      <details><summary>Evidence campaigns and shared cases</summary>
        <div className="contract-list">{campaigns.campaigns.map(campaign => <article className="contract" key={campaign.id}>
          <header><div><strong>{campaign.title}</strong><p>{humanize(campaign.evidenceClass)}</p></div><span>{campaign.remainingUserActions} action{campaign.remainingUserActions === 1 ? '' : 's'} remaining</span></header>
          {campaign.freshSessionRequired && <p>A fresh target session is required at the campaign boundary.</p>}
          {campaign.expectedTranscriptEvidence.length > 0 && <p>Expected evidence: {campaign.expectedTranscriptEvidence.join(', ')}</p>}
          <p>{campaign.caseIds.length} cases share this campaign; {campaign.remainingCaseIds.length} remain unresolved.</p>
        </article>)}</div>
      </details>
    </section>}
    {selfCheckSections.length > 0 && <section className="self-check-sections">
      <p className="eyebrow">Full plan evidence</p>
      <h2>Grouped self-check sections</h2>
      <p>These are the cases that cannot currently be proved from standard SAML, browser, metadata, or Transcript evidence. Record one evidence conclusion per section. Open case-specific overrides only when the shared evidence supports different conclusions.</p>
      <div className="interaction-list">{selfCheckSections.map(section =>
        <form className="interaction" key={section.campaign.id}
          onSubmit={event => void submitSelfCheckSection(event, section)}>
          <fieldset disabled={busy === section.campaign.id}>
            <legend>{section.campaign.title}</legend>
            <p>{section.interactions.length} approved cases reuse this evidence decision.</p>
            <label>Section evidence conclusion<select required name="section-conclusion" defaultValue="">
              <option value="">Choose…</option>
              <option value="satisfied">Evidence satisfies every listed case</option>
              <option value="violated">Evidence violates every listed case</option>
              <option value="unable_to_verify">Unable to verify this evidence section</option>
            </select></label>
            <details><summary>Case scope and optional overrides</summary>
              <p>Leave an override unset to reuse the section conclusion. An override changes only that case; it does not alter the approved verdict mapping.</p>
              {section.interactions.map(interaction => <article key={interaction.caseId}>
                <header><strong>{interaction.caseId}</strong><span>{interaction.kind}</span></header>
                {interaction.promptEn && <details><summary>Approved evidence instructions</summary>
                  <pre>{resolvePrompt(interaction.promptEn, planId, runId)}</pre>
                </details>}
                <label>Case-specific override<select name={`override:${interaction.caseId}`}
                  value={sectionCaseChoices[interaction.caseId] ?? ''}
                  onChange={event => setSectionCaseChoices(current => ({
                    ...current, [interaction.caseId]: event.target.value,
                  }))}>
                  <option value="">Use section conclusion</option>
                  <option value="satisfied">Evidence satisfies this case</option>
                  <option value="violated">Evidence violates this case</option>
                  <option value="unable_to_verify">Unable to verify this case</option>
                </select></label>
              </article>)}
            </details>
            <label>Shared evidence note<textarea name="shared-note" maxLength={4000}
              placeholder="Identify the shared configuration, trace, policy, or review evidence once for this section." /></label>
            <button type="submit">Record section</button>
          </fieldset>
        </form>)}</div>
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
    {activeProbe?.state === 'READY' && activeProbe.startUrl && <article className="interaction active-probe">
      <header><strong>{activeProbe.caseId ?? 'Browser-assisted SAML scenario'}</strong><span>AUTOMATED ORACLE</span></header>
      <p>{activeProbe.instructionsEn}</p>
      {activeProbe.requiresFreshSession && <p>The first IsPassive probe must start in a private browser context with no active target session.</p>}
      <a className="button" href={activeProbe.startUrl}>Open scenario</a>
    </article>}
    {activeProbe?.state === 'AWAITING_RESPONSE' && <article className="interaction active-probe">
      <header><strong>{activeProbe.caseId ?? 'Browser-assisted SAML scenario'}</strong><span>WAITING</span></header>
      <p>The request was dispatched. Complete target login or consent in that browser. Samlier will continue automatically after a correlated SAML Response.</p>
      <button disabled={busy === 'active-probe-abort'} onClick={() => void abortActiveProbe()}>
        No SAML Response was returned
      </button>
    </article>}
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

function planDescription(plan: 'QUICK' | 'STANDARD' | 'FULL') {
  if (plan === 'QUICK') return 'Protocol-observed evidence only.'
  if (plan === 'STANDARD') return 'Quick plus operator-assisted configuration and refresh actions.'
  return 'Standard plus grouped self-attested evidence that cannot be externally observed.'
}

function attestationValue(interaction: PendingInteraction, conclusion: string) {
  const candidates = conclusion === 'satisfied'
    ? ['satisfied', 'evidence_satisfies']
    : conclusion === 'violated'
      ? ['violated', 'evidence_violates']
      : ['unable_to_verify', 'unable']
  const value = candidates.find(candidate => interaction.answerValues.includes(candidate))
  if (!value) throw new Error(`The approved answer mapping for ${interaction.caseId} has no ${conclusion} option.`)
  return value
}
