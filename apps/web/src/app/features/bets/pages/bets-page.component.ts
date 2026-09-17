import { Component } from '@angular/core';

import { LogoutButtonComponent } from '../../../core/auth/logout-button.component';

@Component({
  selector: 'app-bets-page',
  standalone: true,
  imports: [LogoutButtonComponent],
  template: `
    <h1>Bets</h1>
    <app-logout-button />
  `
})
export class BetsPageComponent {}
