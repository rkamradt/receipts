export default function Confirmation({ results, user, mode, onDone }) {
  const isChecks = mode === 'checks'
  const noun = isChecks ? 'check image' : 'receipt'
  const nounPlural = isChecks ? 'check images' : 'receipts'

  return (
    <div className="page">
      <header className="top-bar">
        <span className="app-name">{isChecks ? '💵' : '🧾'} Receipts</span>
        <span className="user-info">
          {user?.picture && <img src={user.picture} alt="" className="avatar" />}
          {user?.name}
          <a href="/logout" className="sign-out-link">Sign out</a>
        </span>
      </header>

      <main className="confirm-main">
        <div className="confirm-icon">✅</div>
        <h2>Done!</h2>

        {results.length > 0 ? (
          <>
            <p>{results.length} {results.length > 1 ? nounPlural : noun} added to the sheet.</p>
            <ul className="result-list">
              {results.map((r, i) => (
                <li key={i}>
                  <a href={r.receiptUrl} target="_blank" rel="noopener noreferrer">
                    View {noun} {i + 1} in Drive
                  </a>
                </li>
              ))}
            </ul>
          </>
        ) : (
          <p>No {nounPlural} were added (all were skipped).</p>
        )}

        <button className="btn btn-primary btn-large" onClick={onDone}>
          Upload more
        </button>
      </main>
    </div>
  )
}
