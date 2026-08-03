import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { ApiClientService } from '../core/api-client.service';
import { AuthSessionService } from '../core/auth-session.service';

interface LoginResponse { accessToken: string; refreshToken: string; }

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="login-shell">
      <section class="login-card">
        <span class="eyebrow">DAERTECH</span>
        <h1>Infrastructure Management Platform</h1>
        <p>Acceso administrativo seguro.</p>
        <form (ngSubmit)="login()">
          <label>Usuario<input [(ngModel)]="username" name="username" autocomplete="username" required></label>
          <label>Contraseña<input [(ngModel)]="password" name="password" type="password" autocomplete="current-password" required></label>
          <button [disabled]="loading()">{{ loading() ? 'Validando…' : 'Iniciar sesión' }}</button>
          @if (error()) { <p class="error">{{ error() }}</p> }
        </form>
      </section>
    </main>
  `
})
export class LoginComponent {
  username = 'admin';
  password = '';
  readonly loading = signal(false);
  readonly error = signal('');

  constructor(
    private readonly api: ApiClientService,
    private readonly session: AuthSessionService,
    private readonly router: Router,
    private readonly route: ActivatedRoute
  ) {}

  login(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.api.post<LoginResponse>('/auth/login', { username: this.username, password: this.password })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: response => {
          this.session.save(response.accessToken, response.refreshToken);
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/';
          void this.router.navigateByUrl(returnUrl);
        },
        error: error => this.error.set(error?.error?.message || 'Credenciales inválidas')
      });
  }
}
