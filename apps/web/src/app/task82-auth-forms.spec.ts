import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TestBed } from '@angular/core/testing';

import { appConfig } from './app.config';

const LOGIN_URL = 'http://localhost:8080/auth/login';
const REGISTER_URL = 'http://localhost:8080/auth/register';
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

const REGISTER_RESPONSE = {
  id: 'b40da580-a017-4a11-bd42-c67aa6409166',
  name: 'QA User',
  email: TEST_EMAIL,
  createdAt: '2026-09-16T12:00:00Z'
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

function findField(form: HTMLFormElement, name: string, type: string): HTMLInputElement | null {
  const named = form.querySelector<HTMLInputElement>(`[name="${name}"]`);
  if (named) {
    return named;
  }

  const typed = form.querySelector<HTMLInputElement>(`input[type="${type}"]`);
  if (typed) {
    return typed;
  }

  const label = Array.from(form.querySelectorAll<HTMLLabelElement>('label'))
    .find((candidate) => candidate.textContent?.toLowerCase().includes(name));
  const labelledControl = label?.htmlFor
    ? form.querySelector<HTMLInputElement>(`#${CSS.escape(label.htmlFor)}`)
    : null;
  return labelledControl;
}

function findForm(harness: RouterTestingHarness): HTMLFormElement | null {
  const page = harness.routeNativeElement;
  expect(page).not.toBeNull();
  return page?.querySelector<HTMLFormElement>('form') ?? null;
}

function fillField(form: HTMLFormElement, name: string, type: string, value: string): boolean {
  const field = findField(form, name, type);
  expect(field).not.toBeNull();
  if (!field) {
    return false;
  }

  field.value = value;
  field.dispatchEvent(new Event('input', { bubbles: true }));
  field.dispatchEvent(new Event('change', { bubbles: true }));
  field.dispatchEvent(new Event('blur', { bubbles: true }));
  return true;
}

function submitControl(form: HTMLFormElement): HTMLElement | null {
  const control = form.querySelector<HTMLElement>(
    'button[type="submit"], button:not([type]), input[type="submit"]',
  );
  expect(control).not.toBeNull();
  control?.click();
  return control;
}

function assertLoadingState(form: HTMLFormElement, control: HTMLElement | null): void {
  expect(control?.getAttribute('disabled') !== null || form.getAttribute('aria-busy') === 'true')
    .toBeTrue();
}

function storedValues(): string[] {
  return Object.values(localStorage);
}

function containsStored(value: string): boolean {
  return storedValues().some((storedValue) => storedValue.includes(value));
}

function userAlert(harness: RouterTestingHarness): HTMLElement | null {
  const root = harness.fixture.nativeElement as HTMLElement;
  return root.querySelector<HTMLElement>('[role="alert"], [aria-live]');
}

describe('Task 8.2 auth forms', () => {
  beforeEach(() => {
    localStorage.clear();
    configureTask82TestBed();
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    localStorage.clear();
  });

  it('should_expose_semantic_login_email_and_password_fields_without_password_confirmation', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login');
    const form = findForm(harness);

    expect(form).not.toBeNull();
    if (!form) {
      return;
    }

    const email = findField(form, 'email', 'email');
    const password = findField(form, 'password', 'password');
    expect(email).not.toBeNull();
    expect(password).not.toBeNull();
    expect(email?.type).toBe('email');
    expect(password?.type).toBe('password');
    expect(form.querySelector('[name="passwordConfirmation"]')).toBeNull();
  });

  it('should_expose_semantic_registration_name_email_and_password_fields_without_extra_credentials', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/register');
    const form = findForm(harness);

    expect(form).not.toBeNull();
    if (!form) {
      return;
    }

    expect(findField(form, 'name', 'text')).not.toBeNull();
    expect(findField(form, 'email', 'email')).not.toBeNull();
    expect(findField(form, 'password', 'password')).not.toBeNull();
    expect(form.querySelector('[name="passwordConfirmation"]')).toBeNull();
  });

  it('should_not_submit_login_when_required_fields_are_missing', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login');
    const form = findForm(harness);
    expect(form).not.toBeNull();
    if (!form) {
      return;
    }

    submitControl(form);

    const requests = TestBed.inject(HttpTestingController).match(
      (request) => request.method === 'POST' && request.url === LOGIN_URL,
    );
    expect(requests.length).toBe(0);
  });

  it('should_not_submit_registration_when_required_fields_are_missing', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/register');
    const form = findForm(harness);
    expect(form).not.toBeNull();
    if (!form) {
      return;
    }

    submitControl(form);

    const requests = TestBed.inject(HttpTestingController).match(
      (request) => request.method === 'POST' && request.url === REGISTER_URL,
    );
    expect(requests.length).toBe(0);
  });

  it('should_expose_login_loading_and_prevent_duplicate_submits_while_request_is_pending', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login');
    const form = findForm(harness);
    expect(form).not.toBeNull();
    if (!form) {
      return;
    }

    const fieldsReady = fillField(form, 'email', 'email', TEST_EMAIL)
      && fillField(form, 'password', 'password', TEST_PASSWORD);
    expect(fieldsReady).toBeTrue();
    if (!fieldsReady) {
      return;
    }

    const control = submitControl(form);
    control?.click();

    const http = TestBed.inject(HttpTestingController);
    const requests = http.match((request) => request.method === 'POST' && request.url === LOGIN_URL);
    expect(requests.length).toBe(1);
    assertLoadingState(form, control);
    requests.forEach((request) => request.flush(LOGIN_RESPONSE));
    await harness.fixture.whenStable();
  });

  it('should_expose_registration_loading_and_prevent_duplicate_submits_while_request_is_pending', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/register');
    const form = findForm(harness);
    expect(form).not.toBeNull();
    if (!form) {
      return;
    }

    const fieldsReady = fillField(form, 'name', 'text', 'QA User')
      && fillField(form, 'email', 'email', TEST_EMAIL)
      && fillField(form, 'password', 'password', TEST_PASSWORD);
    expect(fieldsReady).toBeTrue();
    if (!fieldsReady) {
      return;
    }

    const control = submitControl(form);
    control?.click();

    const http = TestBed.inject(HttpTestingController);
    const requests = http.match((request) => request.method === 'POST' && request.url === REGISTER_URL);
    expect(requests.length).toBe(1);
    assertLoadingState(form, control);
    requests.forEach((request) => request.flush(REGISTER_RESPONSE, { status: 201, statusText: 'Created' }));
    await harness.fixture.whenStable();
  });

  it('should_send_only_the_documented_login_request_and_redirect_after_success', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login');
    const form = findForm(harness);
    expect(form).not.toBeNull();
    if (!form) {
      return;
    }

    const fieldsReady = fillField(form, 'email', 'email', TEST_EMAIL)
      && fillField(form, 'password', 'password', TEST_PASSWORD);
    expect(fieldsReady).toBeTrue();
    if (!fieldsReady) {
      return;
    }
    submitControl(form);

    const http = TestBed.inject(HttpTestingController);
    const requests = http.match((request) => request.method === 'POST' && request.url === LOGIN_URL);
    expect(requests.length).toBe(1);
    if (requests.length !== 1) {
      return;
    }

    const requestBody = requests[0].request.body as Record<string, unknown>;
    expect(Object.keys(requestBody).sort()).toEqual(['email', 'password']);
    expect(requestBody).toEqual({ email: TEST_EMAIL, password: TEST_PASSWORD });
    expect(requests[0].request.headers.has('Authorization')).toBeFalse();
    expect(requests[0].request.headers.has('X-User-Id')).toBeFalse();

    requests[0].flush(LOGIN_RESPONSE);
    await harness.fixture.whenStable();
    harness.fixture.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/dashboard');
    expect(containsStored(TEST_TOKEN)).toBeTrue();
    expect(containsStored(TEST_PASSWORD)).toBeFalse();
  });

  it('should_send_only_the_documented_registration_request_without_creating_a_session', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/register');
    const form = findForm(harness);
    expect(form).not.toBeNull();
    if (!form) {
      return;
    }

    const fieldsReady = fillField(form, 'name', 'text', 'QA User')
      && fillField(form, 'email', 'email', TEST_EMAIL)
      && fillField(form, 'password', 'password', TEST_PASSWORD);
    expect(fieldsReady).toBeTrue();
    if (!fieldsReady) {
      return;
    }
    submitControl(form);

    const http = TestBed.inject(HttpTestingController);
    const requests = http.match((request) => request.method === 'POST' && request.url === REGISTER_URL);
    expect(requests.length).toBe(1);
    if (requests.length !== 1) {
      return;
    }

    const requestBody = requests[0].request.body as Record<string, unknown>;
    expect(Object.keys(requestBody).sort()).toEqual(['email', 'name', 'password']);
    expect(requestBody).toEqual({ name: 'QA User', email: TEST_EMAIL, password: TEST_PASSWORD });
    expect(requests[0].request.headers.has('Authorization')).toBeFalse();
    expect(requests[0].request.headers.has('X-User-Id')).toBeFalse();

    requests[0].flush(REGISTER_RESPONSE, { status: 201, statusText: 'Created' });
    await harness.fixture.whenStable();
    harness.fixture.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/login');
    expect(containsStored(TEST_TOKEN)).toBeFalse();
  });

  it('should_stay_on_login_and_show_safe_feedback_for_invalid_credentials_without_a_redirect_loop', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login');
    const form = findForm(harness);
    expect(form).not.toBeNull();
    if (!form) {
      return;
    }

    const fieldsReady = fillField(form, 'email', 'email', TEST_EMAIL)
      && fillField(form, 'password', 'password', TEST_PASSWORD);
    expect(fieldsReady).toBeTrue();
    if (!fieldsReady) {
      return;
    }
    submitControl(form);

    const http = TestBed.inject(HttpTestingController);
    const requests = http.match((request) => request.method === 'POST' && request.url === LOGIN_URL);
    expect(requests.length).toBe(1);
    if (requests.length !== 1) {
      return;
    }

    requests[0].flush(
      { status: 401, error: 'invalid credentials' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await harness.fixture.whenStable();
    harness.fixture.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/login');
    expect(userAlert(harness)).not.toBeNull();
    expect(http.match((request) => request.method === 'POST' && request.url === LOGIN_URL).length)
      .toBe(0);
    expect(containsStored(TEST_TOKEN)).toBeFalse();
  });

  for (const status of [400, 409]) {
    it(`should_stay_on_register_and_show_safe_feedback_for_registration_${status}_without_a_session`, async () => {
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/register');
      const form = findForm(harness);
      expect(form).not.toBeNull();
      if (!form) {
        return;
      }

      const fieldsReady = fillField(form, 'name', 'text', 'QA User')
        && fillField(form, 'email', 'email', TEST_EMAIL)
        && fillField(form, 'password', 'password', TEST_PASSWORD);
      expect(fieldsReady).toBeTrue();
      if (!fieldsReady) {
        return;
      }
      submitControl(form);

      const http = TestBed.inject(HttpTestingController);
      const requests = http.match((request) => request.method === 'POST' && request.url === REGISTER_URL);
      expect(requests.length).toBe(1);
      if (requests.length !== 1) {
        return;
      }

      requests[0].flush(
        { status, message: 'safe documented message' },
        { status, statusText: status === 400 ? 'Bad Request' : 'Conflict' },
      );
      await harness.fixture.whenStable();
      harness.fixture.detectChanges();

      expect(TestBed.inject(Router).url).toBe('/register');
      expect(userAlert(harness)).not.toBeNull();
      expect(http.match((request) => request.method === 'POST' && request.url === REGISTER_URL).length)
        .toBe(0);
      expect(containsStored(TEST_TOKEN)).toBeFalse();
    });
  }

  it('should_not_expose_internal_error_details_in_authentication_feedback', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/login');
    const form = findForm(harness);
    expect(form).not.toBeNull();
    if (!form) {
      return;
    }

    const fieldsReady = fillField(form, 'email', 'email', TEST_EMAIL)
      && fillField(form, 'password', 'password', TEST_PASSWORD);
    expect(fieldsReady).toBeTrue();
    if (!fieldsReady) {
      return;
    }
    submitControl(form);

    const request = TestBed.inject(HttpTestingController).expectOne({ method: 'POST', url: LOGIN_URL });
    request.flush(
      {
        message: 'java.lang.IllegalStateException: jdbc:postgresql://auth-service:5432/auth_db',
        stackTrace: 'at com.suaposta.auth.AuthController(AuthController.java:42)'
      },
      { status: 500, statusText: 'Internal Server Error' },
    );
    await harness.fixture.whenStable();
    harness.fixture.detectChanges();

    expect(userAlert(harness)).not.toBeNull();
    const alertText = userAlert(harness)?.textContent ?? '';
    expect(alertText).not.toContain('java.lang');
    expect(alertText).not.toContain('AuthController');
    expect(alertText).not.toContain('postgresql');
    expect(alertText).not.toContain('auth-service');
    expect(alertText).not.toContain('5432');
  });
});
