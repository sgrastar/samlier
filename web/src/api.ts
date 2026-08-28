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

export const api = {
  plans: () => request<Plan[]>('/api/plans'),
  createPlan: (input: PlanInput) => request<Plan>('/api/plans', { method: 'POST', body: JSON.stringify(input) }),
  deletePlan: (id: string) => request<void>(`/api/plans/${id}`, { method: 'DELETE' }),
  runs: (planId: string) => request<Run[]>(`/api/plans/${planId}/runs`),
  createRun: (planId: string) => request<Run>(`/api/plans/${planId}/runs`, { method: 'POST' }),
  preflight: (runId: string) => request<Record<string, unknown>>(`/api/runs/${runId}/preflight`, { method: 'POST' }),
  transcript: (runId: string) => request<unknown[]>(`/api/runs/${runId}/transcript`),
}
