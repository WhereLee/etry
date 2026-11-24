import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:8084';
const USERNAME = __ENV.LOAD_USER || 'load_probe';
const PASSWORD = __ENV.LOAD_PASS || 'Load#Probe2026';

export const options = {
  vus: Number(__ENV.VUS || 5),
  duration: __ENV.DURATION || '20s',
  thresholds: { http_req_failed: ['rate<0.02'] },
};

export default function () {
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(res, { 'login 200': (x) => x.status === 200 });
  sleep(0.5);
}
