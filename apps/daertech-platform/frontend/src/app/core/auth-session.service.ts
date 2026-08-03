import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly accessTokenKey = 'accessToken';
  private readonly refreshTokenKey = 'refreshToken';

  readonly accessToken = signal<string | null>(localStorage.getItem(this.accessTokenKey));

  isAuthenticated(): boolean {
    return Boolean(this.accessToken());
  }

  save(accessToken: string, refreshToken: string): void {
    localStorage.setItem(this.accessTokenKey, accessToken);
    localStorage.setItem(this.refreshTokenKey, refreshToken);
    this.accessToken.set(accessToken);
  }

  refreshToken(): string | null {
    return localStorage.getItem(this.refreshTokenKey);
  }

  clear(): void {
    localStorage.removeItem(this.accessTokenKey);
    localStorage.removeItem(this.refreshTokenKey);
    this.accessToken.set(null);
  }
}
