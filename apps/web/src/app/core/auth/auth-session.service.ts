import { computed, Injectable, signal } from '@angular/core';

export const AUTH_TOKEN_STORAGE_KEY = 'bet-control.access-token';

const BASE64_URL_SEGMENT = /^[A-Za-z0-9_-]+$/u;

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly accessToken = signal<string | null>(null);

  readonly isAuthenticated = computed(() => this.accessToken() !== null);

  constructor() {
    this.restoreStoredSession();
  }

  getAccessToken(): string | null {
    const token = this.accessToken();
    if (token === null) {
      return null;
    }

    if (!isLocallyValidJwt(token)) {
      this.clear();
      return null;
    }

    return token;
  }

  hasValidSession(): boolean {
    return this.getAccessToken() !== null;
  }

  establish(accessToken: string): void {
    if (!isLocallyValidJwt(accessToken)) {
      this.clear();
      return;
    }

    try {
      globalThis.localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, accessToken);
      this.accessToken.set(accessToken);
    } catch {
      this.clear();
    }
  }

  clear(): void {
    try {
      globalThis.localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
    } catch {
      // A storage failure must not leave the in-memory session authenticated.
    } finally {
      this.accessToken.set(null);
    }
  }

  private restoreStoredSession(): void {
    let storedToken: string | null = null;

    try {
      storedToken = globalThis.localStorage.getItem(AUTH_TOKEN_STORAGE_KEY);
    } catch {
      storedToken = null;
    }

    if (storedToken !== null && isLocallyValidJwt(storedToken)) {
      this.accessToken.set(storedToken);
      return;
    }

    if (storedToken !== null) {
      this.clear();
    }
  }
}

function isLocallyValidJwt(token: string): boolean {
  const segments = token.split('.');
  if (segments.length !== 3 || segments.some((segment) => segment.length === 0)) {
    return false;
  }

  if (segments.some((segment) => !isValidBase64UrlSegment(segment))) {
    return false;
  }

  try {
    const header = parseJsonObject(decodeBase64Url(segments[0]));
    const payload = parseJsonObject(decodeBase64Url(segments[1]));
    decodeBase64Url(segments[2]);

    if (header === null || payload === null) {
      return false;
    }

    const expiration = (payload as { exp?: unknown }).exp;
    return typeof expiration === 'number'
      && Number.isFinite(expiration)
      && expiration > Math.floor(Date.now() / 1000);
  } catch {
    return false;
  }
}

function isValidBase64UrlSegment(segment: string): boolean {
  return BASE64_URL_SEGMENT.test(segment) && segment.length % 4 !== 1;
}

function parseJsonObject(value: string): Record<string, unknown> | null {
  const parsed = JSON.parse(value) as unknown;
  return parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)
    ? parsed as Record<string, unknown>
    : null;
}

function decodeBase64Url(value: string): string {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const padding = '='.repeat((4 - (base64.length % 4)) % 4);
  return globalThis.atob(base64 + padding);
}
