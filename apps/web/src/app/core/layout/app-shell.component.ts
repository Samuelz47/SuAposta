import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthSessionService } from '../auth/auth-session.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss'
})
export class AppShellComponent {
  private readonly session = inject(AuthSessionService);
  private readonly router = inject(Router);

  readonly isAuthenticated = this.session.isAuthenticated;

  logout(): void {
    this.session.clear();
    void this.router.navigateByUrl('/login');
  }
}
