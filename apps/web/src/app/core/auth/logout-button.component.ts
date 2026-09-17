import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthSessionService } from './auth-session.service';

@Component({
  selector: 'app-logout-button',
  standalone: true,
  template: '<button type="button" (click)="logout()">Logout</button>',
})
export class LogoutButtonComponent {
  private readonly session = inject(AuthSessionService);
  private readonly router = inject(Router);

  logout(): void {
    this.session.clear();
    void this.router.navigateByUrl('/login');
  }
}
