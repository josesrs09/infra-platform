import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../core/api-client.service';

export interface ApplicationSummary {
  id: string; code: string; name: string; description?: string | null; repository_url: string;
  default_branch: string; technology: string; build_tool?: string | null; dockerfile_path: string;
  context_path: string; internal_port?: number | null; health_path?: string | null; metrics_path?: string | null;
  active: boolean;
}
export interface ApplicationEnvironment { id: string; environment: string; branch?: string | null; public_url?: string | null; replicas: number; cpu_limit?: string | null; memory_limit?: string | null; enabled: boolean; }
export interface ApplicationVariable { id: string; environment: string; variable_key: string; variable_value?: string | null; secret: boolean; required: boolean; description?: string | null; }
export interface ApplicationDependency { id: string; dependency_type: string; dependency_name: string; target?: string | null; required: boolean; }
export interface ApplicationVersion { id: string; version: string; git_commit?: string | null; image_tag?: string | null; notes?: string | null; created_by: string; created_at: string; }
export interface ApplicationDetail extends ApplicationSummary { environments: ApplicationEnvironment[]; variables: ApplicationVariable[]; dependencies: ApplicationDependency[]; versions: ApplicationVersion[]; }

@Injectable({ providedIn: 'root' })
export class ApplicationsCenterService {
  constructor(private readonly api: ApiClientService) {}
  list(): Observable<ApplicationSummary[]> { return this.api.get('/admin/applications'); }
  find(id: string): Observable<ApplicationDetail> { return this.api.get(`/admin/applications/${id}`); }
  save(payload: unknown): Observable<ApplicationDetail> { return this.api.post('/admin/applications', payload); }
  delete(id: string): Observable<void> { return this.api.delete(`/admin/applications/${id}`); }
  technologies(): Observable<string[]> { return this.api.get('/admin/applications/catalog/technologies'); }
  saveEnvironment(id: string, payload: unknown): Observable<ApplicationDetail> { return this.api.post(`/admin/applications/${id}/environments`, payload); }
  deleteEnvironment(id: string, childId: string): Observable<ApplicationDetail> { return this.api.delete(`/admin/applications/${id}/environments/${childId}`); }
  saveVariable(id: string, payload: unknown): Observable<ApplicationDetail> { return this.api.post(`/admin/applications/${id}/variables`, payload); }
  deleteVariable(id: string, childId: string): Observable<ApplicationDetail> { return this.api.delete(`/admin/applications/${id}/variables/${childId}`); }
  saveDependency(id: string, payload: unknown): Observable<ApplicationDetail> { return this.api.post(`/admin/applications/${id}/dependencies`, payload); }
  deleteDependency(id: string, childId: string): Observable<ApplicationDetail> { return this.api.delete(`/admin/applications/${id}/dependencies/${childId}`); }
  addVersion(id: string, payload: unknown): Observable<ApplicationDetail> { return this.api.post(`/admin/applications/${id}/versions`, payload); }
  deleteVersion(id: string, childId: string): Observable<ApplicationDetail> { return this.api.delete(`/admin/applications/${id}/versions/${childId}`); }
}
