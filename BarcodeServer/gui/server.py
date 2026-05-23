# -*- coding: utf-8 -*-
"""管理 Flask 服务器进程"""
import threading
import time
from . import SERVER_PORT, get_local_ips


class ServerManager:
    def __init__(self):
        self.server_thread = None
        self.running = False
        self._flask_app = None

    def start(self):
        if self.running:
            return True
        try:
            from api import app, start_server
            self._flask_app = app
            self.running = True
            self.server_thread = threading.Thread(
                target=start_server, daemon=True,
                kwargs={'port': SERVER_PORT}
            )
            self.server_thread.start()
            for _ in range(30):
                if self._check_health():
                    return True
                time.sleep(0.5)
            return False
        except Exception as e:
            print(f"启动服务器失败: {e}")
            return False

    def stop(self):
        self.running = False

    def restart(self):
        self.stop()
        time.sleep(1)
        return self.start()

    def _check_health(self):
        try:
            import urllib.request
            resp = urllib.request.urlopen(
                f'http://127.0.0.1:{SERVER_PORT}/api/health', timeout=1
            )
            return resp.status == 200
        except:
            return False

    def is_running(self):
        return self.running and self._check_health()

    def get_status_text(self):
        if self.is_running():
            ips = get_local_ips()
            ip = ips[0] if ips else 'localhost'
            return f"🟢 运行中 | http://{ip}:{SERVER_PORT}"
        return "🔴 已停止"
