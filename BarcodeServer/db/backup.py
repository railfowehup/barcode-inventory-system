# -*- coding: utf-8 -*-
"""备份/恢复相关数据库操作"""
from datetime import datetime
from . import Database


def backup(db: Database):
    users = db._rows_to_list(db.conn.execute('SELECT * FROM users').fetchall())
    records = db._rows_to_list(
        db.conn.execute('SELECT * FROM records ORDER BY created_at DESC').fetchall()
    )
    sync_logs = db._rows_to_list(
        db.conn.execute('SELECT * FROM sync_logs ORDER BY sync_time DESC').fetchall()
    )
    return {
        'version': '1.0',
        'backup_time': datetime.now().isoformat(),
        'users': users,
        'records': records,
        'sync_logs': sync_logs,
    }


def restore(db: Database, data):
    users_restored = 0
    records_restored = 0

    for u in data.get('users', []):
        existing = db.conn.execute(
            'SELECT * FROM users WHERE name=?', (u.get('name', ''),)
        ).fetchone()
        if not existing:
            db.conn.execute(
                'INSERT INTO users (name, theme_color, role, created_at) VALUES (?, ?, ?, ?)',
                (u.get('name', ''), u.get('theme_color', '#2196F3'),
                 u.get('role', 'operator'), u.get('created_at'))
            )
            users_restored += 1

    for r in data.get('records', []):
        existing = db.conn.execute(
            'SELECT * FROM records WHERE barcode=?', (r.get('barcode', ''),)
        ).fetchone()
        if not existing:
            db.conn.execute(
                '''INSERT INTO records (barcode, user_id, status, address, weight, device_id, note,
                   recipient, logistics_no, signer, sign_time, exception_type, is_duplicate, created_at, sort_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)''',
                (r.get('barcode', ''), r.get('user_id', 1), r.get('status', '入库'),
                 r.get('address', ''), r.get('weight', 0), r.get('device_id', ''),
                 r.get('note', ''), r.get('recipient', ''), r.get('logistics_no', ''),
                 r.get('signer', ''), r.get('sign_time'), r.get('exception_type', ''),
                 r.get('is_duplicate', 0), r.get('created_at'), r.get('sort_at'))
            )
            records_restored += 1

    db.conn.commit()
    return users_restored, records_restored
