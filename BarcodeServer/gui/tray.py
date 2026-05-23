# -*- coding: utf-8 -*-
"""系统托盘应用"""
import os
import threading
from PIL import Image, ImageDraw

from . import BASE_DIR, EXPORT_DIR


class SystemTrayApp:
    def __init__(self, root, server_mgr, status_callback=None):
        self.root = root
        self.server = server_mgr
        self.status_callback = status_callback
        self.tray_icon = None
        self._init_tray()

    def _init_tray(self):
        try:
            import pystray
        except ImportError:
            return

        icon_size = 64
        image = Image.new('RGBA', (icon_size, icon_size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        draw.rectangle([8, 20, 56, 50], fill=(0, 212, 255, 200), outline=(0, 150, 200, 255), width=2)
        draw.rectangle([12, 10, 52, 22], fill=(0, 180, 220, 180), outline=(0, 150, 200, 255), width=1)
        draw.text((20, 28), "📦", font=None, fill=(255, 255, 255, 255))

        def on_open(icon, item):
            self.root.after(0, self._show_window)

        def on_restart(icon, item):
            self.root.after(0, self._restart_server)

        def on_open_export(icon, item):
            if not os.path.exists(EXPORT_DIR):
                os.makedirs(EXPORT_DIR)
            os.startfile(EXPORT_DIR)

        def on_open_data(icon, item):
            os.startfile(BASE_DIR)

        def on_quit(icon, item):
            icon.stop()
            self.root.after(0, self._quit)

        def on_double_click(icon):
            self.root.after(0, self._show_window)

        import pystray
        menu = pystray.Menu(
            pystray.MenuItem('📊 显示主界面', on_open, default=True),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem(lambda item: self.server.get_status_text(), None, enabled=False),
            pystray.MenuItem('🔄 重启服务器', on_restart),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem('📁 打开导出目录', on_open_export),
            pystray.MenuItem('📂 打开数据目录', on_open_data),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem('❌ 退出', on_quit),
        )

        self.tray_icon = pystray.Icon('barcode-server', image, '📦 包裹管理系统', menu)
        self.tray_icon.on_double_click = on_double_click

    def _show_window(self):
        self.root.deiconify()
        self.root.lift()
        self.root.focus_force()

    def _restart_server(self):
        result = self.server.restart()
        if self.status_callback:
            self.status_callback()
        try:
            from plyer import notification
            notification.notify(
                title='📦 包裹管理系统',
                message='✅ 服务器重启成功' if result else '❌ 服务器重启失败',
                timeout=3,
            )
        except:
            pass

    def _quit(self):
        self.server.stop()
        self.root.quit()
        os._exit(0)

    def run(self):
        if self.tray_icon:
            threading.Thread(target=self.tray_icon.run, daemon=True).start()
