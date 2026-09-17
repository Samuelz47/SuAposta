import { HttpErrorResponse } from '@angular/common/http';

import { safeAuthErrorMessage } from './auth-error';

describe('safeAuthErrorMessage', () => {
  it('should_map_login_401_to_safe_useful_feedback', () => {
    const error = new HttpErrorResponse({
      status: 401,
      error: { message: 'invalid credentials' },
    });

    expect(safeAuthErrorMessage(error, 'login')).toBe('Invalid email or password.');
  });

  it('should_map_registration_400_and_409_to_safe_useful_feedback', () => {
    const validationError = new HttpErrorResponse({ status: 400, error: { message: 'bad request' } });
    const duplicateError = new HttpErrorResponse({ status: 409, error: { message: 'duplicate' } });

    expect(safeAuthErrorMessage(validationError, 'register'))
      .toBe('Please check the registration details.');
    expect(safeAuthErrorMessage(duplicateError, 'register'))
      .toBe('An account with this email already exists.');
  });

  it('should_never_expose_arbitrary_infrastructure_messages', () => {
    const error = new HttpErrorResponse({
      status: 500,
      error: {
        message: 'SQLSTATE 08006 connection to db.internal failed',
        stackTrace: 'internal stack details',
      },
    });

    const loginMessage = safeAuthErrorMessage(error, 'login');
    const registrationMessage = safeAuthErrorMessage(error, 'register');

    expect(loginMessage).toBe('Login could not be completed.');
    expect(registrationMessage).toBe('Registration could not be completed.');
    expect(loginMessage).not.toContain('SQLSTATE');
    expect(loginMessage).not.toContain('db.internal');
    expect(registrationMessage).not.toContain('SQLSTATE');
    expect(registrationMessage).not.toContain('db.internal');
  });
});
