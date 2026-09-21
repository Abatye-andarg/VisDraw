import { useEffect, useState } from 'react'

type Connection = 'checking' | 'connected' | 'unavailable'

const messages: Record<Connection, string> = {
  checking: 'Checking connection…',
  connected: 'Connected',
  unavailable: 'Unable to connect',
}

export default function App() {
  const [connection, setConnection] = useState<Connection>('checking')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    const timeout = window.setTimeout(() => controller.abort(), 5000)

    async function checkConnection() {
      try {
        const response = await fetch('/actuator/health', {
          signal: controller.signal,
          cache: 'no-store',
        })
        if (!response.ok) throw new Error('Health request failed')
        const health: unknown = await response.json()
        if (typeof health !== 'object' || health === null ||
            !('status' in health) || health.status !== 'UP') {
          throw new Error('Backend is not healthy')
        }
        if (active) setConnection('connected')
      } catch {
        if (active) setConnection('unavailable')
      } finally {
        window.clearTimeout(timeout)
      }
    }

    void checkConnection()
    return () => {
      active = false
      window.clearTimeout(timeout)
      controller.abort()
    }
  }, [attempt])

  return (
    <main>
      <p className="eyebrow">WRITE · DRAW · STRUCTURE</p>
      <h1>Visual Notes</h1>
      <p className="intro">A place for written and visual thinking.</p>
      <section aria-labelledby="connection-title">
        <h2 id="connection-title">Workspace foundation</h2>
        <p>This first step connects the interface to the Visual Notes backend.</p>
        <p role="status" className={`status ${connection}`}>
          <span aria-hidden="true" className="dot" />
          {messages[connection]}
        </p>
        {connection === 'unavailable' && (
          <p>The service could not be reached. Please try again.</p>
        )}
        <button
          disabled={connection === 'checking'}
          onClick={() => {
            setConnection('checking')
            setAttempt((value) => value + 1)
          }}
        >
          {connection === 'checking' ? 'Checking…' : 'Check again'}
        </button>
      </section>
    </main>
  )
}

