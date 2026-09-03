# Bet Control web

Angular 18.2.14 standalone application, generated with Angular CLI 18.2.21.
The Task 8.1 baseline provides a navigation shell and lazy structural pages at
`/login`, `/register`, `/dashboard`, and `/bets`. Root and unknown URLs redirect
to `/dashboard`. These pages currently render headings only.

## Local development

Use Node.js compatible with the locked Angular CLI (Node 22 is used for task
verification) and npm. From the repository root:

```bash
cd apps/web
npm ci
npm start
```

Open `http://localhost:4200`. The frontend connects directly to the Gateway at
`http://localhost:8080` through CORS, with no global API prefix or Angular dev
proxy. The structural pages make no API requests and can be served on their own.
See [the local runtime contract](../../docs/infrastructure.md#local-application-runtime-contract)
for Gateway configuration.

## HTTP boundary and ownership

- `core/http/` owns the `GATEWAY_BASE_URL` injection token and `GatewayHttpClient`.
- `core/layout/` owns the navigation and routed-content shell composed by the root.
- `features/` owns the lazy standalone pages and future feature services.
- `shared/` is reserved for reusable presentation; its README records the
  boundary until shared presentation code is needed.

Future feature services inject `GatewayHttpClient` and call
`request<T>(method, relativePath, options?)`. Options currently support a JSON
request body and Angular `HttpParams`; the result is the typed JSON response
observable. The client normalizes only the joining slash and rejects absolute
or protocol-relative targets. Feature code must use relative API paths, without
hosts, ports, or a global API prefix.

`GATEWAY_BASE_URL` defines the local default once. To replace it, provide
`{ provide: GATEWAY_BASE_URL, useValue: 'https://gateway.example.test' }` in
`app.config.ts`, or use `TestBed.overrideProvider` in tests. Consumers stay
unchanged. This configuration is an Angular DI value, not a shell environment
variable automatically read by the browser.

The boundary adds no identity or authentication headers. Session behavior,
forms, guards, and business API calls belong to subsequent tasks.

## Verification

Chrome must be installed for the headless Karma suite.

```bash
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

The production build is written to `dist/web/`. The three `task81-*.spec.ts`
files are protected human-approved tests. `core/http/gateway-http-client.spec.ts`
contains implementer-added supporting tests using Angular's HTTP testing
backend, with no real network requests.
