import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { appConfig } from './app.config';

const structuralRoutes = [
  { url: '/login', path: 'login' },
  { url: '/register', path: 'register' },
  { url: '/dashboard', path: 'dashboard' },
  { url: '/bets', path: 'bets' },
];

function configureApplicationRouter(): void {
  TestBed.configureTestingModule({
    providers: [...(appConfig.providers ?? [])],
  });
}

function hasLazyBoundary(route: {
  loadChildren?: unknown;
  loadComponent?: unknown;
} | null): boolean {
  if (route === null) {
    return false;
  }

  return typeof route.loadChildren === 'function' || typeof route.loadComponent === 'function';
}

function deepestActivatedSnapshot(snapshot: ActivatedRouteSnapshot): ActivatedRouteSnapshot {
  let current = snapshot;

  while (current.firstChild) {
    current = current.firstChild;
  }

  return current;
}

function activatedPathHasLazyBoundary(snapshot: ActivatedRouteSnapshot): boolean {
  let current: ActivatedRouteSnapshot | null = snapshot;

  while (current) {
    if (hasLazyBoundary(current.routeConfig)) {
      return true;
    }

    current = current.parent;
  }

  return false;
}

describe('Task 8.1 structural routes', () => {
  for (const route of structuralRoutes) {
    it(`should resolve a structural page for ${route.url}`, async () => {
      configureApplicationRouter();
      const harness = await RouterTestingHarness.create();

      await harness.navigateByUrl(route.url);

      expect(TestBed.inject(Router).url).toBe(route.url);
      expect(harness.routeNativeElement).not.toBeNull();
    });
  }

  it('should redirect the root URL to the dashboard through Angular Router', async () => {
    configureApplicationRouter();
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/');

    expect(TestBed.inject(Router).url).toBe('/dashboard');
    expect(harness.routeNativeElement).not.toBeNull();
  });

  it('should redirect an unknown URL to the dashboard through Angular Router', async () => {
    configureApplicationRouter();
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/route-that-does-not-exist');

    expect(TestBed.inject(Router).url).toBe('/dashboard');
    expect(harness.routeNativeElement).not.toBeNull();
  });

  it('should keep every structural page behind a lazy-loading boundary', async () => {
    configureApplicationRouter();
    const harness = await RouterTestingHarness.create();
    const router = TestBed.inject(Router);

    for (const route of structuralRoutes) {
      await harness.navigateByUrl(route.url);

      const activeLeaf = deepestActivatedSnapshot(router.routerState.snapshot.root);

      expect(activatedPathHasLazyBoundary(activeLeaf))
        .withContext(`Expected ${route.url} (${route.path}) to have a loadComponent or loadChildren boundary`)
        .toBeTrue();
    }
  });
});
