import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { GATEWAY_BASE_URL } from '../http/gateway-base-url';
import { AuthSessionService } from './auth-session.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(AuthSessionService);
  const router = inject(Router);
  const gatewayBaseUrl = inject(GATEWAY_BASE_URL).replace(/\/+$/, '');
  const gatewayRequest = isGatewayRequest(request.url, gatewayBaseUrl);
  const publicAuthRequest = isPublicAuthRequest(request.method, request.url, gatewayBaseUrl);
  const token = gatewayRequest && !publicAuthRequest ? session.getAccessToken() : null;

  let outgoingRequest = request.clone({
    headers: request.headers.delete('X-User-Id'),
  });

  if (gatewayRequest && publicAuthRequest) {
    outgoingRequest = outgoingRequest.clone({
      headers: outgoingRequest.headers.delete('Authorization'),
    });
  } else if (token !== null) {
    outgoingRequest = outgoingRequest.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
  }

  return next(outgoingRequest).pipe(
    catchError((error: unknown) => {
      if (gatewayRequest && !publicAuthRequest && token !== null
        && error instanceof HttpErrorResponse && error.status === 401) {
        session.clear();
        void router.navigateByUrl('/login');
      }

      return throwError(() => error);
    }),
  );
};

function isGatewayRequest(url: string, gatewayBaseUrl: string): boolean {
  return url === gatewayBaseUrl || url.startsWith(`${gatewayBaseUrl}/`);
}

function isPublicAuthRequest(method: string, url: string, gatewayBaseUrl: string): boolean {
  if (method.toUpperCase() !== 'POST' || !isGatewayRequest(url, gatewayBaseUrl)) {
    return false;
  }

  const path = url.slice(gatewayBaseUrl.length).split('?')[0];
  return path === '/auth/login' || path === '/auth/register';
}
