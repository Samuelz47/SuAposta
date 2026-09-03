import { VERSION } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import angularJson from '../../angular.json';
import packageJson from '../../package.json';
import packageLockJson from '../../package-lock.json';
import { AppComponent } from './app.component';
import { appConfig } from './app.config';

type JsonRecord = Record<string, unknown>;

function asRecord(value: unknown): JsonRecord {
  return value as JsonRecord;
}

function hasPropertyDeep(value: unknown, propertyName: string): boolean {
  if (Array.isArray(value)) {
    return value.some((item) => hasPropertyDeep(item, propertyName));
  }

  if (value === null || typeof value !== 'object') {
    return false;
  }

  return Object.entries(value).some(([key, nestedValue]) =>
    key === propertyName || hasPropertyDeep(nestedValue, propertyName),
  );
}

const packageManifest = asRecord(packageJson);
const packageLockManifest = asRecord(packageLockJson);
const angularConfig = asRecord(angularJson);

const dependencies = {
  ...asRecord(packageManifest['dependencies']),
  ...asRecord(packageManifest['devDependencies']),
};

describe('Task 8.1 frontend configuration', () => {
  it('should use Angular major version 18', () => {
    expect(VERSION.major).toBe('18');
  });

  it('should support standalone component bootstrap without an NgModule', async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [...appConfig.providers],
    }).compileComponents();

    expect(TestBed.createComponent(AppComponent).componentInstance).toBeTruthy();
  });

  it('should protect strictNullChecks at test compile time', () => {
    // The compile-time sentinel below makes strictNullChecks observable: the
    // suite build fails if this strict TypeScript check is disabled. The
    // effective strict=true flag is verified separately from the JSONC config.
    expect(true).toBeTrue();
  });

  it('should use SCSS and the Karma runner in the Angular project configuration', () => {
    const webProject = asRecord(asRecord(angularConfig['projects'])['web']);
    const componentSchematic = asRecord(asRecord(webProject['schematics'])['@schematics/angular:component']);
    const buildOptions = asRecord(asRecord(webProject['architect'])['build']);
    const testTarget = asRecord(asRecord(webProject['architect'])['test']);
    const testOptions = asRecord(testTarget['options']);

    expect(componentSchematic['style']).toBe('scss');
    expect(asRecord(buildOptions['options'])['inlineStyleLanguage']).toBe('scss');
    expect(testTarget['builder']).toContain('karma');
    expect(testOptions['tsConfig']).toBe('tsconfig.spec.json');
  });

  it('should use npm and retain a package lock', () => {
    const cliConfig = asRecord(angularConfig['cli']);

    expect(cliConfig['packageManager']).toBe('npm');
    expect(packageLockManifest['lockfileVersion']).toEqual(jasmine.any(Number));
    expect(packageLockManifest['packages']).toEqual(jasmine.any(Object));
  });

  it('should not introduce a prohibited global state-management dependency', () => {
    const prohibited = Object.keys(dependencies).filter((name) =>
      name.startsWith('@ngrx/') ||
      name === 'redux' ||
      name.startsWith('redux-') ||
      name === 'akita' ||
      name.startsWith('@datorama/akita'),
    );

    expect(prohibited).toEqual([]);
  });

  it('should not introduce the Angular SSR runtime dependency', () => {
    const webProject = asRecord(asRecord(angularConfig['projects'])['web']);
    const architect = asRecord(webProject['architect']);

    expect(dependencies['@angular/ssr']).toBeUndefined();
    expect(architect['server']).toBeUndefined();
    expect(architect['prerender']).toBeUndefined();
  });

  it('should not configure an Angular development proxy', () => {
    expect(hasPropertyDeep(angularConfig, 'proxyConfig')).toBeFalse();
  });
});

// These compile-time sentinels make the strictness contract observable without
// adding a production-only type or configuration seam for the test.
function acceptsString(value: string): string {
  return value;
}

const possiblyUndefined: string | undefined = undefined;
// @ts-expect-error strictNullChecks must reject an undefined value here.
acceptsString(possiblyUndefined);
