# -*- coding: utf-8 -*-
"""
📦 包裹管理系统 - Flask API 服务
按功能拆分到各子模块
"""
import os
import socket
from datetime import datetime

from flask import Flask
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
    serve(app, host=host, port=port)


