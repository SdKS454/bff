#!/usr/bin/env python3
"""Run only against disposable local BFF/User instances: creates test users and revokes their tokens."""
import argparse
import base64
import http.cookiejar
import json
import unittest
import urllib.error
import urllib.request
import uuid


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class UserIntegration(unittest.TestCase):
    def setUp(self):
        self.client = urllib.request.build_opener(
            NoRedirect(), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.login = 'bff_sync_' + uuid.uuid4().hex[:16]
        self.body = dict(login=self.login, password='local-test-password',
                         fullName='Тестовый пользователь', birthDate='2000-05-17')

    def call(self, base, path, method='GET', body=None, token=None):
        headers = {}
        data = None
        if body is not None:
            data = json.dumps(body).encode('utf-8')
            headers['Content-Type'] = 'application/json'
        if token:
            headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
        try:
            response = self.client.open(req, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read().decode('utf-8')
            content_type = response.headers.get('Content-Type', '')
            result = json.loads(raw) if raw and 'application/json' in content_type else raw
            return response.status, response.headers, result

    def register_and_login(self):
        status, _, user = self.call(BFF_URL, '/api/auth/register', 'POST', self.body)
        self.assertEqual(201, status)
        self.assertEqual('USER', user['role'])
        status, _, auth = self.call(BFF_URL, '/api/auth/login', 'POST',
                                    dict(login=self.login, password=self.body['password']))
        self.assertEqual(200, status)
        self.assertEqual(user['id'], auth['user']['id'])
        self.assertEqual('USER', auth['user']['role'])
        self.assertEqual('RS256', json.loads(base64.urlsafe_b64decode(
            auth['accessToken'].split('.')[0] + '==='))['alg'])
        return user, auth['accessToken']

    def test_registration_rules_match_actual_user_service(self):
        for patch in [dict(birthDate=None), dict(password='short'), dict(password='я' * 37),
                      dict(login='иван'), dict(fullName=' '), dict(fullName='a' * 201),
                      dict(birthDate='2026-02-30')]:
            for base, path in [(BFF_URL, '/api/auth/register'), (USER_URL, '/api/users/register')]:
                with self.subTest(base=base, invalid_fields=list(patch)):
                    status, _, error = self.call(base, path, 'POST', self.body | patch)
                    self.assertEqual(400, status)
                    self.assertTrue(error.get('error'))
        body = self.body | dict(role='ADMIN')
        status, _, user = self.call(BFF_URL, '/api/auth/register', 'POST', body)
        self.assertEqual(201, status)
        self.assertEqual('USER', user['role'])
        status, _, error = self.call(BFF_URL, '/api/auth/register', 'POST', self.body)
        self.assertEqual(409, status)
        self.assertEqual('Логин уже занят', error['error'])

    def test_session_profile_and_api_logout_revoke_real_jwt(self):
        user, token = self.register_and_login()
        self.assertEqual(204, self.call(USER_URL, '/api/users/validate', token=token)[0])
        status, _, profile = self.call(BFF_URL, '/api/users/me')
        self.assertEqual(200, status)
        self.assertEqual(user['id'], profile['id'])
        self.assertEqual(204, self.call(BFF_URL, '/api/auth/logout', 'POST')[0])
        self.assertEqual(401, self.call(USER_URL, '/api/users/validate', token=token)[0])
        self.assertEqual(401, self.call(BFF_URL, '/api/users/me')[0])
        self.assertEqual(204, self.call(BFF_URL, '/api/auth/logout', 'POST', token=token)[0])

    def test_browser_logout_post_revokes_but_get_does_not(self):
        _, token = self.register_and_login()
        self.assertEqual(200, self.call(BFF_URL, '/logout')[0])
        self.assertEqual(204, self.call(USER_URL, '/api/users/validate', token=token)[0])
        status, headers, _ = self.call(BFF_URL, '/logout', 'POST')
        self.assertEqual(303, status)
        self.assertEqual('/scooter-bff/login.jsp', headers['Location'])
        self.assertEqual(401, self.call(USER_URL, '/api/users/validate', token=token)[0])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bff-url', required=True)
    parser.add_argument('--user-url', required=True)
    args = parser.parse_args()
    BFF_URL, USER_URL = args.bff_url.rstrip('/'), args.user_url.rstrip('/')
    unittest.main(argv=['check-user-integration'], verbosity=2)
