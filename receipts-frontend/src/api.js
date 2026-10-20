/**
 * Thin wrappers around fetch. All calls go to the same origin (Spring Boot
 * serves both the static frontend and the /api endpoints).
 *
 * CSRF: Spring Security sets an XSRF-TOKEN cookie. We read it and send it
 * back as X-XSRF-TOKEN on every mutating request.
 */

function getCsrf() {
  const match = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='))
  return match ? decodeURIComponent(match.split('=')[1]) : ''
}

export async function getMe() {
  const res = await fetch('/api/me', { credentials: 'same-origin' })
  if (!res.ok) throw new Error('Failed to fetch user info')
  return res.json()
}

/**
 * Send a receipt image for extraction.
 * @param {File} file
 * @returns {Promise<{date,items,total,confidenceNotes}>}
 */
export async function extractReceipt(file) {
  const form = new FormData()
  form.append('file', file)

  const res = await fetch('/api/extract', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'X-XSRF-TOKEN': getCsrf() },
    body: form,
  })

  if (res.status === 401) throw new SessionExpiredError()
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || 'Extraction failed')
  }
  return res.json()
}

/**
 * Upload the original file to Drive and append a row to the sheet.
 * @param {File} originalFile
 * @param {{date,items,total,confidenceNotes}} data  reviewed receipt data
 * @returns {Promise<{receiptUrl: string}>}
 */
export async function confirmReceipt(originalFile, data) {
  const form = new FormData()
  form.append('file', originalFile)
  form.append('data', JSON.stringify(data))

  const res = await fetch('/api/confirm', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'X-XSRF-TOKEN': getCsrf() },
    body: form,
  })

  if (res.status === 401) throw new SessionExpiredError()
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || 'Could not save to sheet')
  }
  return res.json()
}

/**
 * Send a check image for extraction. May return multiple checks.
 * @param {File} file
 * @returns {Promise<{checks: Array, confidenceNotes: string}>}
 */
export async function extractChecks(file) {
  const form = new FormData()
  form.append('file', file)

  const res = await fetch('/api/check/extract', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'X-XSRF-TOKEN': getCsrf() },
    body: form,
  })

  if (res.status === 401) throw new SessionExpiredError()
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || 'Extraction failed')
  }
  return res.json()
}

/**
 * Upload the check image to Drive and append one row per check to the sheet.
 * @param {File} originalFile
 * @param {Array<{date,checkNumber,payee,amount,memo,confidenceNotes}>} checks  reviewed checks
 * @returns {Promise<{receiptUrl: string}>}
 */
export async function confirmChecks(originalFile, checks) {
  const form = new FormData()
  form.append('file', originalFile)
  form.append('data', JSON.stringify(checks))

  const res = await fetch('/api/check/confirm', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'X-XSRF-TOKEN': getCsrf() },
    body: form,
  })

  if (res.status === 401) throw new SessionExpiredError()
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || 'Could not save to sheet')
  }
  return res.json()
}

export class SessionExpiredError extends Error {
  constructor() { super('Session expired') }
}
