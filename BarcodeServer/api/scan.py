# -*- coding: utf-8 -*-
"""扫码/批量扫码相关 API"""
from flask import request, jsonify
from . import app, db, notify_update, ok_response, err_response
from db import users as db_users
from db import records as db_records


@app.route('/api/scan', methods=['POST'])
def api_scan():
    data = request.get_json() or {}

    barcode = (data.get('barcode') or '').strip()
    user_id = data.get('user_id')
    user_name = (data.get('user_name') or '').strip()

    if not barcode:
        return err_response('缺少条码', 'ERR_MISSING_BARCODE', 400)

    if not user_id:
        if user_name:
            user = db_users.login_user(db, user_name)
            if isinstance(user, dict) and user.get('id'):
                user_id = user['id']
        else:
            return err_response('缺少用户信息（user_id 或 user_name）', 'ERR_MISSING_PARAM', 400)

    user = db_users.get_user(db, user_id)
    if not user:
        return err_response('用户不存在', 'ERR_USER_NOT_FOUND', 404)

    existing = db_records.find_by_barcode(db, barcode)
    if existing:
        status = existing.get('status', '')
        if status == '入库':
            return err_response('该包裹已入库', 'ERR_BARCODE_EXISTS', 409)
        if status == '分拣':
            return err_response('该包裹已分拣，如需入库请先回退状态', 'ERR_ALREADY_SORTED', 409)
        if status == '出库':
            return err_response('该包裹已出库，无法再次入库', 'ERR_ALREADY_SHIPPED', 409)
        if status == '签收':
            return err_response('该包裹已签收，无法再次入库', 'ERR_ALREADY_SIGNED', 409)


    record = db_records.add(db, barcode, user_id,
        address=data.get('address', ''),
        weight=data.get('weight', 0),
        note=data.get('note', '')
    )
    notify_update('scan', record)
    return ok_response({'record': record})


@app.route('/api/scan/batch', methods=['POST'])
def api_scan_batch():
    data = request.get_json() or {}

    records = data.get('records') or []
    user_id = data.get('user_id')
    user_name = (data.get('user_name') or '').strip()

    if not records:
        return err_response('缺少数据', 'ERR_MISSING_PARAM', 400)

    if not user_id:
        if user_name:
            user = db_users.login_user(db, user_name)
            if isinstance(user, dict) and user.get('id'):
                user_id = user['id']
        else:
            return err_response('缺少用户信息（user_id 或 user_name）', 'ERR_MISSING_PARAM', 400)

    user = db_users.get_user(db, user_id)
    if not user:
        return err_response('用户不存在', 'ERR_USER_NOT_FOUND', 404)

    inserted = 0
    for item in records:
        ts = None
        if item.get('timestamp'):
            try:
                from datetime import datetime as dt
                ts = dt.fromisoformat(item['timestamp'].replace('Z', '+00:00'))
                ts = ts.strftime('%Y-%m-%d %H:%M:%S')
            except:
                pass
        existing = db_records.find_by_barcode(db, (item.get('barcode') or '').strip())
        if not existing:
            db_records.add(db,
                barcode=item.get('barcode', ''),
                user_id=user_id,
                note=item.get('note', ''),
                status='入库',
                created_at=ts
            )
            inserted += 1

    if inserted > 0:
        notify_update('scan_batch', {'inserted': inserted})
    return ok_response({'inserted': inserted})
