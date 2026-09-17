import { Component } from '@angular/core';

import { LogoutButtonComponent } from '../../../core/auth/logout-button.component';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [LogoutButtonComponent],
  template: `
    <h1>Dashboard</h1>
    <app-logout-button />
  `
})
export class DashboardPageComponent {}
