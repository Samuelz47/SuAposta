import { InjectionToken } from '@angular/core';

export const GATEWAY_BASE_URL = new InjectionToken<string>('Gateway base URL', {
  providedIn: 'root',
  factory: () => 'http://localhost:8080'
});
