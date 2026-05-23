# -*- coding: utf-8 -*-
"""
📦 包裹管理系统 - Flask API 服务
按功能拆分到各子模块

=== 统一 API 规范 ===
成功响应:
    { "success": true, "data": {...} }
失败响应:
    { "success": false, "error": "描述", "error_code": "ERR_xxx" }

=== 错误码列表 ===
ERR_MISSING_BARCODE    - 缺少条码
ERR_MISSING_PARAM      - 缺少请求参数
ERR_USER_NOT_FOUND     - 用户不存在
ERR_RECORD_NOT_FOUND   - 记录不存在
ERR_BARCODE_EXISTS     - 条码已入库
ERR_ALREADY_SORTED     - 已分拣
ERR_ALREADY_SHIPPED    - 已出库
ERR_ALREADY_SIGNED     - 已签收
ERR_NO_PERMISSION      - 无权限（非管理员）
ERR_INVALID_DATA       - 数据格式错误
ERR_DB_ERROR           - 数据库错误
ERR_NETWORK            - 网络错误
ERR_INTERNAL           - 内部错误
ERR_QR_FAILED          - 二维码生成失败

=== 日志格式 ===
[API] /api/scan <- POST from 192.168.1.x
[API] /api/scan -> 200 OK (0.023s)
[API] /api/scan -> 400 ERR_MISSING_BARCODE (0.001s)
"""
import os
import socket
import time
import uuid
from datetime import datetime

from flask import Flask, request, jsonify
from flask_cors import CORS

from db import Database, BASE_DIR, EXPORT_DIR

app = Flask(__name__)
CORS(app)
db = Database()

PORT = int(os.environ.get('PORT', 3000))
HOST = '0.0.0.0'

# 确保导出目录存在
os.makedirs(EXPORT_DIR, exist_ok=True)

# 桌面 GUI 更新监听器
update_listeners = []


def register_update_listener(callback):
    if callback not in update_listeners:
        update_listeners.append(callback)


def notify_update(event_type='update', data=None):
    for callback in list(update_listeners):
        try:
            callback(event_type, data)
        except Exception:
            pass


def get_local_ip():
    """获取本机局域网IP地址"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        pass
    try:
        hostname = socket.gethostname()
        return socket.gethostbyname(hostname)
    except:
        return '127.0.0.1'


def now_str():
    return datetime.now().strftime('%Y-%m-%d %H:%M:%S')


# ==================== 统一响应工具 ====================

def ok_response(data=None):
    """成功响应 - 统一格式，始终包含 data 字段"""
    if data is None:
        return jsonify({'success': True, 'data': None})
    return jsonify({'success': True, 'data': data})


def err_response(error_msg, error_code='ERR_INTERNAL', status_code=400):
    """错误响应 - 统一格式"""
    return jsonify({
        'success': False,
        'error': error_msg,
        'error_code': error_code,
    }), status_code


# ==================== 请求日志中间件 ====================

@app.before_request
def log_request_start():
    """记录请求开始时间"""
    request._start_time = time.time()
    request._request_id = str(uuid.uuid4())[:8]
    # 跳过健康检查的日志
    if request.path == '/api/health':
        return
    ip = request.remote_addr or 'unknown'
    print(f"[API] [{request._request_id}] {request.path} <- {request.method} from {ip}")


@app.after_request
def log_request_end(response):
    """记录请求结束"""
    if request.path == '/api/health':
        return response
    elapsed = time.time() - getattr(request, '_start_time', time.time())
    status_code = response.status_code
    rid = getattr(request, '_request_id', '????')
    # 尝试从响应中提取 error_code
    try:
        body = response.get_json()
        if body and isinstance(body, dict):
            ec = body.get('error_code', '')
            if ec:
                print(f"[API] [{rid}] {request.path} -> {status_code} {ec} ({elapsed:.3f}s)")
            else:
                print(f"[API] [{rid}] {request.path} -> {status_code} ({elapsed:.3f}s)")
        else:
            print(f"[API] [{rid}] {request.path} -> {status_code} ({elapsed:.3f}s)")
    except Exception:
        print(f"[API] [{rid}] {request.path} -> {status_code} ({elapsed:.3f}s)")
    # 添加请求ID到响应头（方便调试）
    response.headers['X-Request-ID'] = rid
    return response


# 导入并注册所有路由
from . import auth
from . import records
from . import sync
from . import export
from . import devices
from . import misc


def start_server(port=PORT, host=HOST, debug=False):
    """启动 Flask 服务器"""
    from waitress import serve
    print(f"🚀 服务器启动: http://{host}:{port}")
    print(f"📡 局域网访问: http://{get_local_ip()}:{port}")
    print(f"📋 错误码统一格式生效中 - 遇到问题请报 error_code")
    serve(app, host=host, port=port)
