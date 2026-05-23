# -*- coding: utf-8 -*-
"""设备管理相关 API"""
from flask import request, jsonify
from . import app, db, now_str
from db import devices as db_devices


@app.route('/api/device/heartbeat', methods=['POST'])
def api_device_heartbeat():
    try:
        data = request.get_json()
        if not data or not data.get('device_id'):
            return jsonify({'error': '缺少 device_id'}), 400
        db_devices.record_heartbeat(db,
            device_id=data['device_id'],
            device_name=data.get('device_name', ''),
            ip_address=data.get('ip_address', ''),
            user_name=data.get('user_name', '')
        )
        return jsonify({'success': True, 'time': now_str()})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/devices/online', methods=['GET'])
def api_devices_online():
    try:
        timeout = request.args.get('timeout', 30, type=int)
        devices = db_devices.get_online(db, timeout)
        return jsonify({
            'success': True,
            'online_count': len(devices),
            'devices': devices
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/devices/history', methods=['GET'])
def api_devices_history():
    try:
        limit = request.args.get('limit', 50, type=int)
        devices = db_devices.get_all(db, limit)
        return jsonify({
            'success': True,
            'devices': devices
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/devices/group', methods=['PUT'])
def api_device_group():
    try:
        data = request.get_json() or {}
        device_id = data.get('device_id')
        group = data.get('group', '')
        if not device_id:
            return jsonify({'error': '缺少 device_id'}), 400
        db_devices.update_group(db, device_id, group)
        return jsonify({'success': True})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/devices/check-offline', methods=['GET'])
def api_check_offline():
    try:
        timeout = request.args.get('timeout', 60, type=int)
        devices = db_devices.get_online(db, timeout)
        all_devices = db_devices.get_all(db, 100)
        offline_devices = []
        for dev in all_devices:
            if not any(d['device_id'] == dev['device_id'] for d in devices):
                offline_devices.append(dev)
        return jsonify({
            'success': True,
            'online_count': len(devices),
            'offline_count': len(offline_devices),
            'offline_devices': offline_devices
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500
