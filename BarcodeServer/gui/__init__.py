# -*- coding: utf-8 -*-
"""
📦 包裹管理系统 - tkinter 桌面管理界面
按功能拆分到各子模块
"""
import os
import sys
import socket
import json
import urllib.request

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EXPORT_DIR = os.path.join(BASE_DIR, 'exports')
SERVER_PORT = int(os.environ.get('BARCODE_SERVER_PORT', 3000))


def api_get(path, timeout=3):
    """调用 API 并自动解包 data 字段，返回 (data_dict, error_str)"""
    try:
        resp = urllib.request.urlopen(
            f'http://127.0.0.1:{SERVER_PORT}{path}', timeout=timeout
        )
        body = json.loads(resp.read().decode())
        if body.get('success'):
            return body.get('data'), None
        return None, body.get('error', '未知错误')
    except Exception as e:
        return None, str(e)


def get_local_ips():

    """获取本机局域网 IPv4 地址"""
    ips = []
    try:
        import psutil
        for iface, addrs in psutil.net_if_addrs().items():
            for addr in addrs:
                if addr.family == socket.AF_INET and not addr.address.startswith('127.'):
                    ips.append(addr.address)
    except:
        pass
    if not ips:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(('8.8.8.8', 80))
            ips.append(s.getsockname()[0])
            s.close()
        except:
            ips.append('127.0.0.1')
    return ips


def play_sound():
    try:
        import winsound
        winsound.MessageBeep(winsound.MB_OK)
    except:
        pass


def show_notification(title, message, timeout=3):
    try:
        from plyer import notification
        notification.notify(title=title, message=message, timeout=timeout)
    except:
        pass
