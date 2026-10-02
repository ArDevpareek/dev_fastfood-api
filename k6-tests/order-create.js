// Milestone 3 load test: POST /order under a burst of concurrent users.
//
// Same load shape as the Phase 1 GET /restaurants baseline (see README):
// ramp to 50 virtual users over 10s, hold 20s, ramp down over 10s. Each
// virtual user sends orders back-to-back with no pause, so this measures
// how the endpoint copes when many orders arrive at once.
//
// Run against either version by passing the status code it's expected to
// return — the old synchronous endpoint answers 201 Created, the Kafka
// version answers 202 Accepted:
//
//   k6 run -e EXPECTED_STATUS=201 k6-tests/order-create.js   (old, sync)
//   k6 run -e EXPECTED_STATUS=202 k6-tests/order-create.js   (Milestone 3)
//
// USER_ID / RESTAURANT_ID / MENU_ITEM_ID default to rows in the local dev
// database (Priya, Sagar Ratna, Butter Chicken). They aren't part of
// init.sql, so on a fresh database pass your own IDs with -e.

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EXPECTED_STATUS = parseInt(__ENV.EXPECTED_STATUS || '202', 10);

const payload = JSON.stringify({
    userId: __ENV.USER_ID || '09a85a76-9458-418c-bcc1-a1e942da5825',
    restaurantId: __ENV.RESTAURANT_ID || '909176a9-6dc9-4832-81f5-dbd9f71ac759',
    deliveryAddress: 'K6 load test',
    items: [{ menuItemId: __ENV.MENU_ITEM_ID || '925c610a-ea5d-4b14-ab24-67e76f116c65', quantity: 2 }],
});

export const options = {
    stages: [
        { duration: '10s', target: 50 },
        { duration: '20s', target: 50 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000'],
        http_req_failed: ['rate<0.05'],
    },
};

export default function () {
    const res = http.post(`${BASE_URL}/order`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });
    check(res, { [`status is ${EXPECTED_STATUS}`]: (r) => r.status === EXPECTED_STATUS });
}
