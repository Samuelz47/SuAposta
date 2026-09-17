import { Component, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthApiService } from '../../../core/auth/auth-api.service';
import { safeAuthErrorMessage } from '../../../core/auth/auth-error';

@Component({
  selector: 'app-register-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section aria-labelledby="register-title">
      <h1 id="register-title">Register</h1>

      <form
        [formGroup]="form"
        (ngSubmit)="submit()"
        [attr.aria-busy]="loading() ? 'true' : null"
        novalidate>
        <div>
          <label for="register-name">Name</label>
          <input
            id="register-name"
            name="name"
            type="text"
            formControlName="name"
            autocomplete="name" />
          @if (form.controls.name.invalid && form.controls.name.touched) {
            <p>Name is required.</p>
          }
        </div>

        <div>
          <label for="register-email">Email</label>
          <input
            id="register-email"
            name="email"
            type="email"
            formControlName="email"
            autocomplete="email" />
          @if (form.controls.email.invalid && form.controls.email.touched) {
            <p>Email is required and must be valid.</p>
          }
        </div>

        <div>
          <label for="register-password">Password</label>
          <input
            id="register-password"
            name="password"
            type="password"
            formControlName="password"
            autocomplete="new-password" />
          @if (form.controls.password.invalid && form.controls.password.touched) {
            <p>Password must contain at least 8 characters.</p>
          }
        </div>

        <button #submitButton type="submit" [disabled]="loading()">
          {{ loading() ? 'Creating account...' : 'Create account' }}
        </button>

        @if (errorMessage()) {
          <p role="alert">{{ errorMessage() }}</p>
        }
      </form>

      <p>Already have an account? <a routerLink="/login">Login</a></p>
    </section>
  `
})
export class RegisterPageComponent {
  @ViewChild('submitButton', { static: true })
  private submitButton!: ElementRef<HTMLButtonElement>;

  private readonly auth = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);

  readonly form = this.formBuilder.nonNullable.group({
    name: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
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
    const registration = this.form.getRawValue();
    this.auth.register(registration).subscribe({
      next: () => {
        this.setLoading(false);
        void this.router.navigateByUrl('/login');
      },
      error: (error: unknown) => {
        this.setLoading(false);
        this.errorMessage.set(safeAuthErrorMessage(error, 'register'));
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
