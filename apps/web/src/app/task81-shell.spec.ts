import { ComponentFixture, TestBed } from '@angular/core/testing';

import { appConfig } from './app.config';
import { AppComponent } from './app.component';

describe('Task 8.1 application shell', () => {
  let fixture: ComponentFixture<AppComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [...(appConfig.providers ?? [])],
    }).compileComponents();

    fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
  });

  it('should expose global navigation and a routed-content outlet', () => {
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('nav, [role="navigation"]')).not.toBeNull();
    expect(element.querySelector('router-outlet')).not.toBeNull();
  });
});
