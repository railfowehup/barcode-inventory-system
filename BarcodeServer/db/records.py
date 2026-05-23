# -*- coding: utf-8 -*-
"""包裹记录相关数据库操作"""
import json
from datetime import datetime
from . import Database


def find_by_barcode(db: Database, barcode: str):
    row = db.conn.execute('SELECT * FROM records WHERE barcode = ?', (barcode,)).fetchone()
    return dict(row) if row else None


def find_by_id(db: Database, record_id: int):
    row = db.conn.execute('SELECT * FROM records WHERE id = ?', (record_id,)).fetchone()
    return dict(row) if row else None


def find_by_barcode_and_status(db: Database, barcode: str, status: str):
    row = db.conn.execute(
        'SELECT * FROM records WHERE barcode=? AND status=?', (barcode, status)
    ).fetchone()
    return dict(row) if row else None


def add(db: Database, barcode, user_id, address='', weight=0, note='', status='入库', created_at=None):
    db.conn.execute(
        '''INSERT INTO records (barcode, user_id, status, address, weight, note, created_at, updated_at, version)
           VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, 1)''',
        (barcode, user_id, status, address, weight, note, created_at)
    )
    db.conn.commit()
    return find_by_barcode(db, barcode)


def update_status(db: Database, record_id, status, **kwargs):
    """更新记录状态"""
    updates = ['status=?', 'updated_at=?', 'version=version+1']
    params = [status, datetime.now().strftime('%Y-%m-%d %H:%M:%S')]
    for key, val in kwargs.items():
        if val is not None:
            updates.append(f'{key}=?')
            params.append(val)
    params.append(record_id)
    db.conn.execute(
        f'UPDATE records SET {", ".join(updates)} WHERE id=?', params
    )
    db.conn.commit()
    return find_by_id(db, record_id)


def update_fields(db: Database, record_id, **fields):
    """更新指定字段"""
    updates = []
    params = []
    for key, val in fields.items():
        if val is not None:
            updates.append(f'{key}=?')
            params.append(val)
    if updates:
        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        updates.append('updated_at=?')
        params.append(now)
        updates.append('version=version+1')
        params.append(record_id)
        db.conn.execute(
            f'UPDATE records SET {", ".join(updates)} WHERE id=?', params
        )
        db.conn.commit()
    return find_by_id(db, record_id)


def delete(db: Database, record_id):
    db.conn.execute('DELETE FROM records WHERE id=?', (record_id,))
    db.conn.commit()
    return True


def change_address(db: Database, record_id, address, user_name=''):
    record = find_by_id(db, record_id)
    if not record:
        return None

    history = []
    try:
        history = json.loads(record.get('address_history', '[]'))
    except:
        pass

    history.append({
        'from': record.get('address', ''),
        'to': address,
        'changed_by': user_name,
        'time': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    })

    updated = update_fields(db, record_id,
        address=address,
        address_history=json.dumps(history, ensure_ascii=False)
    )
    return updated, history


def query_list(db: Database, user_id=None, page=1, page_size=50, status=None,
               date_from=None, date_to=None, search_q=None, address=None):
    """通用查询，支持分页、筛选、搜索"""
    where = []
    params = []

    if user_id:
        where.append('r.user_id=?')
        params.append(user_id)
    if status:
        where.append('r.status=?')
        params.append(status)
    if date_from:
        where.append('r.created_at>=?')
        params.append(date_from)
    if date_to:
        where.append('r.created_at<=?')
        params.append(date_to + ' 23:59:59')
    if search_q:
        where.append('(r.barcode LIKE ? OR r.note LIKE ?)')
        params.append(f'%{search_q}%')
        params.append(f'%{search_q}%')
    if address:
        where.append('r.address=?')
        params.append(address)

    where_clause = 'WHERE ' + ' AND '.join(where) if where else ''
    offset = (page - 1) * page_size

    total = db.conn.execute(
        f'SELECT COUNT(*) as cnt FROM records r {where_clause}', params
    ).fetchone()['cnt']

    query_params = params + [page_size, offset]
    rows = db.conn.execute(f'''
        SELECT r.*, u.name as user_name, u.theme_color
        FROM records r
        JOIN users u ON r.user_id = u.id
        {where_clause}
        ORDER BY r.created_at DESC
        LIMIT ? OFFSET ?
    ''', query_params).fetchall()

    return {
        'total': total,
        'page': page,
        'page_size': page_size,
        'records': db._rows_to_list(rows)
    }


def get_stats(db: Database, user_id=None):
    where = 'WHERE user_id=?' if user_id else ''
    params = [user_id] if user_id else []

    def count(sql, extra=''):
        full_sql = f'SELECT COUNT(*) as cnt FROM records {where}'
        if extra:
            if where:
                full_sql += f' {extra}'
            else:
                full_sql += f' WHERE {extra.lstrip("AND ")}'
        return db.conn.execute(full_sql, params).fetchone()['cnt']

    total = count('')
    today = count('', "AND date(created_at)=date('now','localtime')")
    this_week = count('', "AND created_at>=datetime('now','localtime','weekday 0','-7 days')")
    this_month = count('', "AND strftime('%Y-%m',created_at)=strftime('%Y-%m','now','localtime')")

    in_count = count('', "AND status='入库'")
    sort_count = count('', "AND status='分拣'")
    ship_count = count('', "AND status='出库'")
    sign_count = count('', "AND status='签收'")
    duplicate_count = count('', "AND is_duplicate=1")

    # 每日统计（近7天）
    daily_where = 'WHERE user_id=?' if user_id else ''
    daily_params = [user_id] if user_id else []
    daily_rows = db.conn.execute(f'''
        SELECT date(created_at) as date, COUNT(*) as count
        FROM records {daily_where}
        WHERE created_at>=datetime('now','localtime','-7 days')
        GROUP BY date(created_at) ORDER BY date DESC
    ''', daily_params).fetchall()

    # 地址分布
    address_where = 'WHERE user_id=? AND address!=?' if user_id else 'WHERE address!=?'
    address_params = [user_id, ''] if user_id else ['']
    address_rows = db.conn.execute(f'''
        SELECT address, COUNT(*) as count
        FROM records {address_where}
        GROUP BY address ORDER BY count DESC
    ''', address_params).fetchall()

    # 每人工作量
    user_where = 'WHERE r.user_id=?' if user_id else ''
    user_params = [user_id] if user_id else []
    user_rows = db.conn.execute(f'''
        SELECT u.name, u.theme_color, COUNT(*) as count
        FROM records r JOIN users u ON r.user_id=u.id
        {user_where}
        GROUP BY r.user_id ORDER BY count DESC
    ''', user_params).fetchall()

    return {
        'total': total, 'today': today,
        'this_week': this_week, 'this_month': this_month,
        'in_count': in_count, 'sort_count': sort_count,
        'ship_count': ship_count, 'sign_count': sign_count,
        'duplicate_count': duplicate_count,
        'daily_stats': db._rows_to_list(daily_rows),
        'address_stats': db._rows_to_list(address_rows),
        'user_stats': db._rows_to_list(user_rows),
    }


def get_all_for_export(db: Database, user_id=None, status=None, date_from=None, date_to=None):
    where = []
    params = []
    if user_id:
        where.append('r.user_id=?')
        params.append(user_id)
    if status:
        where.append('r.status=?')
        params.append(status)
    if date_from:
        where.append('r.created_at>=?')
        params.append(date_from)
    if date_to:
        where.append('r.created_at<=?')
        params.append(date_to + ' 23:59:59')

    where_clause = 'WHERE ' + ' AND '.join(where) if where else ''
    rows = db.conn.execute(f'''
        SELECT r.*, u.name as user_name
        FROM records r JOIN users u ON r.user_id=u.id
        {where_clause}
        ORDER BY r.created_at DESC
    ''', params).fetchall()
    return db._rows_to_list(rows)
