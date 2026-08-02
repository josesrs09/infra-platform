import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../core/api-client.service';

export interface PlatformUser {
  id: string;
  username: string;
  email: string;
  full_name: string;
  enabled: boolean;
  locked: boolean;
  last_login_at?: string | null;
}

export interface PlatformRole {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  active: boolean;
}

export interface PlatformPermission {
  id: string;
  code: string;
  name: string;
  module: string;
}

export interface UserPayload {
  username: string;
  email: string;
  fullName: string;
  password?: string;
  enabled: boolean;
  locked: boolean;
  roleIds: string[];
}

export interface RolePayload {
  code: string;
  name: string;
  description?: string;
  active: boolean;
  permissionIds: string[];
}

@Injectable({ providedIn: 'root' })
export class SecurityAdminService {
  constructor(private readonly api: ApiClientService) {}

  users(): Observable<PlatformUser[]> { return this.api.get('/admin/users'); }
  createUser(payload: UserPayload): Observable<{ id: string }> { return this.api.post('/admin/users', payload); }
  updateUser(id: string, payload: UserPayload): Observable<void> { return this.api.put(`/admin/users/${id}`, payload); }
  deleteUser(id: string): Observable<void> { return this.api.delete(`/admin/users/${id}`); }

  roles(): Observable<PlatformRole[]> { return this.api.get('/admin/roles'); }
  createRole(payload: RolePayload): Observable<{ id: string }> { return this.api.post('/admin/roles', payload); }
  updateRole(id: string, payload: RolePayload): Observable<void> { return this.api.put(`/admin/roles/${id}`, payload); }
  deleteRole(id: string): Observable<void> { return this.api.delete(`/admin/roles/${id}`); }

  permissions(): Observable<PlatformPermission[]> { return this.api.get('/admin/permissions'); }
}
