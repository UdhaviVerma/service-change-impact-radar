import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'

type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'

type ServiceNode = {
  id: string
  name: string
  description: string
}

type DependencyEdge = {
  id: string
  sourceService: string
  targetService: string
  type: 'REST' | 'KAFKA'
  operation: string | null
  topic: string | null
  sourceReference: string
  notes: string
}

type DemoWorkspace = {
  id: string
  name: string
  description: string
  services: ServiceNode[]
  dependencies: DependencyEdge[]
  sampleProvider: string
  sampleChangeDescription: string
}

type ImpactFinding = {
  severity: Severity
  title: string
  explanation: string
  changedElement: string
  potentiallyAffectedService: string
  evidence: string[]
  recommendedVerification: string
  breaking: boolean
  confidence: string
}

type ImpactReport = {
  workspaceId: string
  providerService: string
  summary: string
  overallRisk: string
  findingsBySeverity: Record<Severity, number>
  affectedServices: string[]
  findings: ImpactFinding[]
  rolloutChecklist: string[]
  generatedAt: string
}

type AnalysisResponse = {
  report: ImpactReport
  markdown: string
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

const SEVERITY_ORDER: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

const severityLabel = (severity: Severity) => severity.charAt(0) + severity.slice(1).toLowerCase()

function App() {
  const [workspace, setWorkspace] = useState<DemoWorkspace | null>(null)
  const [selectedService, setSelectedService] = useState<string | null>(null)
  const [selectedEdge, setSelectedEdge] = useState<string | null>(null)
  const [beforeFile, setBeforeFile] = useState<File | null>(null)
  const [afterFile, setAfterFile] = useState<File | null>(null)
  const [providerService, setProviderService] = useState('customer-service')
  const [analysis, setAnalysis] = useState<AnalysisResponse | null>(null)
  const [isLoadingWorkspace, setIsLoadingWorkspace] = useState(false)
  const [isAnalyzing, setIsAnalyzing] = useState(false)
  const [statusMessage, setStatusMessage] = useState<string | null>('Load sample project to start.')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const servicePositions = useMemo(() => {
    const base: Record<string, { x: number; y: number }> = {
      'customer-service': { x: 12, y: 20 },
      'order-service': { x: 38, y: 18 },
      'payment-service': { x: 64, y: 20 },
      'notification-service': { x: 26, y: 62 },
      'analytics-service': { x: 62, y: 62 },
    }
    return base
  }, [])

  const selectedServiceDetails = workspace?.services.find((service) => service.id === selectedService)
  const selectedEdgeDetails = workspace?.dependencies.find((edge) => edge.id === selectedEdge)

  async function loadSampleProject() {
    setIsLoadingWorkspace(true)
    setErrorMessage(null)
    try {
      const response = await fetch(`${API_BASE}/api/workspaces/demo`)
      if (!response.ok) {
        throw new Error('Could not load demo workspace.')
      }
      const data = (await response.json()) as DemoWorkspace
      setWorkspace(data)
      setProviderService(data.sampleProvider)
      setSelectedService(data.sampleProvider)
      setSelectedEdge(null)
      setStatusMessage('Demo workspace loaded. Run sample analysis or upload your own OpenAPI files.')
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load demo workspace.')
    } finally {
      setIsLoadingWorkspace(false)
    }
  }

  async function runSampleAnalysis() {
    await runAnalysis({ useDemo: true })
  }

  async function runUploadAnalysis(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!beforeFile || !afterFile) {
      setErrorMessage('Please select both before and after OpenAPI files.')
      return
    }
    await runAnalysis({ useDemo: false })
  }

  async function runAnalysis(options: { useDemo: boolean }) {
    setIsAnalyzing(true)
    setErrorMessage(null)
    setStatusMessage(null)

    try {
      const formData = new FormData()
      formData.append('providerService', providerService.trim())
      formData.append('useDemo', String(options.useDemo))

      if (!options.useDemo && beforeFile && afterFile) {
        formData.append('beforeFile', beforeFile)
        formData.append('afterFile', afterFile)
      }

      const response = await fetch(`${API_BASE}/api/analysis/openapi-diff`, {
        method: 'POST',
        body: formData,
      })

      if (!response.ok) {
        const apiError = await response.json().catch(() => ({}))
        throw new Error(apiError.message || 'Analysis failed.')
      }

      const data = (await response.json()) as AnalysisResponse
      setAnalysis(data)
      setStatusMessage('Analysis completed successfully.')
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Analysis failed.')
    } finally {
      setIsAnalyzing(false)
    }
  }

  async function copyMarkdown() {
    if (!analysis?.markdown) {
      return
    }
    await navigator.clipboard.writeText(analysis.markdown)
    setStatusMessage('Markdown report copied to clipboard.')
  }

  function downloadMarkdown() {
    if (!analysis?.markdown) {
      return
    }
    const blob = new Blob([analysis.markdown], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'impact-report.md'
    anchor.click()
    URL.revokeObjectURL(url)
  }

  return (
    <main className="layout">
      <header className="hero">
        <p className="badge">Portfolio Project · Demo Data</p>
        <h1>Service Change Impact Radar</h1>
        <p className="tagline">See what a code or API change could break across your microservices.</p>
      </header>

      <section className="panel controls" aria-label="Project controls">
        <div className="actions-row">
          <button type="button" onClick={loadSampleProject} disabled={isLoadingWorkspace}>
            {isLoadingWorkspace ? 'Loading…' : 'Load sample project'}
          </button>
          <button type="button" onClick={runSampleAnalysis} disabled={isAnalyzing || !workspace}>
            Run sample analysis
          </button>
          <label>
            Provider service
            <input
              value={providerService}
              onChange={(event) => setProviderService(event.target.value)}
              placeholder="customer-service"
              aria-label="Provider service"
            />
          </label>
        </div>
        <form className="upload-form" onSubmit={runUploadAnalysis}>
          <label>
            Before OpenAPI (.json/.yaml/.yml)
            <input
              type="file"
              accept=".json,.yaml,.yml"
              onChange={(event) => setBeforeFile(event.target.files?.[0] || null)}
            />
          </label>
          <label>
            After OpenAPI (.json/.yaml/.yml)
            <input
              type="file"
              accept=".json,.yaml,.yml"
              onChange={(event) => setAfterFile(event.target.files?.[0] || null)}
            />
          </label>
          <button type="submit" disabled={isAnalyzing}>
            {isAnalyzing ? 'Analyzing…' : 'Analyze upload'}
          </button>
        </form>
        {statusMessage && <p className="status success">{statusMessage}</p>}
        {errorMessage && <p className="status error">{errorMessage}</p>}
      </section>

      <section className="grid-two">
        <article className="panel">
          <h2>Dependency Map</h2>
          <p className="muted">REST edges and Kafka topics from bundled demo workspace.</p>
          {!workspace ? (
            <p className="empty">Load sample project to visualize dependencies.</p>
          ) : (
            <div className="map-shell" role="img" aria-label="Service dependency map">
              {workspace.dependencies.map((edge) => {
                const source = servicePositions[edge.sourceService]
                const target = servicePositions[edge.targetService]
                if (!source || !target) return null
                const midX = (source.x + target.x) / 2
                const midY = (source.y + target.y) / 2
                return (
                  <button
                    key={edge.id}
                    type="button"
                    className={`edge edge-${edge.type.toLowerCase()} ${selectedEdge === edge.id ? 'edge-selected' : ''}`}
                    style={{
                      left: `${midX}%`,
                      top: `${midY}%`,
                      width: `${Math.hypot(target.x - source.x, target.y - source.y) * 1.1}%`,
                      transform: `translate(-50%, -50%) rotate(${Math.atan2(target.y - source.y, target.x - source.x)}rad)`,
                    }}
                    onClick={() => {
                      setSelectedEdge(edge.id)
                      setSelectedService(null)
                    }}
                    aria-label={`${edge.type} dependency from ${edge.sourceService} to ${edge.targetService}`}
                  >
                    <span>{edge.type === 'REST' ? 'REST' : 'Kafka'}</span>
                  </button>
                )
              })}

              {workspace.services.map((service) => (
                <button
                  key={service.id}
                  type="button"
                  className={`node ${selectedService === service.id ? 'node-selected' : ''}`}
                  style={{ left: `${servicePositions[service.id]?.x || 0}%`, top: `${servicePositions[service.id]?.y || 0}%` }}
                  onClick={() => {
                    setSelectedService(service.id)
                    setSelectedEdge(null)
                  }}
                >
                  {service.name}
                </button>
              ))}
            </div>
          )}
          <p className="legend"><strong>Legend:</strong> REST = solid line, Kafka = dashed line.</p>
        </article>

        <article className="panel detail-panel">
          <h2>Selection details</h2>
          {selectedServiceDetails && (
            <div>
              <p><strong>Service:</strong> {selectedServiceDetails.name}</p>
              <p>{selectedServiceDetails.description}</p>
            </div>
          )}
          {selectedEdgeDetails && (
            <div>
              <p><strong>Type:</strong> {selectedEdgeDetails.type}</p>
              <p><strong>Source:</strong> {selectedEdgeDetails.sourceService}</p>
              <p><strong>Target:</strong> {selectedEdgeDetails.targetService}</p>
              {selectedEdgeDetails.operation && <p><strong>Operation:</strong> {selectedEdgeDetails.operation}</p>}
              {selectedEdgeDetails.topic && <p><strong>Topic:</strong> {selectedEdgeDetails.topic}</p>}
              <p><strong>Reference:</strong> {selectedEdgeDetails.sourceReference}</p>
              <p><strong>Notes:</strong> {selectedEdgeDetails.notes}</p>
            </div>
          )}
          {!selectedServiceDetails && !selectedEdgeDetails && <p className="empty">Select a node or edge in the map.</p>}
        </article>
      </section>

      <section className="panel report-panel">
        <h2>Impact Report</h2>
        {!analysis ? (
          <p className="empty">Run analysis to view deterministic findings and Markdown export.</p>
        ) : (
          <>
            <div className="summary-grid">
              <div>
                <p className="muted">Overall risk</p>
                <p className="risk">{analysis.report.overallRisk}</p>
              </div>
              <div>
                <p className="muted">Affected services</p>
                <p>{analysis.report.affectedServices.length}</p>
              </div>
              <div>
                <p className="muted">Findings</p>
                <p>{analysis.report.findings.length}</p>
              </div>
            </div>

            <div className="severity-row">
              {SEVERITY_ORDER.map((severity) => (
                <div key={severity} className={`severity-badge severity-${severity.toLowerCase()}`}>
                  <span>{severityLabel(severity)}</span>
                  <strong>{analysis.report.findingsBySeverity?.[severity] || 0}</strong>
                </div>
              ))}
            </div>

            <ul className="findings-list">
              {analysis.report.findings.map((finding, index) => (
                <li key={`${finding.changedElement}-${index}`} className="finding-card">
                  <p className="finding-title">
                    <span className={`pill severity-${finding.severity.toLowerCase()}`}>{severityLabel(finding.severity)}</span>
                    {finding.title}
                  </p>
                  <p>{finding.explanation}</p>
                  <p><strong>Changed:</strong> {finding.changedElement}</p>
                  <p><strong>Affected:</strong> {finding.potentiallyAffectedService}</p>
                  <p><strong>Evidence:</strong> {finding.evidence.join(' · ')}</p>
                  <p><strong>Recommended check:</strong> {finding.recommendedVerification}</p>
                  <p><strong>Breaking confidence:</strong> {finding.breaking ? 'Rule-backed breaking change' : 'Potential impact, verify usage'} ({finding.confidence})</p>
                </li>
              ))}
            </ul>

            <div className="report-actions">
              <button type="button" onClick={copyMarkdown}>Copy Markdown</button>
              <button type="button" onClick={downloadMarkdown}>Download .md</button>
            </div>

            <textarea value={analysis.markdown} readOnly aria-label="Markdown report" rows={16} />
          </>
        )}
      </section>
    </main>
  )
}

export default App
