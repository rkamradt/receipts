import { useState, useEffect } from 'react'
import { extractChecks, confirmChecks, SessionExpiredError } from '../api.js'

export default function CheckReview({ file, index, total, onConfirmed, onSkipped, onSessionExpired }) {
  const [imgUrl, setImgUrl] = useState(null)
  const [zoomed, setZoomed] = useState(false)

  const [extracting, setExtracting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  // List of checks extracted from this image, each editable
  const [checks, setChecks] = useState([])
  const [overallNotes, setOverallNotes] = useState('')

  useEffect(() => {
    if (!file) return
    const url = URL.createObjectURL(file)
    setImgUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  useEffect(() => {
    if (!file) return
    runExtraction()
  }, [file])

  async function runExtraction() {
    setExtracting(true)
    setError(null)
    try {
      const result = await extractChecks(file)
      setChecks(result.checks || [])
      setOverallNotes(result.confidenceNotes || '')
    } catch (e) {
      if (e instanceof SessionExpiredError) { onSessionExpired(); return }
      setError(e.message || 'Extraction failed')
    } finally {
      setExtracting(false)
    }
  }

  async function handleConfirm() {
    if (checks.length === 0) return
    setSaving(true)
    setError(null)
    try {
      const data = checks.map(c => ({
        date:            c.date            || null,
        checkNumber:     c.checkNumber     || null,
        payee:           c.payee           || null,
        amount:          c.amount !== '' && c.amount != null ? parseFloat(c.amount) : null,
        memo:            c.memo            || null,
        confidenceNotes: c.confidenceNotes || '',
      }))
      const result = await confirmChecks(file, data)
      onConfirmed(result)
    } catch (e) {
      if (e instanceof SessionExpiredError) { onSessionExpired(); return }
      setError(e.message || 'Failed to save')
      setSaving(false)
    }
  }

  function updateCheck(idx, field, value) {
    setChecks(prev => prev.map((c, i) => i === idx ? { ...c, [field]: value } : c))
  }

  function addCheck() {
    setChecks(prev => [...prev, {
      date: '', checkNumber: '', payee: '', amount: '', memo: '', confidenceNotes: ''
    }])
  }

  function removeCheck(idx) {
    setChecks(prev => prev.filter((_, i) => i !== idx))
  }

  return (
    <div className="page">
      <header className="top-bar">
        <span className="app-name">💵 Checks</span>
        <span className="progress-label">Image {index + 1} of {total}</span>
      </header>

      <main className="review-main">
        {/* Check image */}
        <div className="image-container" onClick={() => setZoomed(z => !z)}>
          {imgUrl && (
            <img
              src={imgUrl}
              alt="Check"
              className={zoomed ? 'receipt-img zoomed' : 'receipt-img'}
            />
          )}
          {!zoomed && <div className="zoom-hint">Tap to zoom</div>}
        </div>

        {extracting && (
          <div className="status-banner loading-banner">
            Reading checks…
          </div>
        )}
        {error && (
          <div className="status-banner error-banner">
            {error}
          </div>
        )}
        {overallNotes && !extracting && (
          <div className="status-banner warn-banner">
            {overallNotes}
          </div>
        )}
        {!extracting && checks.length === 0 && !error && (
          <div className="status-banner warn-banner">
            No checks detected — try Re-read or add one manually.
          </div>
        )}

        {/* One card per check */}
        {checks.map((check, idx) => (
          <div key={idx} className="check-card">
            <div className="check-card-header">
              <span className="check-card-title">Check {idx + 1}</span>
              <button
                className="btn btn-icon"
                onClick={() => removeCheck(idx)}
                disabled={extracting || saving}
                aria-label="Remove check"
              >
                ✕
              </button>
            </div>

            {check.confidenceNotes && (
              <div className="status-banner warn-banner check-note">
                {check.confidenceNotes}
              </div>
            )}

            <div className="form-section">
              <label className="field-label">Date</label>
              <input
                type="date"
                className="field-input"
                value={check.date || ''}
                onChange={e => updateCheck(idx, 'date', e.target.value)}
                disabled={extracting}
              />
            </div>

            <div className="form-section">
              <label className="field-label">Check #</label>
              <input
                type="text"
                className="field-input"
                placeholder="Check number"
                value={check.checkNumber || ''}
                onChange={e => updateCheck(idx, 'checkNumber', e.target.value)}
                disabled={extracting}
              />
            </div>

            <div className="form-section">
              <label className="field-label">Payee</label>
              <input
                type="text"
                className="field-input"
                placeholder="Pay to the order of"
                value={check.payee || ''}
                onChange={e => updateCheck(idx, 'payee', e.target.value)}
                disabled={extracting}
              />
            </div>

            <div className="form-section">
              <label className="field-label">Amount</label>
              <input
                type="number"
                className="field-input"
                placeholder="0.00"
                step="0.01"
                value={check.amount ?? ''}
                onChange={e => updateCheck(idx, 'amount', e.target.value)}
                disabled={extracting}
              />
            </div>

            <div className="form-section">
              <label className="field-label">Memo</label>
              <input
                type="text"
                className="field-input"
                placeholder="Memo"
                value={check.memo || ''}
                onChange={e => updateCheck(idx, 'memo', e.target.value)}
                disabled={extracting}
              />
            </div>
          </div>
        ))}

        <button className="btn btn-secondary" onClick={addCheck} disabled={extracting || saving}>
          + Add check
        </button>

        {/* Action buttons */}
        <div className="action-row">
          <button
            className="btn btn-primary btn-large"
            onClick={handleConfirm}
            disabled={extracting || saving || checks.length === 0}
          >
            {saving ? 'Saving…' : `Add ${checks.length} check${checks.length !== 1 ? 's' : ''} to sheet`}
          </button>
          <button
            className="btn btn-secondary"
            onClick={runExtraction}
            disabled={extracting || saving}
          >
            Re-read
          </button>
          <button
            className="btn btn-ghost"
            onClick={onSkipped}
            disabled={saving}
          >
            Skip
          </button>
        </div>
      </main>
    </div>
  )
}
