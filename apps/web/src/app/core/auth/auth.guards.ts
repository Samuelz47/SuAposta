import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthSessionService } from './auth-session.service';

export const authenticatedGuard: CanActivateFn = () => {
  const session = inject(AuthSessionService);
  const router = inject(Router);

  return session.hasValidSession() ? true : router.createUrlTree(['/login']);
};

export const publicOnlyGuard: CanActivateFn = () => {
  const session = inject(AuthSessionService);
  const router = inject(Router);

  return session.hasValidSession() ? router.createUrlTree(['/dashboard']) : true;
};
