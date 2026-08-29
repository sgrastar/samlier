export type Profile = 'IDP_CORE' | 'IDP_FULL' | 'SP_CORE' | 'SP_FULL'

export interface Plan {
  plan: {
    id: string
    name: string
    profile: Profile
    target: { kind: 'IDP' | 'SP' | 'TOKEN_TRANSLATION_PROXY'; entityId: string }
  }
  entityId: string
  metadataUrl: string
  mdqUrl: string
}

export interface Run {
  id: string
  planId: string
  status: string
  targetToSuiteReachability: string
  context: Record<string, unknown>
}

export interface PublicResult {
  schemaVersion: '1'
  run: {
    id: string
    startedAt: string
    finishedAt: string
    conformance: string
    completeness: string
    scopeQualifications: Array<{
      kind: string
      predicate: string
      excludedObligations: string[]
      reason: string
      attestedBy: string
      attestedAt: string
      verified: false
    }>
  }
  suite: { name: string; version: string; imageDigest: string; executionMode: string }
  evaluationBundle: { digest: string }
  profile: { id: string; spec: { document: string; version: string; date: string }; levelDefinitionNote: string }
  target: {
    declaredProduct: string
    declaredBy: string
    verified: false
    entityId: string
    metadataDigest: string
    role: string
    kind: string
  }
  advisories: Array<{ code: string; obligation: string; severity: string; messageEn: string; affectsVerdict: false }>
  suiteIncidents: Array<{ kind: string; caseId?: string; actionId?: string; note: string }>
  summary: {
    requirements: ResultCount
    obligations: ResultCount
    cases: ResultCount
  }
  coverage: {
    obligationsTotal: number
    obligationsApplicable: number
    mustApplicable: number
    mustObservable: number
    mustResolved: number
    mustUnresolved: number
    mustNotObservable: number
    verifiedRatio: number
  }
  requirements: Array<{
    id: string
    verdict: string
    specUrl: string
    obligations: Array<{ key: string; level: string; role: string; verdict: string }>
    cases: Array<{ id: string; obligation: string; outcome: string | null; verdict: string; mode: string; reason: string }>
  }>
  unresolved: Array<{ obligation: string; level: string; verdict: string; reasons: string[]; howToResolve: string }>
  notObservable: Array<{ obligation: string; level: string; reason: string }>
  conformanceStatement: string
}

interface ResultCount {
  total: number
  verdicts: Record<string, number>
}

export interface PlanInput {
  name: string
  profile: Profile
  targetKind: 'IDP' | 'SP' | 'TOKEN_TRANSLATION_PROXY'
  targetEntityId: string
  metadataSourceKind: 'URL'
  metadataSourceLocation: string
  suiteMetadataDelivery: 'MANUAL' | 'HTTP_URL' | 'MDQ'
  declaredFeatures: Record<string, boolean>
  parameters: { clockSkewToleranceSeconds: number; metadataRefreshWaitSeconds: number; testUserHint: string }
  interaction: { allowBrowserSteps: boolean; allowAttestation: boolean }
}

export interface ManagementSession {
  runId: string
  csrfToken: string
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: { 'content-type': 'application/json', ...(init?.headers ?? {}) },
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: response.statusText }))
    throw new Error(body.message ?? `HTTP ${response.status}`)
  }
  return response.status === 204 ? (undefined as T) : response.json()
}

function camelize(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(camelize)
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>).map(([key, child]) => [
      key.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase()),
      camelize(child),
    ]))
  }
  return value
}

export const api = {
  plans: () => request<Plan[]>('/api/plans'),
  createPlan: (input: PlanInput) => request<Plan>('/api/plans', { method: 'POST', body: JSON.stringify(input) }),
  deletePlan: (id: string) => request<void>(`/api/plans/${id}`, { method: 'DELETE' }),
  runs: (planId: string) => request<Run[]>(`/api/plans/${planId}/runs`),
  createRun: (planId: string) => request<Run>(`/api/plans/${planId}/runs`, { method: 'POST' }),
  preflight: (runId: string) => request<Record<string, unknown>>(`/api/runs/${runId}/preflight`, { method: 'POST' }),
  transcript: (runId: string) => request<unknown[]>(`/api/runs/${runId}/transcript`),
  result: async (runId: string) => camelize(
    await request<unknown>(`/api/runs/${runId}/result.json`),
  ) as PublicResult,
  managementSession: (runId: string, token: string) => request<ManagementSession>(
    '/api/manage/session', { method: 'POST', body: JSON.stringify({ runId, token }) },
  ),
}
