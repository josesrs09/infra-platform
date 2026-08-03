import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../core/api-client.service';

export interface DeploymentStep {
  id: string;
  step_order: number;
  step_name: string;
  status: string;
  command_summary?: string | null;
  output?: string | null;
  exit_code?: number | null;
  started_at?: string | null;
  finished_at?: string | null;
}

export interface DeploymentRuntimeEvent {
  id: string;
  event_type: string;
  status: string;
  details?: string | null;
  performed_by?: string | null;
  created_at: string;
}

export interface Deployment {
  id: string;
  application_id: string;
  application_code: string;
  application_name: string;
  environment: string;
  version: string;
  git_branch: string;
  git_commit?: string | null;
  image_tag?: string | null;
  registry_id?: string | null;
  registry_image?: string | null;
  strategy?: string | null;
  status: string;
  health_status?: string | null;
  health_message?: string | null;
  active_slot?: string | null;
  requested_by: string;
  reason?: string | null;
  notification_status?: string | null;
  created_at: string;
  started_at?: string | null;
  finished_at?: string | null;
  steps?: DeploymentStep[];
  artifacts?: unknown[];
}

export interface DeploymentRequest {
  applicationId: string;
  environment: string;
  version: string;
  branch: string;
  reason: string;
}

export interface Registry {
  id: string;
  code: string;
  name: string;
  registry_url: string;
  username_secret_key?: string | null;
  password_secret_key?: string | null;
  insecure: boolean;
  active: boolean;
}

@Injectable({ providedIn: 'root' })
export class DeploymentCenterService {
  constructor(private readonly api: ApiClientService) {}

  list(environment = '', applicationId = ''): Observable<Deployment[]> {
    return this.api.get('/admin/deployments', { environment, applicationId });
  }
  detail(id: string): Observable<Deployment> { return this.api.get(`/admin/deployments/${id}`); }
  create(payload: DeploymentRequest): Observable<Deployment> { return this.api.post('/admin/deployments', payload); }
  execute(id: string): Observable<Deployment> { return this.api.post(`/admin/deployments/${id}/execute`); }
  rollback(id: string, reason: string): Observable<Deployment> { return this.api.post(`/admin/deployments/${id}/rollback`, { reason }); }
  strategies(): Observable<Array<{ code: string; name: string; description: string }>> { return this.api.get('/admin/deployment-operations/strategies'); }
  setStrategy(id: string, strategy: string, registryId: string | null, registryImage: string): Observable<Deployment> {
    return this.api.post(`/admin/deployment-operations/${id}/strategy`, { strategy, registryId, registryImage });
  }
  promote(id: string, targetEnvironment: string, reason: string): Observable<Deployment> {
    return this.api.post(`/admin/deployment-operations/${id}/promote`, { targetEnvironment, reason });
  }
  events(id: string): Observable<DeploymentRuntimeEvent[]> { return this.api.get(`/admin/deployment-runtime/${id}/events`); }
  pushImage(id: string): Observable<Deployment> { return this.api.post(`/admin/deployment-runtime/${id}/push`); }
  switchTraffic(id: string, targetSlot: 'BLUE' | 'GREEN'): Observable<Deployment> {
    return this.api.post(`/admin/deployment-runtime/${id}/switch-traffic`, { targetSlot });
  }
  registries(): Observable<Registry[]> { return this.api.get('/admin/container-registries'); }
}
