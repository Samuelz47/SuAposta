import { HttpParams } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { appConfig } from '../../app.config';
import { GATEWAY_BASE_URL } from './gateway-base-url';
import { GatewayHttpClient } from './gateway-http-client';

// IMPLEMENTER-ADDED SUPPORTING TESTS. The protected Task81 specs are unchanged.
describe('Gateway HTTP boundary', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()]
    });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
  });

  it('should_send_a_request_to_the_default_gateway_without_identity_headers_when_given_a_relative_path', () => {
    const body = { probe: 'boundary' };
    let response: { accepted: boolean } | undefined;

    TestBed.inject(GatewayHttpClient)
      .request<{ accepted: boolean }>('POST', '/auth/login', { body })
      .subscribe((value) => response = value);

    const request = TestBed.inject(HttpTestingController)
      .expectOne({ method: 'POST', url: 'http://localhost:8080/auth/login' });
    expect(request.request.body).toEqual(body);
    expect(request.request.headers.has('X-User-Id')).toBeFalse();
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({ accepted: true });
    expect(response).toEqual({ accepted: true });
  });

  it('should_change_the_http_target_when_gateway_configuration_is_overridden', () => {
    TestBed.overrideProvider(GATEWAY_BASE_URL, { useValue: 'https://gateway.example.test' });

    TestBed.inject(GatewayHttpClient).request<unknown>('POST', '/auth/login').subscribe();

    const request = TestBed.inject(HttpTestingController)
      .expectOne({ method: 'POST', url: 'https://gateway.example.test/auth/login' });
    expect(request.request.headers.has('X-User-Id')).toBeFalse();
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({});
  });

  for (const { base, path } of [
    { base: 'http://localhost:8080', path: 'auth/login' },
    { base: 'http://localhost:8080/', path: '/auth/login' },
    { base: 'http://localhost:8080/', path: 'auth/login' }
  ]) {
    it(`should_join_with_one_slash_when_base_is_${base}_and_path_is_${path}`, () => {
      TestBed.overrideProvider(GATEWAY_BASE_URL, { useValue: base });

      TestBed.inject(GatewayHttpClient).request<unknown>('POST', path).subscribe();

      const request = TestBed.inject(HttpTestingController)
        .expectOne({ method: 'POST', url: 'http://localhost:8080/auth/login' });
      expect(request.request.url).toBe('http://localhost:8080/auth/login');
      request.flush({});
    });
  }

  it('should_preserve_the_path_content_when_normalizing_the_join', () => {
    TestBed.overrideProvider(GATEWAY_BASE_URL, { useValue: 'https://gateway.example.test/' });

    TestBed.inject(GatewayHttpClient)
      .request<unknown>('GET', '/Probe//Case%20Sensitive?value=A%2FB').subscribe();

    const request = TestBed.inject(HttpTestingController)
      .expectOne('https://gateway.example.test/Probe//Case%20Sensitive?value=A%2FB');
    expect(request.request.url).toBe('https://gateway.example.test/Probe//Case%20Sensitive?value=A%2FB');
    request.flush({});
  });

  it('should_forward_query_parameters_when_supplied_to_the_gateway_request', () => {
    const params = new HttpParams().set('value', 'A B').append('value', 'C');

    TestBed.inject(GatewayHttpClient).request<unknown>('GET', '/probe', { params }).subscribe();

    const request = TestBed.inject(HttpTestingController)
      .expectOne({ method: 'GET', url: 'http://localhost:8080/probe?value=A%20B&value=C' });
    expect(request.request.params.getAll('value')).toEqual(['A B', 'C']);
    request.flush({});
  });

  for (const path of ['http://localhost:8081/auth/login', '//betting-service:8082/bets']) {
    it(`should_reject_a_direct_target_without_sending_a_request_when_path_is_${path}`, () => {
      const client = TestBed.inject(GatewayHttpClient);

      expect(() => client.request<unknown>('GET', path).subscribe())
        .toThrowError('Gateway requests require a relative API path.');
      TestBed.inject(HttpTestingController).expectNone(() => true);
    });
  }
});
