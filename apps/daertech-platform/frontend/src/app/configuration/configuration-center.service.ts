import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../core/api-client.service';

export interface ConfigurationItem {
  id: string;
  category: string;
  key: string;
  value: string;
  secret: boolean;
  environment: string;
  valueType: string;
  description?: string | null;
  active: boolean;
  version: number;
  updatedAt?: string | null;
}

export interface ConfigurationPayload {
  category: string;
  key: string;
  value: string;
  secret: boolean;
  environment: string;
  valueType: string;
  description?: string;
  validationRule?: string;
  active: boolean;
  reason: string;
}

export interface ConfigurationHistory {
  id: string;
  operation: string;
  version: number;
  reason?: string | null;
  changed_at: string;
  success: boolean;
}

export interface ConnectivityRequest {
  type: string;
  host: string;
  port?: number | null;
  path?: string;
  scheme?: string;
  method?: string;
  timeoutMs?: number;
}

export interface ConnectivityResult {
  success: boolean;
  message: string;
  elapsedMs?: number;
  statusCode?: number;
}

@Injectable({ providedIn: 'root' })
export class ConfigurationCenterService {
  private readonly baseUrl = '/api/v1';

  constructor(private readonly api: ApiClientService, private readonly http: HttpClient) {}

  list(environment: string): Observable<ConfigurationItem[]> {
    return this.api.get('/admin/configurations', { environment });
  }

  save(payload: ConfigurationPayload): Observable<ConfigurationItem> {
    return this.api.post('/admin/configurations', payload);
  }

  history(id: string): Observable<ConfigurationHistory[]> {
    return this.api.get(`/admin/configurations/${id}/history`);
  }

  rollback(id: string, version: number, reason: string): Observable<ConfigurationItem> {
    return this.api.post(`/admin/configurations/${id}/rollback/${version}`, { reason });
  }

  validate(payload: ConnectivityRequest): Observable<ConnectivityResult> {
    return this.api.post('/admin/configuration-operations/validate', payload);
  }

  export(environment: string, format: 'ENV' | 'YAML'): Observable<Blob> {
    const params = new HttpParams().set('environment', environment).set('format', format);
    return this.http.get(`${this.baseUrl}/admin/configuration-operations/export`, {
      params,
      responseType: 'blob'
    });
  }
}
