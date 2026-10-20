import { useRef, useState } from 'react'

const ACCEPTED = 'image/jpeg,image/png,image/webp,image/heic,.jpg,.jpeg,.png,.webp,.heic'

export default function Upload({ user, onFilesSelected }) {
  const [mode, setMode] = useState('receipts')
  const inputRef = useRef(null)

  function handleChange(e) {
    const selected = Array.from(e.target.files || [])
    if (selected.length > 0) onFilesSelected(selected, mode)
  }

  function handleDrop(e) {
    e.preventDefault()
    const files = Array.from(e.dataTransfer.files).filter(f => f.type.startsWith('image/'))
    if (files.length > 0) onFilesSelected(files, mode)
  }

  function handleDragOver(e) { e.preventDefault() }

  return (
    <div className="page">
      <header className="top-bar">
        <span className="app-name">🧾 Receipts</span>
        <span className="user-info">
          {user?.picture && <img src={user.picture} alt="" className="avatar" />}
          {user?.name}
          <a href="/logout" className="sign-out-link">Sign out</a>
        </span>
      </header>

      <main className="upload-main">
        <div className="mode-toggle">
          <button
            className={`btn ${mode === 'receipts' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setMode('receipts')}
          >
            🧾 Receipts
          </button>
          <button
            className={`btn ${mode === 'checks' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setMode('checks')}
          >
            💵 Checks
          </button>
        </div>

        {mode === 'receipts' ? (
          <>
            <h2>Upload Receipts</h2>
            <p className="hint">Select photos or take pictures of receipts to add to the sheet.</p>
          </>
        ) : (
          <>
            <h2>Upload Checks</h2>
            <p className="hint">Select photos of checks. Multiple checks per image are supported.</p>
          </>
        )}

        <div
          className="drop-zone"
          onClick={() => inputRef.current?.click()}
          onDrop={handleDrop}
          onDragOver={handleDragOver}
          role="button"
          tabIndex={0}
          onKeyDown={e => e.key === 'Enter' && inputRef.current?.click()}
          aria-label={`Select ${mode} images`}
        >
          <div className="drop-icon">{mode === 'receipts' ? '📷' : '📸'}</div>
          <div className="drop-label">Tap to select images</div>
          <div className="drop-sub">JPEG · PNG · WebP · HEIC</div>
          <input
            ref={inputRef}
            type="file"
            accept={ACCEPTED}
            multiple
            capture="environment"
            onChange={handleChange}
            style={{ display: 'none' }}
          />
        </div>
      </main>
    </div>
  )
}
