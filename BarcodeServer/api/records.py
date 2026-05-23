# -*- coding: utf-8 -*-
"""包裹记录 CRUD API（分拣、出库、签收、改地址、编辑、删除、查询、统计）"""
from flask import request, jsonify
from . import app, db, notify_update, now_str, ok_response, err_response
from db import users as db_users
from db import records as db_records


@app.route('/api/sort/check', methods=['POST'])
def api_sort_check():
    """分拣前置检查"""
    data = request.get_json()
    if not data or not data.get('barcode'):
        return err_response('缺少条码', 'ERR_MISSING_BARCODE', 400)

    record = db_records.find_by_barcode(db, data['barcode'].strip())
    if not record:
        return ok_response({'allowed': False, 'message': '⚠️ 该包裹未入库，请先入库'})

    status = record['status']
    msgs = {
        '分拣': '⚠️ 该包裹已被分拣，不可重复分拣',
        '出库': '⚠️ 该包裹已出库，不可分拣',
        '签收': '⚠️ 该包裹已签收，不可分拣',
        '异常': '⚠️ 该包裹已标记异常，不可分拣',
    }
    if status in msgs:
        return ok_response({'allowed': False, 'message': msgs[status]})

    return ok_response({'allowed': True, 'message': '可以分拣', 'record': record})


@app.route('/api/sort', methods=['POST'])
def api_sort():
    data = request.get_json()
    if not data or not data.get('barcode') or not data.get('user_id'):
        return err_response('缺少条码或用户信息', 'ERR_MISSING_PARAM', 400)

    user = db_users.get_user(db, data['user_id'])
    if not user:
        return err_response('用户不存在', 'ERR_USER_NOT_FOUND', 404)

    record = db_records.find_by_barcode(db, data['barcode'].strip())
    if not record:
        return err_response('该包裹未入库，请先入库', 'ERR_RECORD_NOT_FOUND', 404)
    if record['status'] == '分拣':
        return err_response('该包裹已被分拣', 'ERR_ALREADY_SORTED', 409)

    updated = db_records.update_status(db, record['id'], '分拣',
        device_id=(data.get('device_id', '') or '').strip(),
        sort_at=now_str()
    )
    notify_update('sort', updated)
    return ok_response({'record': updated})


@app.route('/api/ship', methods=['POST'])
def api_ship():
    data = request.get_json()
    if not data or not data.get('barcode') or not data.get('user_id'):
        return err_response('缺少条码或用户信息', 'ERR_MISSING_PARAM', 400)

    record = db_records.find_by_barcode(db, data['barcode'].strip())
    if not record:
        return err_response('该包裹未入库', 'ERR_RECORD_NOT_FOUND', 404)
    if record['status'] == '出库':
        return err_response('该包裹已出库', 'ERR_ALREADY_SHIPPED', 409)
    if record['status'] == '入库':
        return err_response('该包裹尚未分拣，请先分拣', 'ERR_NOT_SORTED', 400)
    if record['status'] == '签收' or record['status'] == '异常':
        return err_response('该包裹已签收或异常，无法出库', 'ERR_INVALID_STATUS', 400)

    updated = db_records.update_status(db, record['id'], '出库',
        logistics_no=(data.get('logistics_no', '') or '').strip(),
        recipient=(data.get('recipient', '') or '').strip()
    )
    notify_update('ship', updated)
    return ok_response({'record': updated})


@app.route('/api/sign/check', methods=['POST'])
def api_sign_check():
    """签收前置检查"""
    data = request.get_json()
    if not data or not data.get('barcode'):
        return err_response('缺少条码', 'ERR_MISSING_BARCODE', 400)

    record = db_records.find_by_barcode(db, data['barcode'].strip())
    if not record:
        return ok_response({'allowed': False, 'message': '⚠️ 该包裹未入库，请先入库'})

    status = record['status']
    msgs = {
        '签收': '⚠️ 该包裹已签收，不可重复签收',
        '异常': '⚠️ 该包裹已标记异常，不可签收',
        '分拣': '⚠️ 该包裹正在分拣中，不可签收',
        '入库': '⚠️ 该包裹尚未出库，不可签收',
    }
    if status in msgs:
        return ok_response({'allowed': False, 'message': msgs[status]})

    return ok_response({'allowed': True, 'message': '可以签收', 'record': record})


@app.route('/api/sign', methods=['POST'])
def api_sign():
    data = request.get_json()
    if not data or not data.get('barcode') or not data.get('user_id'):
        return err_response('缺少条码或用户信息', 'ERR_MISSING_PARAM', 400)

    record = db_records.find_by_barcode(db, data['barcode'].strip())
    if not record:
        return err_response('该包裹未入库', 'ERR_RECORD_NOT_FOUND', 404)
    if record['status'] == '签收':
        return err_response('该包裹已签收', 'ERR_ALREADY_SIGNED', 409)

    exception_type = (data.get('exception_type', '') or '').strip()
    new_status = '异常' if exception_type else '签收'
    updated = db_records.update_status(db, record['id'], new_status,
        signer=(data.get('signer', '') or '').strip(),
        sign_time=now_str(),
        exception_type=exception_type
    )
    notify_update('sign', updated)
    return ok_response({'record': updated})


@app.route('/api/records/<int:record_id>/address', methods=['PUT'])
def api_update_address(record_id):
    data = request.get_json()
    if not data or not data.get('address'):
        return err_response('缺少地址', 'ERR_MISSING_PARAM', 400)

    result = db_records.change_address(db, record_id, data['address'],
        user_name=data.get('user_name', '未知')
    )
    if result is None:
        return err_response('记录不存在', 'ERR_RECORD_NOT_FOUND', 404)

    updated, history = result
    notify_update('address_change', updated)
    return ok_response({'record': updated, 'address_history': history})


@app.route('/api/records/<int:record_id>', methods=['PUT'])
def api_update_record(record_id):
    data = request.get_json()
    user = db_users.get_user(db, data.get('user_id'))
    if not user or user.get('role') != 'admin':
        return err_response('仅管理员可编辑', 'ERR_NO_PERMISSION', 403)

    record = db_records.find_by_id(db, record_id)
    if not record:
        return err_response('记录不存在', 'ERR_RECORD_NOT_FOUND', 404)

    kwargs = {}
    if 'address' in data:
        kwargs['address'] = data['address'].strip()
    if 'weight' in data:
        kwargs['weight'] = float(data['weight'])
    if 'note' in data:
        kwargs['note'] = data['note'].strip()

    updated = db_records.update_fields(db, record_id, **kwargs) if kwargs else record
    notify_update('record_update', updated)
    return ok_response({'record': updated})


@app.route('/api/records/<int:record_id>', methods=['DELETE'])
def api_delete_record(record_id):
    data = request.get_json()
    user = db_users.get_user(db, data.get('user_id'))
    if not user or user.get('role') != 'admin':
        return err_response('仅管理员可删除', 'ERR_NO_PERMISSION', 403)

    if db_records.delete(db, record_id):
        notify_update('delete', {'record_id': record_id})
        return ok_response({'message': '已删除'})
    return err_response('记录不存在', 'ERR_RECORD_NOT_FOUND', 404)


@app.route('/api/records/search', methods=['GET'])
def api_search_records():
    try:
        result = db_records.query_list(db,
            search_q=request.args.get('q'),
            status=request.args.get('status'),
            address=request.args.get('address'),
            date_from=request.args.get('date_from'),
            date_to=request.args.get('date_to'),
            page=int(request.args.get('page', 1)),
            page_size=int(request.args.get('page_size', 50))
        )
        return ok_response(result)
    except Exception as e:
        return err_response(f'查询失败: {str(e)}', 'ERR_DB_ERROR', 500)


@app.route('/api/records', methods=['GET'])
def api_get_records():
    try:
        result = db_records.query_list(db,
            user_id=request.args.get('user_id'),
            status=request.args.get('status'),
            date_from=request.args.get('date_from'),
            date_to=request.args.get('date_to'),
            page=int(request.args.get('page', 1)),
            page_size=int(request.args.get('page_size', 20))
        )
        return ok_response(result)
    except Exception as e:
        return err_response(f'查询失败: {str(e)}', 'ERR_DB_ERROR', 500)


@app.route('/api/stats', methods=['GET'])
def api_stats():
    try:
        stats = db_records.get_stats(db, user_id=request.args.get('user_id'))
        return ok_response(stats)
    except Exception as e:
        return err_response(f'统计失败: {str(e)}', 'ERR_DB_ERROR', 500)
