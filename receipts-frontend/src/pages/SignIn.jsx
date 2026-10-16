export default function SignIn() {
  return (
    <div className="center-page">
      <div className="card signin-card">
        <div className="app-icon">🧾</div>
        <h1>Receipts</h1>
        <p className="subtitle">Sign in with your kamradtfamily.net account to get started.</p>
        <a href="/oauth2/authorization/google" className="btn btn-primary btn-large">
          Sign in with Google
        </a>
      </div>
    </div>
  )
}
