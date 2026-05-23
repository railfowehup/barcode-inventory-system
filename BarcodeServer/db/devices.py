# -*- coding: utf-8 -*-
"""设备心跳相关数据库操作"""
from datetime import datetime
from . import Database


def record_heartbeat(db: Database, device_id, device_name='', ip_address='', user_name=''):
    existing = db.conn.execute(
        'SELECT * FROM device_heartbeats WHERE device_id=?', (device_id,)
    ).fetchone()
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    if existing:
        db.conn.execute(
            '''UPDATE device_heartbeats SET device_name=?, ip_address=?, user_name=?, last_heartbeat=?
               WHERE device_id=?''',
            (device_name, ip_address, user_name, now, device_id)
        )
    else:
        db.conn.execute(
            '''INSERT INTO device_heartbeats (device_id, device_name, ip_address, user_name, last_heartbeat)
               VALUES (?, ?, ?, ?, ?)''',
            (device_id, device_name, ip_address, user_name, now)
        )
    db.conn.commit()


def update_group(db: Database, device_id, group):
    db.conn.execute(
        'UPDATE device_heartbeats SET device_group=? WHERE device_id=?',
        (group, device_id)
    )
    db.conn.commit()


def get_online(db: Database, timeout_seconds=30):
    rows = db.conn.execute('''
        SELECT * FROM device_heartbeats
        WHERE last_heartbeat >= datetime('now', 'localtime', ?)
        ORDER BY last_heartbeat DESC
    ''', (f'-{timeout_seconds} seconds',)).fetchall()
    return db._rows_to_list(rows)


def get_all(db: Database, limit=50):
    rows = db.conn.execute('''
        SELECT * FROM device_heartbeats
        ORDER BY last_heartbeat DESC LIMIT ?
    ''', (limit,)).fetchall()
    return db._rows_to_list(rows)


def log_connection(db: Database, device_id, device_name='', user_name='', ip_address=''):
    db.conn.execute(
        '''INSERT INTO connection_logs (device_id, device_name, user_name, ip_address)
           VALUES (?, ?, ?, ?)''',
        (device_id, device_name, user_name, ip_address)
    )
    db.conn.commit()


def get_connection_logs(db: Database, limit=50):
    rows = db.conn.execute(
        'SELECT * FROM connection_logs ORDER BY connected_at DESC LIMIT ?', (limit,)
    ).fetchall()
    return db._rows_to_list(rows)


def get_connection_count(db: Database, device_id=None):
    if device_id:
        row = db.conn.execute(
            'SELECT COUNT(*) as cnt FROM connection_logs WHERE device_id=?', (device_id,)
        ).fetchone()
    else:
        row = db.conn.execute('SELECT COUNT(*) as cnt FROM connection_logs').fetchone()
    return row['cnt'] if row else 0
