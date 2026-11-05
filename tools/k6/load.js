import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:18080';
const SERIES_BASE = Number(__ENV.LOAD_SERIES_BASE || 9001);
const STUDENT_USERNAME = __ENV.STUDENT_USERNAME || 'student';
const STUDENT_PASSWORD = __ENV.STUDENT_PASSWORD;

export const options = {
  scenarios: {
    browse: {
      exec: 'browse',
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '2m', target: 100 },
        { duration: '1m', target: 100 },
        { duration: '30s', target: 0 },
      ],
      gracefulStop: '30s',
      tags: { scenario: 'browse' },
    },
    playback_control: {
      exec: 'playback_control',
      executor: 'constant-vus',
      vus: 5,
      duration: '3m',
      startTime: '30s',
      tags: { scenario: 'playback_control' },
    },
    order_write: {
      exec: 'order_write',
      executor: 'per-vu-iterations',
      vus: 5,
      iterations: 1,
      maxDuration: '2m',
      startTime: '45s',
      tags: { scenario: 'order_write' },
    },
    order_read: {
      exec: 'order_read',
      executor: 'constant-vus',
      vus: 20,
      duration: '3m',
      startTime: '1m',
      tags: { scenario: 'order_read' },
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.02'],
    'http_req_duration{scenario:browse}': ['p(95)<300', 'p(99)<800'],
    'http_req_duration{scenario:playback_control}': ['p(95)<800', 'p(99)<2000'],
    'http_req_duration{scenario:order_write}': ['p(95)<1000', 'p(99)<2500'],
    'http_req_duration{scenario:order_read}': ['p(95)<300', 'p(99)<800'],
  },
};

function login() {
  if (!STUDENT_PASSWORD) {
    throw new Error('STUDENT_PASSWORD is required');
  }
  const response = http.post(`${BASE}/api/auth/login`, JSON.stringify({
    username: STUDENT_USERNAME,
    password: STUDENT_PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' } });
  check(response, { 'login 200': (r) => r.status === 200 });
  return response.json().token;
}

function auth(token) {
  return { Authorization: `Bearer ${token}` };
}

function createPaidOrder(token, seriesId, requestId) {
  const order = http.post(`${BASE}/api/orders`, JSON.stringify({
    seriesId,
    clientRequestId: requestId,
  }), { headers: { ...auth(token), 'Content-Type': 'application/json' } });
  check(order, { 'order create 200': (r) => r.status === 200 });
  if (order.status !== 200) return null;
  const orderNo = order.json().orderNo;
  const pay = http.post(`${BASE}/api/orders/${orderNo}/pay`, null, { headers: auth(token) });
  check(pay, { 'order pay 200': (r) => r.status === 200 });
  const simulated = http.post(`${BASE}/api/payments/mock/${orderNo}/events`, JSON.stringify({
    scenario: 'SUCCESS',
  }), { headers: { ...auth(token), 'Content-Type': 'application/json' } });
  check(simulated, { 'mock success 200': (r) => r.status === 200 });
  return orderNo;
}

export function setup() {
  const token = login();
  const orderNos = [];
  for (let i = 0; i < 5; i++) {
    const orderNo = createPaidOrder(token, SERIES_BASE + i, `load-setup-${Date.now()}-${i}`);
    if (orderNo) orderNos.push(orderNo);
  }
  return { token, orderNos };
}

export function browse(data) {
  const headers = auth(data.token);
  const series = http.get(`${BASE}/api/learning/series`, { headers, tags: { scenario: 'browse' } });
  check(series, { 'series 200': (r) => r.status === 200 });
  const detail = http.get(`${BASE}/api/learning/series/1`, { headers, tags: { scenario: 'browse' } });
  check(detail, { 'detail 200': (r) => r.status === 200 });
  sleep(0.5);
}

export function playback_control(data) {
  const play = http.get(`${BASE}/api/learning/videos/1/play`, {
    headers: auth(data.token),
    tags: { scenario: 'playback_control' },
  });
  check(play, { 'play 200': (r) => r.status === 200 });
  sleep(3);
}

export function order_write(data) {
  const index = (__VU - 1) % 5;
  createPaidOrder(data.token, SERIES_BASE + 5 + index, `load-write-${Date.now()}-${__VU}`);
  sleep(1);
}

export function order_read(data) {
  if (!data.orderNos.length) return;
  const orderNo = data.orderNos[__VU % data.orderNos.length];
  const detail = http.get(`${BASE}/api/orders/${orderNo}`, {
    headers: auth(data.token),
    tags: { scenario: 'order_read' },
  });
  check(detail, { 'order detail 200': (r) => r.status === 200 });
  sleep(0.5);
}
