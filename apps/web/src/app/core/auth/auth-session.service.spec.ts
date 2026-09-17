import { AUTH_TOKEN_STORAGE_KEY, AuthSessionService } from './auth-session.service';

function encodeBase64Url(value: string): string {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/u, '');
}

function jwtToken(
  header = '{"alg":"HS256","typ":"JWT"}',
  payload = JSON.stringify({
    sub: 'b40da580-a017-4a11-bd42-c67aa6409166',
    exp: Math.floor(Date.now() / 1000) + 3600,
  }),
  signature = 'synthetic-signature',
): string {
  return `${encodeBase64Url(header)}.${encodeBase64Url(payload)}.${signature}`;
}

function expectStoredTokenToBeRejected(token: string): void {
  localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token);

  const session = new AuthSessionService();

  expect(session.hasValidSession()).toBeFalse();
  expect(localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBeNull();
}

describe('AuthSessionService local JWT validation', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('should_reject_a_token_with_an_invalid_base64url_header', () => {
    const [, payload, signature] = jwtToken().split('.');
    expectStoredTokenToBeRejected(`%%%.${payload}.${signature}`);
  });

  it('should_reject_a_token_with_invalid_header_json', () => {
    expectStoredTokenToBeRejected(jwtToken('not-json'));
  });

  it('should_reject_a_token_with_an_invalid_base64url_payload', () => {
    const valid = jwtToken();
    const [, , signature] = valid.split('.');
    expectStoredTokenToBeRejected(`${valid.split('.')[0]}.%%%.${signature}`);
  });

  it('should_reject_a_token_with_invalid_payload_json', () => {
    const valid = jwtToken();
    const [header, , signature] = valid.split('.');
    expectStoredTokenToBeRejected(`${header}.${encodeBase64Url('not-json')}.${signature}`);
  });

  it('should_reject_a_token_with_an_invalid_base64url_signature', () => {
    expectStoredTokenToBeRejected(`${jwtToken().split('.').slice(0, 2).join('.')}.%%%`);
  });

  it('should_reject_a_token_with_missing_or_invalid_exp', () => {
    expectStoredTokenToBeRejected(jwtToken(
      undefined,
      JSON.stringify({ sub: 'b40da580-a017-4a11-bd42-c67aa6409166' }),
    ));

    expectStoredTokenToBeRejected(jwtToken(
      undefined,
      JSON.stringify({ exp: 'future' }),
    ));
  });

  it('should_reject_an_expired_token', () => {
    expectStoredTokenToBeRejected(jwtToken(
      undefined,
      JSON.stringify({ exp: Math.floor(Date.now() / 1000) - 1 }),
    ));
  });

  it('should_accept_a_valid_synthetic_jwt', () => {
    const token = jwtToken();
    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token);

    const session = new AuthSessionService();

    expect(session.hasValidSession()).toBeTrue();
    expect(session.getAccessToken()).toBe(token);
  });
});
