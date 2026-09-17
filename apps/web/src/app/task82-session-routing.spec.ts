import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TestBed } from '@angular/core/testing';

import { appConfig } from './app.config';

const LOGIN_URL = 'http://localhost:8080/auth/login';
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

interface StoredSession {
  key: string;
  token: string;
}

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

function findField(form: HTMLFormElement, name: string, type: string): HTMLInputElement | null {
  return form.querySelector<HTMLInputElement>(`[name="${name}"]`)
    ?? form.querySelector<HTMLInputElement>(`input[type="${type}"]`);
}

function fillLogin(form: HTMLFormElement): boolean {
  const email = findField(form, 'email', 'email');
  const password = findField(form, 'password', 'password');
  expect(email).not.toBeNull();
  expect(password).not.toBeNull();
  if (!email || !password) {
    return false;
  }

  for (const [field, value] of [[email, TEST_EMAIL], [password, TEST_PASSWORD]] as const) {
    field.value = value;
    field.dispatchEvent(new Event('input', { bubbles: true }));
    field.dispatchEvent(new Event('change', { bubbles: true }));
  }
  return true;
}

async function establishSession(
  harness: RouterTestingHarness,
  http: HttpTestingController,
): Promise<StoredSession | null> {
  await harness.navigateByUrl('/login');
  const form = harness.routeNativeElement?.querySelector<HTMLFormElement>('form') ?? null;
  expect(form).not.toBeNull();
  if (!form || !fillLogin(form)) {
    return null;
  }

  const submit = form.querySelector<HTMLElement>(
    'button[type="submit"], button:not([type]), input[type="submit"]',
  );
  expect(submit).not.toBeNull();
  if (!submit) {
    return null;
  }
  submit.click();

  const requests = http.match((request) => request.method === 'POST' && request.url === LOGIN_URL);
  expect(requests.length).toBe(1);
  if (requests.length === 0) {
    return null;
  }
  requests.forEach((request) => request.flush(LOGIN_RESPONSE));
  await harness.fixture.whenStable();

  const entry = Object.entries(localStorage).find(([, value]) => value.includes(TEST_TOKEN));
  expect(entry).not.toBeNull();
  return entry ? { key: entry[0], token: TEST_TOKEN } : null;
}

function logoutAction(harness: RouterTestingHarness): HTMLElement | null {
  const root = harness.fixture.nativeElement as HTMLElement;
  const candidates = Array.from(root.querySelectorAll<HTMLElement>(
    'button, a, [role="button"]',
  ));
  return candidates.find((candidate) => {
    const accessibleText = [
      candidate.textContent ?? '',
      candidate.getAttribute('aria-label') ?? '',
      candidate.getAttribute('title') ?? ''
    ].join(' ').toLowerCase();
    return accessibleText.includes('logout') || accessibleText.includes('sair');
  }) ?? null;
}

describe('Task 8.2 session routing', () => {
  beforeEach(() => {
    localStorage.clear();
    configureTask82TestBed();
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    localStorage.clear();
  });

  it('should_keep_public_login_and_register_routes_accessible_without_a_session', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login');
    expect(TestBed.inject(Router).url).toBe('/login');

    await harness.navigateByUrl('/register');
    expect(TestBed.inject(Router).url).toBe('/register');
  });

  for (const path of ['/dashboard', '/bets']) {
    it(`should_redirect_${path}_to_login_when_the_session_is_missing`, async () => {
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(path);

      expect(TestBed.inject(Router).url).toBe('/login');
    });
  }

  it('should_treat_a_malformed_stored_token_as_unauthenticated_and_clear_it', async () => {
    const harness = await RouterTestingHarness.create();
    const session = await establishSession(harness, TestBed.inject(HttpTestingController));
    if (!session) {
      return;
    }

    localStorage.setItem(session.key, 'not-a-jwt');
    TestBed.resetTestingModule();
    configureTask82TestBed();
    const restoredHarness = await RouterTestingHarness.create();
    await restoredHarness.navigateByUrl('/dashboard');

    expect(TestBed.inject(Router).url).toBe('/login');
    expect(localStorage.getItem(session.key)).toBeNull();
  });

  it('should_treat_an_expired_stored_token_as_unauthenticated_and_clear_it', async () => {
    const harness = await RouterTestingHarness.create();
    const session = await establishSession(harness, TestBed.inject(HttpTestingController));
    if (!session) {
      return;
    }

    localStorage.setItem(session.key, syntheticJwt(Math.floor(Date.now() / 1000) - 60));
    TestBed.resetTestingModule();
    configureTask82TestBed();
    const restoredHarness = await RouterTestingHarness.create();
    await restoredHarness.navigateByUrl('/bets');

    expect(TestBed.inject(Router).url).toBe('/login');
    expect(localStorage.getItem(session.key)).toBeNull();
  });

  it('should_allow_valid_session_navigation_to_dashboard_and_bets', async () => {
    const harness = await RouterTestingHarness.create();
    const session = await establishSession(harness, TestBed.inject(HttpTestingController));
    if (!session) {
      return;
    }

    await harness.navigateByUrl('/dashboard');
    expect(TestBed.inject(Router).url).toBe('/dashboard');
    await harness.navigateByUrl('/bets');
    expect(TestBed.inject(Router).url).toBe('/bets');
  });

  it('should_redirect_an_authenticated_user_from_login_and_register_to_dashboard', async () => {
    const harness = await RouterTestingHarness.create();
    const session = await establishSession(harness, TestBed.inject(HttpTestingController));
    if (!session) {
      return;
    }

    await harness.navigateByUrl('/login');
    expect(TestBed.inject(Router).url).toBe('/dashboard');
    await harness.navigateByUrl('/register');
    expect(TestBed.inject(Router).url).toBe('/dashboard');
  });

  it('should_restore_a_valid_session_from_local_storage_when_the_application_router_is_recreated', async () => {
    const harness = await RouterTestingHarness.create();
    const session = await establishSession(harness, TestBed.inject(HttpTestingController));
    if (!session) {
      return;
    }

    TestBed.resetTestingModule();
    configureTask82TestBed();
    const restoredHarness = await RouterTestingHarness.create();
    await restoredHarness.navigateByUrl('/dashboard');

    expect(TestBed.inject(Router).url).toBe('/dashboard');
    expect(localStorage.getItem(session.key)).toContain(session.token);
  });

  it('should_expose_logout_that_clears_the_local_session_and_redirects_to_login', async () => {
    const harness = await RouterTestingHarness.create();
    const session = await establishSession(harness, TestBed.inject(HttpTestingController));
    if (!session) {
      return;
    }

    const action = logoutAction(harness);
    expect(action).not.toBeNull();
    if (!action) {
      return;
    }

    action.click();
    await harness.fixture.whenStable();
    harness.fixture.detectChanges();

    expect(localStorage.getItem(session.key)).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/login');
  });
});
