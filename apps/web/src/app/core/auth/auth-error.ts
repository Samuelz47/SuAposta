import { HttpErrorResponse } from '@angular/common/http';

export type AuthErrorContext = 'login' | 'register';

const LOGIN_INVALID_CREDENTIALS = 'Invalid email or password.';
const LOGIN_GENERIC_ERROR = 'Login could not be completed.';
const REGISTER_INVALID_REQUEST = 'Please check the registration details.';
const REGISTER_DUPLICATE_EMAIL = 'An account with this email already exists.';
const REGISTER_GENERIC_ERROR = 'Registration could not be completed.';

export function safeAuthErrorMessage(error: unknown, context: AuthErrorContext): string {
  const status = error instanceof HttpErrorResponse ? error.status : null;

  if (context === 'login') {
    return status === 401 ? LOGIN_INVALID_CREDENTIALS : LOGIN_GENERIC_ERROR;
  }

  if (status === 400) {
    return REGISTER_INVALID_REQUEST;
  }

  if (status === 409) {
    return REGISTER_DUPLICATE_EMAIL;
  }

  return REGISTER_GENERIC_ERROR;
}
