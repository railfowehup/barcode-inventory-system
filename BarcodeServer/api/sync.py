# -*- coding: utf-8 -*-
"""
同步/合并相关 API

=== 版本说明 ===
推荐使用新版增量同步 API:
  - /api/sync/push  (增量推送)
  - /api/sync/pull  (增量拉取)
  - /api/sync/status (同步状态)

旧版全量同步（保留兼容，标记弃用）:
  - /api/merge
  - /api/pull
"""
from flask import request, jsonify
from . import app, db, notify_update, now_str, ok_response, err_response
from db import sync as db_sync
from db import records as db_records
from db import users as db_users


# ==================== 新版增量同步 API ====================

@app.route('/api/sync/push', methods=['POST'])
def api_sync_push():
    """手机推送增量记录到电脑"""
    try:
        data = request.get_json() or {}
        records = data.get('records', [])
        device_id = data.get('device_id', '')
        user_name = data.get('user_name', '')

        if not records:
            return ok_response({'inserted': 0, 'updated': 0, 'conflicts': 0})

        inserted = 0
        updated = 0
        conflicts = []

        for item in records:
            barcode = (item.get('barcode') or '').strip()
            if not barcode:
                continue

            existing = db_records.find_by_barcode(db, barcode)
            if existing:
                phone_version = item.get('version', 1)
                server_version = existing.get('version', 1)
                if phone_version > server_version:
                    db_records.update_status(db, existing['id'],
                        item.get('status', existing['status']),
                        address=item.get('address', existing.get('address', '')),
                        weight=item.get('weight', existing.get('weight', 0)),
                        note=item.get('note', existing.get('note', '')),
                        updated_at=item.get('updated_at', now_str()),
                        version=phone_version
                    )
                    updated += 1
                elif phone_version == server_version:
                    conflicts.append({
                        'barcode': barcode,
                        'server_record': existing,
                        'phone_record': item
                    })
            else:
                db_records.add(db, barcode,
                    user_id=item.get('user_id', 1),
                    address=item.get('address', ''),
                    weight=item.get('weight', 0),
                    note=item.get('note', ''),
                    status=item.get('status', '入库'),
                    created_at=item.get('created_at')
                )
                inserted += 1

        if inserted > 0 or updated > 0:
            notify_update('sync_push', {'inserted': inserted, 'updated': updated})

        return ok_response({
            'inserted': inserted,
            'updated': updated,
            'conflicts': conflicts,
            'conflict_count': len(conflicts)
        })
    except Exception as e:
        return err_response(str(e), 'ERR_DB_ERROR', 500)


@app.route('/api/sync/pull', methods=['POST'])
def api_sync_pull():
    """手机从电脑拉取增量记录"""
    try:
        data = request.get_json() or {}
        since = data.get('since')
        users, records = db_sync.pull_data(db, since=since)
        return ok_response({
            'users': users,
            'records': records,
            'total_records': len(records),
            'server_time': now_str()
        })
    except Exception as e:
        return err_response(str(e), 'ERR_DB_ERROR', 500)


@app.route('/api/sync/status', methods=['GET'])
def api_sync_status():
    try:
        stats = db_records.get_stats(db)
        return ok_response({
            'total_records': stats.get('total', 0),
            'server_time': now_str(),
            'device_id': request.args.get('device_id', '')
        })
    except Exception as e:
        return err_response(str(e), 'ERR_DB_ERROR', 500)


# ==================== 旧版全量同步（保留兼容，标记弃用） ====================

@app.route('/api/merge', methods=['POST'])
def api_merge():
    """[弃用] 旧版全量合并 - 请改用 /api/sync/push"""
    data = request.get_json()
    if not data or not data.get('records'):
        return err_response('缺少数据', 'ERR_MISSING_PARAM', 400)

    result = db_sync.merge_data(db,
        users_data=data.get('users'),
        records_data=data['records'],
        device_id=data.get('device_id', ''),
        user_name=data.get('user_name', '')
    )
    return ok_response(result)


@app.route('/api/pull', methods=['POST'])
def api_pull():
    """[弃用] 旧版全量拉取 - 请改用 /api/sync/pull"""
    data = request.get_json() or {}
    users, records = db_sync.pull_data(db, since=data.get('since'))
    return ok_response({
        'users': users,
        'records': records,
        'total_records': len(records)
    })


@app.route('/api/sync-logs', methods=['GET'])
def api_sync_logs():
    logs = db_sync.get_sync_logs(db)
    return ok_response({'sync_logs': logs})
