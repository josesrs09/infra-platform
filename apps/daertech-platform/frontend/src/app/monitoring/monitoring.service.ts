import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../core/api-client.service';

export interface MonitoringDashboard {
  totalTargets: number;
  activeTargets: number;
  statusCounts: Record<string, number>;
  recentFailures: MonitoringCheck[];
}

export interface MonitoringTarget {
  id: string;
  application_id?: string;
  application_code?: string;
  name: string;
  environment: string;
  target_type: string;
  health_url?: string;
  metrics_url?: string;
  timeout_ms: number;
  interval_seconds: number;
  active: boolean;
  last_status?: string;
  last_checked_at?: string;
  last_response_ms?: number;
}

export interface MonitoringCheck {
  id: string;
  target_id: string;
  status: string;
  http_status?: number;
  response_ms?: number;
  message?: string;
  checked_at: string;
}

@Injectable({ providedIn: 'root' })
export class MonitoringService {
  constructor(private readonly api: ApiClientService) {}

  dashboard(): Observable<MonitoringDashboard> {
    return this.api.get<MonitoringDashboard>('/admin/monitoring/dashboard');
  }

  targets(environment?: string): Observable<MonitoringTarget[]> {
    return this.api.get<MonitoringTarget[]>('/admin/monitoring/targets', { environment });
  }

  saveTarget(target: unknown): Observable<MonitoringTarget> {
    return this.api.post<MonitoringTarget>('/admin/monitoring/targets', target);
  }

  checkTarget(id: string): Observable<MonitoringTarget> {
    return this.api.post<MonitoringTarget>(`/admin/monitoring/targets/${id}/check`);
  }
}
