# -*- coding: utf-8 -*-
"""用户认证相关 API"""
from flask import request, jsonify
from . import app, db, ok_response, err_response
from db import users as db_users


@app.route('/api/login', methods=['POST'])
def api_login():
    data = request.get_json()
    if not data or not data.get('name'):
        return err_response('请输入姓名', 'ERR_MISSING_PARAM', 400)
    user = db_users.login_user(db, data['name'])
    if user is None:
        return err_response('登录失败', 'ERR_INTERNAL', 500)
    if isinstance(user, dict) and 'error' in user:
        return err_response(user['error'], 'ERR_INTERNAL', 500)
    return ok_response(user)


@app.route('/api/users', methods=['GET'])
def api_users():
    users = db_users.get_all_users(db)
    return ok_response({'users': users})
