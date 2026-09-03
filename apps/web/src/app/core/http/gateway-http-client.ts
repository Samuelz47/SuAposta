import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { GATEWAY_BASE_URL } from './gateway-base-url';

export interface GatewayRequestOptions {
  body?: unknown;
  params?: HttpParams;
}

@Injectable({ providedIn: 'root' })
export class GatewayHttpClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(GATEWAY_BASE_URL);

  request<T>(method: string, path: string, options: GatewayRequestOptions = {}): Observable<T> {
    if (/^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(path)) {
      throw new Error('Gateway requests require a relative API path.');
    }

    const url = `${this.baseUrl.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;
    return this.http.request<T>(method, url, options);
  }
}
