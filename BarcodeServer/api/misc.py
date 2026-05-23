# -*- coding: utf-8 -*-
"""杂项 API（健康检查、二维码等）"""
import io
import base64
from datetime import datetime

from flask import request, jsonify
from . import app, db, get_local_ip, PORT


@app.route('/api/health', methods=['GET'])
def api_health():
    return jsonify({'status': 'ok', 'time': datetime.now().isoformat()})


@app.route('/api/qrcode', methods=['GET'])
def api_qrcode():
    try:
        import qrcode as qrcode_lib
        ip = get_local_ip()
        server_url = f'http://{ip}:{PORT}'
        img = qrcode_lib.make(server_url)
        buffer = io.BytesIO()
        img.save(buffer, format='PNG')
        img_base64 = base64.b64encode(buffer.getvalue()).decode()
        data_url = f'data:image/png;base64,{img_base64}'
        return jsonify({
            'success': True,
            'url': server_url,
            'qrcode': data_url
        })
    except Exception as e:
        return jsonify({'error': f'二维码生成失败: {str(e)}'}), 500
