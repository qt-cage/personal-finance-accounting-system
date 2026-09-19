"""Bounded, synthetic-data verification for an explicitly isolated local database.

Requires MYSQL_PWD in the process environment and an already-running test backend.
Example (credentials must never be committed):
  python focused_load_check.py --database finance_quality_test_example \
    --base-url http://localhost:18090 --mysql E:/MySQL/bin/mysql.exe \
    --output result.json

Each run creates a new test account; its rows remain in the disposable test database.
This is a local read-load smoke test, not a capacity or production SLA benchmark.
"""

import argparse
import concurrent.futures
import datetime as dt
import json
import math
import os
from pathlib import Path
import re
import secrets
import statistics
import subprocess
import time
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', required=True)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--mysql', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'finance_quality_test_[a-zA-Z0-9_]+', args.database):
        raise SystemExit('Refusing non-isolated database name')
    url = urllib.parse.urlparse(args.base_url)
    if (url.scheme != 'http' or url.hostname not in ('localhost', '127.0.0.1')
            or url.port is None or url.port in (8080, 8088, 5173)
            or url.path not in ('', '/') or url.username or url.password):
        raise SystemExit('Use an explicit, non-production local test port')
    if not os.environ.get('MYSQL_PWD'):
        raise SystemExit('MYSQL_PWD environment variable is required')

    def sql(statement):
        completed = subprocess.run(
            [args.mysql, '--host=127.0.0.1', '--port=3306', '--user=root',
             '--default-character-set=utf8mb4', '--batch', '--skip-column-names',
             '--database=' + args.database], input=statement, text=True,
            encoding='utf-8', capture_output=True, check=True)
        return completed.stdout.strip()

    assert sql('SELECT DATABASE();') == args.database
    token = None

    def http(path, body=None):
        headers = {'Content-Type': 'application/json', 'lang': 'zh-cn'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request(args.base_url.rstrip('/') + path,
            data=None if body is None else json.dumps(body).encode(), headers=headers)
        with urllib.request.urlopen(req, timeout=30) as response:
            assert response.status == 200, 'Unexpected HTTP status'
            data = json.load(response)
        assert data.get('code') == 200, 'Business response code: ' + str(data.get('code'))
        return data.get('data')

    username = 'load_' + secrets.token_hex(6)
    password = secrets.token_urlsafe(12)
    http('/api/user/register', {'username': username, 'password': password})
    login = http('/api/user/login', {'username': username, 'password': password})
    token = login['token']
    user_id = int(login['userId'])
    # Prove this HTTP instance writes to the requested test DB before bulk insertion.
    assert sql("SELECT id FROM `user` WHERE username='" + username + "';") == str(user_id)
    category_names = ['Load expense ' + str(i) for i in range(5)] + ['Load income ' + str(i) for i in range(2)]
    for i, name in enumerate(category_names):
        http('/api/category', {'name': name, 'type': 1 if i < 5 else 2})
    categories = {c['name']: int(c['id']) for c in http('/api/category/list') if c['userId'] == user_id}
    assert all(name in categories for name in category_names)
    # Integer cents keep the independent expected values exact.
    totals = {'expense': 0, 'income': 0}
    by_category = {name: 0 for name in category_names[:5]}
    by_month = {}
    september = {'total': 0, category_names[0]: 0}
    rows = []
    for i in range(10000):
        income = i % 4 == 0
        kind = 2 if income else 1
        name = category_names[5 + (i // 4) % 2] if income else category_names[(i // 4 + i % 4) % 5]
        cents = ((i * 37) % 99999) + 1
        date = dt.date(2026, 1, 1) + dt.timedelta(days=i % 365)
        month = date.strftime('%Y-%m')
        field = 'income' if income else 'expense'
        totals[field] += cents
        by_month.setdefault(month, {'expense': 0, 'income': 0})[field] += cents
        if not income:
            by_category[name] += cents
            if month == '2026-09':
                september['total'] += cents
                if name == category_names[0]:
                    september[name] += cents
        rows.append(f"({user_id},{categories[name]},{cents // 100}.{cents % 100:02d},{kind},'synthetic-load-{i:05d}','{date}',0)")
    started_seed = time.perf_counter()
    for offset in range(0, len(rows), 1000):
        sql('INSERT INTO bill (user_id,category_id,amount,type,remark,bill_date,deleted) VALUES ' + ','.join(rows[offset:offset + 1000]) + ';')
    seed_ms = (time.perf_counter() - started_seed) * 1000
    assert sql(f'SELECT COUNT(*) FROM bill WHERE user_id={user_id} AND deleted=0;') == '10000'
    for cat in (None, categories[category_names[0]]):
        http('/api/budget', {'categoryId': cat, 'month': '2026-09', 'amount': '500000.00'})

    def money(cents):
        return f'{cents // 100}.{cents % 100:02d}' if cents >= 0 else '-' + money(-cents)

    def normalize(value):
        from decimal import Decimal
        return f'{Decimal(str(value)):.2f}'

    expected_stats = {key: money(value) for key, value in totals.items()}
    expected_stats['balance'] = money(totals['income'] - totals['expense'])
    expected_category = {name: money(amount) for name, amount in by_category.items()}
    expected_month = {month: {key: money(amount) for key, amount in value.items()} for month, value in by_month.items()}
    expected_budget = {'total': money(september['total']), category_names[0]: money(september[category_names[0]])}
    dates = 'startDate=2026-01-01&endDate=2026-12-31'
    endpoints = {
        'pagination': '/api/bill/page?page=250&size=20',
        'statistics': '/api/bill/statistics?' + dates,
        'category_statistics': '/api/bill/category-stats?type=1&' + dates,
        'monthly_report': '/api/bill/month-summary?' + dates,
        'budget_read': '/api/budget/list?month=2026-09',
    }

    def validate(name, data):
        if name == 'pagination':
            actual = {'total': data['total'], 'records': len(data['records']), 'current': data['current']}
            assert all(row['userId'] == user_id and row['remark'].startswith('synthetic-load-') for row in data['records'])
            expected = {'total': 10000, 'records': 20, 'current': 250}
        elif name == 'statistics':
            actual = {key: normalize(data[key]) for key in expected_stats}
            expected = expected_stats
        elif name == 'category_statistics':
            actual = {row['category_name']: normalize(row['total_amount']) for row in data}
            expected = expected_category
        elif name == 'monthly_report':
            actual = {row['month']: {key: normalize(row[key]) for key in ('expense', 'income')} for row in data}
            expected = expected_month
        else:
            actual = {'total' if row['categoryId'] is None else row['categoryName']: normalize(row['spentAmount']) for row in data}
            assert len(data) == 2
            assert all(normalize(row['amount']) == '500000.00' for row in data)
            expected = expected_budget
        assert actual == expected, 'Response content does not match independent expected totals'
        return {'expected': expected, 'actual': actual, 'passed': True}

    result = {
        'measured_at': dt.datetime.now().astimezone().isoformat(),
        'scope': 'Local isolated test instance; deterministic synthetic bills; bounded read load only',
        'database': args.database, 'base_url': args.base_url,
        'dataset': {'bill_count': 10000, 'expense_count': 7500, 'income_count': 2500,
                    'range': ['2026-01-01', '2026-12-31'], 'amount_formula_cents': '(i*37)%99999+1',
                    'expense_categories': 5, 'income_categories': 2, 'budget_count': 2,
                    'batch_size': 1000, 'seed_elapsed_ms': round(seed_ms, 2)},
        'correctness': {}, 'load': {},
        'limitations': ['Single local Windows host; HTTP without production network latency',
                       'One synthetic account; five concurrent read workers; not a capacity limit',
                       'Does not measure concurrent writes, long-running soak, or all devices',
                       'SQL bulk load bypasses bill-create validation; functional flows checked separately'],
    }
    for name, path in endpoints.items():
        result['correctness'][name] = validate(name, http(path))
    for _ in range(3):
        for name, path in endpoints.items():
            validate(name, http(path))
    print('WARMUP_COMPLETE: starting 200 reads with 5 workers', flush=True)

    def request_once(name):
        started = time.perf_counter()
        try:
            response = http(endpoints[name])
            elapsed = (time.perf_counter() - started) * 1000
            validate(name, response)
            return {'endpoint': name, 'elapsed_ms': round(elapsed, 3), 'success': True}
        except Exception as exc:
            # Do not record request headers, payloads, credentials, or response bodies.
            return {'endpoint': name, 'elapsed_ms': round((time.perf_counter() - started) * 1000, 3),
                    'success': False, 'error_type': type(exc).__name__}

    workload = list(endpoints) * 40
    started_load = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        measurements = list(executor.map(request_once, workload))
    duration = time.perf_counter() - started_load

    def aggregate(measured):
        times = sorted(item['elapsed_ms'] for item in measured)
        failures = [item for item in measured if not item['success']]
        return {'requests': len(times), 'successes': len(times) - len(failures), 'failures': len(failures),
                'success_rate': (len(times) - len(failures)) / len(times),
                'p50_ms': round(statistics.median(times), 3),
                'p95_ms': times[math.ceil(len(times) * .95) - 1], 'max_ms': times[-1],
                'errors': failures}

    result['load'] = {'workers': 5, 'warmup_requests': 15,
                      'timing': 'HTTP response read completion; validation runs on every response',
                      'percentile_method': 'p95 nearest rank; p50 median',
                      'elapsed_seconds': round(duration, 3), 'requests_per_second': round(200 / duration, 2),
                      'overall': aggregate(measurements),
                      'endpoints': {name: aggregate([m for m in measurements if m['endpoint'] == name]) for name in endpoints}}
    path = Path(args.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result['load'], ensure_ascii=False, indent=2))
    print('Saved:', path)
    if result['load']['overall']['failures']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
