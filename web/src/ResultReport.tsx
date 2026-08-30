import { useEffect, useState } from 'react'
import { api, type PublicResult } from './api'

export function ResultReport({ runId }: { runId: string }) {
  const [result, setResult] = useState<PublicResult>()
  const [error, setError] = useState('')

  useEffect(() => {
    void api.result(runId).then(setResult).catch(cause => setError((cause as Error).message))
  }, [runId])

  if (error) return <main className="report"><p className="eyebrow">Samlier result</p><h1>Result unavailable</h1><aside role="alert">{error}</aside></main>
  if (!result) return <main className="report"><p className="eyebrow">Samlier result</p><p role="status">Loading authoritative result…</p></main>

  const ratio = Math.round(result.coverage.verifiedRatio * 1000) / 10
  return <main className="report">
    <header className="report-header">
      <div>
        <p className="eyebrow">Samlier conformance result</p>
        <h1>{humanize(result.run.conformance)}</h1>
        <p className="lede">{result.conformanceStatement}</p>
      </div>
      <div className={`verdict verdict-${result.run.conformance.toLowerCase()}`}>
        <span>Completeness</span><strong>{humanize(result.run.completeness)}</strong>
      </div>
    </header>

    <section className="metrics" aria-label="Result coverage">
      <Metric label="Verified MUST" value={`${result.coverage.mustResolved}/${result.coverage.mustObservable}`} />
      <Metric label="Applicable obligations" value={`${result.coverage.obligationsApplicable}/${result.coverage.obligationsTotal}`} />
      <Metric label="Verified ratio" value={`${ratio}%`} />
      <Metric label="Unresolved MUST" value={String(result.coverage.mustUnresolved)} />
    </section>

    <section className="panel report-section">
      <h2>Evidence provenance</h2>
      <p>Externally verified and self-attested evidence are reported separately; self-attestation is never presented as external verification.</p>
      <dl>
        <dt>Externally verified</dt><dd>{result.evidenceSummary.externallyVerified} cases</dd>
        <dt>Self-attested</dt><dd>{result.evidenceSummary.selfAttested} cases</dd>
        <dt>Not verified</dt><dd>{result.evidenceSummary.notVerified} cases</dd>
      </dl>
    </section>

    <section className="report-grid">
      <section className="panel">
        <h2>Target and provenance</h2>
        <dl>
          <dt>Product</dt><dd>{result.target.declaredProduct} <small>(declared)</small></dd>
          <dt>Entity ID</dt><dd><code>{result.target.entityId}</code></dd>
          <dt>Role</dt><dd>{result.target.role} / {result.target.kind}</dd>
          <dt>Profile</dt><dd>{result.profile.id}</dd>
          <dt>Suite</dt><dd>{result.suite.name} {result.suite.version}</dd>
          <dt>Evaluation bundle</dt><dd><code>{shortDigest(result.evaluationBundle.digest)}</code></dd>
          <dt>Run</dt><dd><code>{result.run.id}</code></dd>
        </dl>
      </section>

      <section className="panel">
        <h2>Resolution status</h2>
        {result.unresolved.length === 0 ? <p className="quiet-success">No unresolved obligations.</p> :
          <ul className="finding-list">{result.unresolved.map(item => <li key={item.obligation}>
            <strong>{item.obligation}: {humanize(item.verdict)}</strong>
            <span>{item.howToResolve}</span>
          </li>)}</ul>}
        {result.notObservable.map(item => <p className="notice" key={item.obligation}>
          <strong>{item.obligation}</strong> is not externally observable. {item.reason}
        </p>)}
      </section>
    </section>

    {(result.advisories.length > 0 || result.suiteIncidents.length > 0) && <section className="panel report-section">
      <h2>Advisories and Suite incidents</h2>
      <ul className="finding-list">
        {result.advisories.map(item => <li key={`${item.code}-${item.obligation}`}><strong>{item.code}</strong><span>{item.messageEn} It does not affect the verdict.</span></li>)}
        {result.suiteIncidents.map((item, index) => <li key={`${item.kind}-${index}`}><strong>{item.kind}</strong><span>{item.note}</span></li>)}
      </ul>
    </section>}

    <section className="report-section">
      <div className="section-heading"><div><p className="eyebrow">Traceable detail</p><h2>Requirements</h2></div><span>{result.summary.requirements.total} requirements</span></div>
      <div className="requirements">{result.requirements.map(requirement => <details key={requirement.id} className="requirement">
        <summary><span>{requirement.id}</span><strong className={`badge badge-${requirement.verdict.toLowerCase()}`}>{humanize(requirement.verdict)}</strong></summary>
        <div className="requirement-body">
          <a href={requirement.specUrl}>Source requirement</a>
          <ul>{requirement.obligations.map(item => <li key={item.key}><code>{item.key}</code><span>{item.level} · {humanize(item.verdict)}</span></li>)}</ul>
        </div>
      </details>)}</div>
    </section>

    <footer>This is a test result, not a certification. Target product details are declarations and are not independently verified.</footer>
  </main>
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div><span>{label}</span><strong>{value}</strong></div>
}

function humanize(value: string) {
  return value.toLowerCase().replaceAll('_', ' ').replace(/^./, first => first.toUpperCase())
}

function shortDigest(value: string) {
  return `${value.slice(0, 19)}…${value.slice(-8)}`
}
