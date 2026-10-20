import { useState, useEffect } from 'react'
import { getMe, SessionExpiredError } from './api.js'
import SignIn from './pages/SignIn.jsx'
import Upload from './pages/Upload.jsx'
import Review from './pages/Review.jsx'
import CheckReview from './pages/CheckReview.jsx'
import Confirmation from './pages/Confirmation.jsx'

export default function App() {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  // Upload → review flow state
  const [files, setFiles] = useState([])          // File[] selected by user
  const [currentIndex, setCurrentIndex] = useState(0)
  const [results, setResults] = useState([])       // confirmed rows
  const [mode, setMode] = useState('receipts')     // 'receipts' | 'checks'

  // page: 'signin' | 'upload' | 'review' | 'check-review' | 'done'
  const [page, setPage] = useState('signin')

  const handleSessionExpired = () => {
    window.location.href = '/oauth2/authorization/google'
  }

  useEffect(() => {
    getMe()
      .then(u => {
        if (u.signedIn) {
          setUser(u)
          setPage('upload')
        } else {
          setPage('signin')
        }
      })
      .catch(() => setPage('signin'))
      .finally(() => setLoading(false))
  }, [])

  function handleFilesSelected(selected, selectedMode) {
    setFiles(selected)
    setCurrentIndex(0)
    setResults([])
    setMode(selectedMode)
    setPage(selectedMode === 'checks' ? 'check-review' : 'review')
  }

  function handleConfirmed(result) {
    const next = [...results, result]
    setResults(next)
    if (currentIndex + 1 < files.length) {
      setCurrentIndex(i => i + 1)
    } else {
      setPage('done')
    }
  }

  function handleSkipped() {
    if (currentIndex + 1 < files.length) {
      setCurrentIndex(i => i + 1)
    } else {
      setPage('done')
    }
  }

  function handleDone() {
    setFiles([])
    setCurrentIndex(0)
    setResults([])
    setPage('upload')
  }

  if (loading) {
    return <div className="loading">Loading…</div>
  }

  if (page === 'signin') {
    return <SignIn />
  }

  if (page === 'upload') {
    return <Upload user={user} onFilesSelected={handleFilesSelected} />
  }

  if (page === 'review') {
    return (
      <Review
        file={files[currentIndex]}
        index={currentIndex}
        total={files.length}
        onConfirmed={handleConfirmed}
        onSkipped={handleSkipped}
        onSessionExpired={handleSessionExpired}
      />
    )
  }

  if (page === 'check-review') {
    return (
      <CheckReview
        file={files[currentIndex]}
        index={currentIndex}
        total={files.length}
        onConfirmed={handleConfirmed}
        onSkipped={handleSkipped}
        onSessionExpired={handleSessionExpired}
      />
    )
  }

  if (page === 'done') {
    return <Confirmation results={results} user={user} mode={mode} onDone={handleDone} />
  }

  return null
}
