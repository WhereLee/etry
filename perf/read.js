import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:8084';
const VUS = Number(__ENV.VUS || 50);
const DURATION = __ENV.DURATION || '30s';

export const options = {
  scenarios: {
    browse: { executor: 'constant-vus', vus: VUS, duration: DURATION },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const r = Math.random();
  let res;
  if (r < 0.80) {
    res = http.get(`${BASE}/api/materials?page=1&size=12`);
  } else if (r < 0.95) {
    res = http.get(`${BASE}/api/materials/search?keyword=%E5%87%BD%E6%95%B0`);
  } else {
    res = http.get(`${BASE}/api/materials/1/preview`);
  }
  check(res, { 'status 200': (x) => x.status === 200 });
  sleep(0.2);
}
