import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:18080';
const STUDENT_USERNAME = __ENV.STUDENT_USERNAME || 'student';
const STUDENT_PASSWORD = __ENV.STUDENT_PASSWORD;

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
  },
};

export function setup() {
  if (!STUDENT_PASSWORD) {
    throw new Error('STUDENT_PASSWORD is required');
  }
  const response = http.post(`${BASE}/api/auth/login`, JSON.stringify({
    username: STUDENT_USERNAME,
    password: STUDENT_PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' } });
  check(response, { 'login 200': (r) => r.status === 200 });
  return { token: response.json().token };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };
  const series = http.get(`${BASE}/api/learning/series`, { headers });
  check(series, { 'series 200': (r) => r.status === 200 });
  const detail = http.get(`${BASE}/api/learning/series/1`, { headers });
  check(detail, { 'detail 200': (r) => r.status === 200 });
  const play = http.get(`${BASE}/api/learning/videos/1/play`, { headers });
  check(play, { 'play 200': (r) => r.status === 200 });
  sleep(1);
}
