import { Component } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ApiClientService } from '../core/api-client.service';
import { AuthSessionService } from '../core/auth-session.service';

@Component({
  selector: 'app-admin-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <main class="shell">
      <header>
        <div>
          <span class="eyebrow">DAERTECH</span>
          <h1>Centro de administración</h1>
          <p>Seguridad, configuración, aplicaciones, despliegues y observabilidad.</p>
        </div>
        <button class="secondary" (click)="logout()">Cerrar sesión</button>
      </header>
      <nav>
        <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{exact:true}">Dashboard</a>
        <a routerLink="/users" routerLinkActive="active">Usuarios</a>
        <a routerLink="/roles" routerLinkActive="active">Roles</a>
        <a routerLink="/permissions" routerLinkActive="active">Permisos</a>
        <a routerLink="/monitoring" routerLinkActive="active">Monitoreo</a>
        <a routerLink="/legacy" routerLinkActive="active">Administración heredada</a>
      </nav>
      <router-outlet />
    </main>
  `
})
export class AdminShellComponent {
  constructor(
    private readonly api: ApiClientService,
    private readonly session: AuthSessionService,
    private readonly router: Router
  ) {}

  logout(): void {
    const refreshToken = this.session.refreshToken();
    this.api.post('/auth/logout', { refreshToken }).subscribe({
      next: () => this.finishLogout(),
      error: () => this.finishLogout()
    });
  }

  private finishLogout(): void {
    this.session.clear();
    void this.router.navigate(['/login']);
  }
}
