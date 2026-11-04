import fs from 'node:fs/promises'
import path from 'node:path'
import http from 'node:http'
import https from 'node:https'
import { performance } from 'node:perf_hooks'

const BASE = process.env.BASE_URL || 'http://127.0.0.1:8084'
const USERNAME = process.env.TEST_USER || 'student'
const PASSWORD = process.env.TEST_PASSWORD || 'student123'
const CONCURRENCY = Number(process.env.CONCURRENCY || 100)
const RANGE_BYTES = Number(process.env.RANGE_BYTES || 1024 * 1024)
const TIMEOUT_MS = Number(process.env.TIMEOUT_MS || 60000)

async function jsonFetch(url, options = {}) {
  const response = await fetch(url, options)
  if (!response.ok) {
    throw new Error(`${url} -> HTTP ${response.status}`)
  }
  return response.json()
}

async function prepare() {
  const login = await jsonFetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USERNAME, password: PASSWORD })
  })
  const headers = { Authorization: `Bearer ${login.token}` }
  const series = await jsonFetch(`${BASE}/api/learning/series`, { headers })
  const detail = await jsonFetch(`${BASE}/api/learning/series/${series[0].id}`, { headers })
  const video = detail.chapters[0].videos[0]
  const play = await jsonFetch(`${BASE}/api/learning/videos/${video.id}/play`, { headers })
  return { video, playUrl: play.playUrl }
}

function requestRange(url, bytes) {
  return new Promise((resolve) => {
    const client = url.startsWith('https:') ? https : http
    const started = performance.now()
    let responseAt = null
    let firstByteAt = null
    let received = 0
    let status = 0
    let contentRange = null
    let error = null
    let settled = false

    const finish = () => {
      if (settled) return
      settled = true
      resolve({
        status,
        error,
        bytes: received,
        responseMs: responseAt == null ? null : Math.round(responseAt - started),
        firstByteMs: firstByteAt == null ? null : Math.round(firstByteAt - started),
        totalMs: Math.round(performance.now() - started),
        contentRange
      })
    }

    const req = client.get(url, {
      headers: { Range: `bytes=0-${Math.max(0, bytes - 1)}` },
      agent: false
    }, (res) => {
      status = res.statusCode
      contentRange = res.headers['content-range'] || null
      responseAt = performance.now()
      res.on('data', (chunk) => {
        if (firstByteAt == null) firstByteAt = performance.now()
        received += chunk.length
      })
      res.on('end', finish)
      res.on('error', (e) => { error = e.message; finish() })
    })
    req.setTimeout(TIMEOUT_MS, () => {
      error = 'timeout'
      req.destroy(new Error('timeout'))
    })
    req.on('error', (e) => {
      error = error || e.message
      finish()
    })
  })
}

function percentile(values, p) {
  if (!values.length) return null
  const sorted = [...values].sort((a, b) => a - b)
  const index = Math.min(sorted.length - 1, Math.ceil(p / 100 * sorted.length) - 1)
  return sorted[index]
}

function summarize(results) {
  const ok = results.filter((r) => r.status === 206 && r.bytes > 0)
  const statuses = {}
  for (const result of results) {
    const key = result.status || result.error || 'unknown'
    statuses[key] = (statuses[key] || 0) + 1
  }
  const responseTimes = ok.map((r) => r.responseMs).filter((v) => v != null)
  const firstByteTimes = ok.map((r) => r.firstByteMs).filter((v) => v != null)
  const totalTimes = ok.map((r) => r.totalMs).filter((v) => v != null)
  return {
    requests: results.length,
    ok: ok.length,
    failed: results.length - ok.length,
    statuses,
    bytes: results.reduce((sum, r) => sum + (r.bytes || 0), 0),
    responseMs: {
      p50: percentile(responseTimes, 50),
      p95: percentile(responseTimes, 95),
      max: responseTimes.length ? Math.max(...responseTimes) : null
    },
    firstByteMs: {
      p50: percentile(firstByteTimes, 50),
      p95: percentile(firstByteTimes, 95),
      max: firstByteTimes.length ? Math.max(...firstByteTimes) : null
    },
    totalMs: {
      p50: percentile(totalTimes, 50),
      p95: percentile(totalTimes, 95),
      max: totalTimes.length ? Math.max(...totalTimes) : null
    }
  }
}

const test = await prepare()
const started = performance.now()
const results = await Promise.all(
  Array.from({ length: CONCURRENCY }, () => requestRange(test.playUrl, RANGE_BYTES))
)
const elapsedMs = Math.round(performance.now() - started)
const summary = summarize(results)
const report = {
  timestamp: new Date().toISOString(),
  base: BASE,
  videoId: test.video.id,
  videoTitle: test.video.title,
  concurrency: CONCURRENCY,
  rangeBytes: RANGE_BYTES,
  elapsedMs,
  summary,
  failures: results.filter((r) => r.status !== 206 || r.bytes <= 0).slice(0, 20)
}
const outDir = path.resolve(process.cwd(), 'logs')
await fs.mkdir(outDir, { recursive: true })
const outFile = path.join(outDir, `video-load-test-${Date.now()}.json`)
await fs.writeFile(outFile, JSON.stringify(report, null, 2), 'utf8')
console.log(JSON.stringify({ outFile, ...report }, null, 2))