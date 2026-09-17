import { inject, Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { GatewayHttpClient } from '../http/gateway-http-client';
import {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
} from './auth.models';
import { AuthSessionService } from './auth-session.service';

@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly gateway = inject(GatewayHttpClient);
  private readonly session = inject(AuthSessionService);

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.gateway.request<LoginResponse>('POST', '/auth/login', { body: request }).pipe(
      tap((response) => this.session.establish(response.accessToken)),
    );
  }

  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.gateway.request<RegisterResponse>('POST', '/auth/register', { body: request });
  }
}
