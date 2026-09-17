import { Component, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthApiService } from '../../../core/auth/auth-api.service';
import { safeAuthErrorMessage } from '../../../core/auth/auth-error';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section aria-labelledby="login-title">
      <h1 id="login-title">Login</h1>

      <form
        [formGroup]="form"
        (ngSubmit)="submit()"
        [attr.aria-busy]="loading() ? 'true' : null"
        novalidate>
        <div>
          <label for="login-email">Email</label>
          <input
            id="login-email"
            name="email"
            type="email"
            formControlName="email"
            autocomplete="email" />
          @if (form.controls.email.invalid && form.controls.email.touched) {
            <p>Email is required and must be valid.</p>
          }
        </div>

        <div>
          <label for="login-password">Password</label>
          <input
            id="login-password"
            name="password"
            type="password"
            formControlName="password"
            autocomplete="current-password" />
          @if (form.controls.password.invalid && form.controls.password.touched) {
            <p>Password is required.</p>
          }
        </div>

        <button #submitButton type="submit" [disabled]="loading()">
          {{ loading() ? 'Signing in...' : 'Sign in' }}
        </button>

        @if (errorMessage()) {
          <p role="alert">{{ errorMessage() }}</p>
        }
      </form>

      <p>Don't have an account? <a routerLink="/register">Register</a></p>
    </section>
  `
})
export class LoginPageComponent {
  @ViewChild('submitButton', { static: true })
  private submitButton!: ElementRef<HTMLButtonElement>;

  private readonly auth = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  submit(): void {
    if (this.loading()) {
      return;
    }

    this.errorMessage.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.setLoading(true);
    const credentials = this.form.getRawValue();
    this.auth.login(credentials).subscribe({
      next: () => {
        this.setLoading(false);
        void this.router.navigateByUrl('/dashboard');
      },
      error: (error: unknown) => {
        this.setLoading(false);
        this.errorMessage.set(safeAuthErrorMessage(error, 'login'));
      },
    });
  }

  private setLoading(value: boolean): void {
    this.loading.set(value);
    this.submitButton.nativeElement.disabled = value;

    if (value) {
      this.submitButton.nativeElement.form?.setAttribute('aria-busy', 'true');
    } else {
      this.submitButton.nativeElement.form?.removeAttribute('aria-busy');
    }
  }
}
