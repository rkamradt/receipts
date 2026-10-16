import { useState, useEffect, useRef } from 'react'
import { extractReceipt, confirmReceipt, SessionExpiredError } from '../api.js'

export default function Review({ file, index, total, onConfirmed, onSkipped, onSessionExpired }) {
  const [imgUrl, setImgUrl] = useState(null)
  const [zoomed, setZoomed] = useState(false)

  const [extracting, setExtracting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  // Editable receipt fields
  const [date, setDate] = useState('')
  const [items, setItems] = useState([])   // [{description, amount}]
  const [receiptTotal, setReceiptTotal] = useState('')

  // Whether the model returned any confidence warnings
  const [notes, setNotes] = useState('')

  // Build an object URL for the original file
  useEffect(() => {
    if (!file) return
    const url = URL.createObjectURL(file)
    setImgUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  // Kick off extraction whenever the file changes
  useEffect(() => {
    if (!file) return
    runExtraction()
  }, [file])

  async function runExtraction() {
    setExtracting(true)
    setError(null)
    try {
      const result = await extractReceipt(file)
      setDate(result.date || '')
      setItems(result.items || [])
      setReceiptTotal(result.total != null ? String(result.total) : '')
      setNotes(result.confidenceNotes || '')
    } catch (e) {
      if (e instanceof SessionExpiredError) { onSessionExpired(); return }
      setError(e.message || 'Extraction failed')
    } finally {
      setExtracting(false)
    }
  }

  async function handleConfirm() {
    setSaving(true)
    setError(null)
    try {
      const data = {
        date: date || null,
        items,
        total: receiptTotal !== '' ? parseFloat(receiptTotal) : null,
        confidenceNotes: notes,
      }
      const result = await confirmReceipt(file, data)
      onConfirmed(result)
    } catch (e) {
      if (e instanceof SessionExpiredError) { onSessionExpired(); return }
      setError(e.message || 'Failed to save')
      setSaving(false)
    }
  }

  // Check if items sum ≠ total (warn, not block)
  const itemsSum = items.reduce((acc, i) => acc + (parseFloat(i.amount) || 0), 0)
  const parsedTotal = parseFloat(receiptTotal)
  const sumMismatch = !isNaN(parsedTotal) && items.length > 0
    && Math.abs(itemsSum - parsedTotal) > 0.01

  function updateItem(idx, field, value) {
    setItems(prev => prev.map((item, i) =>
      i === idx ? { ...item, [field]: value } : item
    ))
  }

  function addItem() {
    setItems(prev => [...prev, { description: '', amount: '' }])
  }

  function removeItem(idx) {
    setItems(prev => prev.filter((_, i) => i !== idx))
  }

  return (
    <div className="page">
      <header className="top-bar">
        <span className="app-name">🧾 Receipts</span>
        <span className="progress-label">Receipt {index + 1} of {total}</span>
      </header>

      <main className="review-main">
        {/* Receipt image */}
        <div className="image-container" onClick={() => setZoomed(z => !z)}>
          {imgUrl && (
            <img
              src={imgUrl}
              alt="Receipt"
              className={zoomed ? 'receipt-img zoomed' : 'receipt-img'}
            />
          )}
          {!zoomed && <div className="zoom-hint">Tap to zoom</div>}
        </div>

        {/* Extraction status */}
        {extracting && (
          <div className="status-banner loading-banner">
            Reading receipt…
          </div>
        )}
        {error && (
          <div className="status-banner error-banner">
            {error}
          </div>
        )}
        {notes && !extracting && (
          <div className="status-banner warn-banner">
            {notes}
          </div>
        )}
        {sumMismatch && !extracting && (
          <div className="status-banner warn-banner">
            Items sum ({itemsSum.toFixed(2)}) does not match total ({parsedTotal.toFixed(2)}) — this is OK if tax is included in the total.
          </div>
        )}

        {/* Editable fields */}
        <div className="form-section">
          <label className="field-label">Date</label>
          <input
            type="date"
            className="field-input"
            value={date}
            onChange={e => setDate(e.target.value)}
            disabled={extracting}
          />
        </div>

        <div className="form-section">
          <label className="field-label">Items</label>
          {items.map((item, idx) => (
            <div key={idx} className="item-row">
              <input
                type="text"
                className="field-input item-desc"
                placeholder="Description"
                value={item.description}
                onChange={e => updateItem(idx, 'description', e.target.value)}
                disabled={extracting}
              />
              <input
                type="number"
                className="field-input item-amount"
                placeholder="0.00"
                step="0.01"
                value={item.amount ?? ''}
                onChange={e => updateItem(idx, 'amount', e.target.value)}
                disabled={extracting}
              />
              <button
                className="btn btn-icon"
                onClick={() => removeItem(idx)}
                disabled={extracting}
                aria-label="Remove item"
              >
                ✕
              </button>
            </div>
          ))}
          <button className="btn btn-secondary" onClick={addItem} disabled={extracting}>
            + Add item
          </button>
        </div>

        <div className="form-section">
          <label className="field-label">Total</label>
          <input
            type="number"
            className="field-input"
            placeholder="0.00"
            step="0.01"
            value={receiptTotal}
            onChange={e => setReceiptTotal(e.target.value)}
            disabled={extracting}
          />
        </div>

        {/* Action buttons */}
        <div className="action-row">
          <button
            className="btn btn-primary btn-large"
            onClick={handleConfirm}
            disabled={extracting || saving}
          >
            {saving ? 'Saving…' : 'Add to sheet'}
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
