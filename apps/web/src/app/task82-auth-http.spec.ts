import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TestBed } from '@angular/core/testing';

import { appConfig } from './app.config';
import { GatewayHttpClient } from './core/http/gateway-http-client';

const LOGIN_URL = 'http://localhost:8080/auth/login';
const PROBE_URL = 'http://localhost:8080/probe';
const TEST_EMAIL = 'qa.task82@example.com';
const TEST_PASSWORD = 'StrongPassword123';
const TEST_TOKEN = syntheticJwt(Math.floor(Date.now() / 1000) + 3600);

const LOGIN_RESPONSE = {
  accessToken: TEST_TOKEN,
  tokenType: 'Bearer',
  expiresIn: 3600,
  user: {
    id: 'b40da580-a017-4a11-bd42-c67aa6409166',
    name: 'QA User',
    email: TEST_EMAIL
  }
};

function syntheticJwt(exp: number): string {
  const encode = (value: string): string =>
    btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/u, '');

  return [
    encode('{"alg":"HS256","typ":"JWT"}'),
    encode(JSON.stringify({ sub: 'b40da580-a017-4a11-bd42-c67aa6409166', exp })),
    'synthetic-signature'
  ].join('.');
}

function configureTask82TestBed(): void {
  TestBed.configureTestingModule({
    providers: [...(appConfig.providers ?? []), provideHttpClientTesting()]
  });
}

async function establishSession(
  harness: RouterTestingHarness,
  http: HttpTestingController,
): Promise<string | null> {
  await harness.navigateByUrl('/login');
  const form = harness.routeNativeElement?.querySelector<HTMLFormElement>('form') ?? null;
  expect(form).not.toBeNull();
  if (!form) {
    return null;
  }

  const email = form.querySelector<HTMLInputElement>('[name="email"]')
    ?? form.querySelector<HTMLInputElement>('input[type="email"]');
  const password = form.querySelector<HTMLInputElement>('[name="password"]')
    ?? form.querySelector<HTMLInputElement>('input[type="password"]');
  const submit = form.querySelector<HTMLElement>(
    'button[type="submit"], button:not([type]), input[type="submit"]',
  );
  expect(email).not.toBeNull();
  expect(password).not.toBeNull();
  expect(submit).not.toBeNull();
  if (!email || !password || !submit) {
    return null;
  }

  for (const [field, value] of [[email, TEST_EMAIL], [password, TEST_PASSWORD]] as const) {
    field.value = value;
    field.dispatchEvent(new Event('input', { bubbles: true }));
    field.dispatchEvent(new Event('change', { bubbles: true }));
  }
  submit.click();

  const requests = http.match((request) => request.method === 'POST' && request.url === LOGIN_URL);
  expect(requests.length).toBe(1);
  if (requests.length === 0) {
    return null;
  }
  requests.forEach((request) => request.flush(LOGIN_RESPONSE));
  await harness.fixture.whenStable();

  const storedToken = Object.values(localStorage).find((value) => value.includes(TEST_TOKEN));
  expect(storedToken).toBeDefined();
  return storedToken;
}

describe('Task 8.2 authenticated Gateway HTTP boundary', () => {
  beforeEach(() => {
    localStorage.clear();
    configureTask82TestBed();
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    localStorage.clear();
  });

  it('should_not_send_authorization_or_client_user_identity_without_a_session', () => {
    let response: unknown;
    TestBed.inject(GatewayHttpClient).request<unknown>('GET', '/probe')
      .subscribe((value) => response = value);

    const request = TestBed.inject(HttpTestingController)
      .expectOne({ method: 'GET', url: PROBE_URL });
    expect(request.request.headers.has('Authorization')).toBeFalse();
    expect(request.request.headers.has('X-User-Id')).toBeFalse();
    request.flush({ ok: true });
    expect(response).toEqual({ ok: true });
  });

  it('should_attach_the_bearer_token_through_the_central_http_boundary_without_X_User_Id', async () => {
    const harness = await RouterTestingHarness.create();
    const token = await establishSession(harness, TestBed.inject(HttpTestingController));
    if (!token) {
      return;
    }

    TestBed.inject(GatewayHttpClient).request<unknown>('GET', '/probe').subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne({ method: 'GET', url: PROBE_URL });

    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${TEST_TOKEN}`);
    expect(request.request.headers.has('X-User-Id')).toBeFalse();
    request.flush({ ok: true });
  });

  it('should_clear_the_session_and_redirect_to_login_when_an_authenticated_request_returns_401', async () => {
    const harness = await RouterTestingHarness.create();
    const token = await establishSession(harness, TestBed.inject(HttpTestingController));
    if (!token) {
      return;
    }

    let errorSeen = false;
    TestBed.inject(GatewayHttpClient).request<unknown>('GET', '/probe')
      .subscribe({ error: () => errorSeen = true });
    const request = TestBed.inject(HttpTestingController).expectOne({ method: 'GET', url: PROBE_URL });
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${TEST_TOKEN}`);
    request.flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });
    await harness.fixture.whenStable();
    harness.fixture.detectChanges();

    expect(errorSeen).toBeTrue();
    expect(Object.values(localStorage).some((value) => value.includes(TEST_TOKEN))).toBeFalse();
    expect(TestBed.inject(Router).url).toBe('/login');
  });
});
