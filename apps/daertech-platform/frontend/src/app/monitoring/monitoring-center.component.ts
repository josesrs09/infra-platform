import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { MonitoringDashboard, MonitoringService, MonitoringTarget } from './monitoring.service';

@Component({
  selector: 'app-monitoring-center',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="panel">
      <div class="panel-head">
        <div>
          <h2>Monitoring Center</h2>
          <p>Disponibilidad, tiempos de respuesta y objetivos operativos por ambiente.</p>
        </div>
        <div class="actions">
          <select [(ngModel)]="environment" (change)="reload()">
            <option value="">Todos</option>
            @for (item of environments; track item) { <option [value]="item">{{ item }}</option> }
          </select>
          <button (click)="reload()" [disabled]="loading()">Actualizar</button>
        </div>
      </div>

      @if (error()) { <div class="banner error">{{ error() }}</div> }

      @if (dashboard()) {
        <div class="summary-grid monitoring-summary">
          <div><b>Objetivos</b><span>{{ dashboard()!.totalTargets }}</span></div>
          <div><b>Activos</b><span>{{ dashboard()!.activeTargets }}</span></div>
          <div><b>UP</b><span class="ok">{{ count('UP') }}</span></div>
          <div><b>DOWN</b><span class="error">{{ count('DOWN') }}</span></div>
          <div><b>UNKNOWN</b><span>{{ count('UNKNOWN') }}</span></div>
        </div>
      }

      <form class="config-form" (ngSubmit)="save()">
        <label>Nombre<input [(ngModel)]="form.name" name="monitorName" required></label>
        <label>Ambiente<select [(ngModel)]="form.environment" name="monitorEnvironment">@for (item of environments; track item) { <option [value]="item">{{ item }}</option> }</select></label>
        <label>Tipo<select [(ngModel)]="form.targetType" name="monitorType"><option value="HTTP">HTTP</option><option value="HTTPS">HTTPS</option><option value="PROMETHEUS">PROMETHEUS</option></select></label>
        <label>Health URL<input [(ngModel)]="form.healthUrl" name="healthUrl" placeholder="https://api/actuator/health" required></label>
        <label>Metrics URL<input [(ngModel)]="form.metricsUrl" name="metricsUrl" placeholder="https://api/actuator/prometheus"></label>
        <label>Timeout ms<input [(ngModel)]="form.timeoutMs" name="timeoutMs" type="number" min="500" max="60000"></label>
        <label>Intervalo segundos<input [(ngModel)]="form.intervalSeconds" name="intervalSeconds" type="number" min="10"></label>
        <label class="check"><input [(ngModel)]="form.active" name="monitorActive" type="checkbox"> Activo</label>
        <button [disabled]="saving()">Guardar objetivo</button>
      </form>

      <div class="table-wrap">
        <table>
          <thead><tr><th>Nombre</th><th>Ambiente</th><th>Tipo</th><th>Estado</th><th>Respuesta</th><th>Última revisión</th><th>Acción</th></tr></thead>
          <tbody>
            @for (target of targets(); track target.id) {
              <tr>
                <td>{{ target.name }}</td><td>{{ target.environment }}</td><td>{{ target.target_type }}</td>
                <td><span class="status" [class.ok-bg]="target.last_status==='UP'" [class.error-bg]="target.last_status==='DOWN'">{{ target.last_status || 'UNKNOWN' }}</span></td>
                <td>{{ target.last_response_ms ?? '—' }} ms</td><td>{{ target.last_checked_at || '—' }}</td>
                <td><button class="secondary" (click)="check(target)" [disabled]="checkingId()===target.id">{{ checkingId()===target.id ? 'Validando…' : 'Validar' }}</button></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `
})
export class MonitoringCenterComponent implements OnInit {
  readonly environments = ['DEVELOPMENT', 'QA', 'CERTIFICATION', 'PRODUCTION'];
  readonly dashboard = signal<MonitoringDashboard | null>(null);
  readonly targets = signal<MonitoringTarget[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly checkingId = signal<string | null>(null);
  readonly error = signal('');

  environment = '';
  form = { name: '', environment: 'PRODUCTION', targetType: 'HTTPS', healthUrl: '', metricsUrl: '', timeoutMs: 5000, intervalSeconds: 60, active: true };

  constructor(private readonly monitoring: MonitoringService) {}
  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.loading.set(true); this.error.set('');
    forkJoin({ dashboard: this.monitoring.dashboard(), targets: this.monitoring.targets(this.environment) }).subscribe({
      next: result => { this.dashboard.set(result.dashboard); this.targets.set(result.targets); this.loading.set(false); },
      error: error => { this.error.set(error?.error?.message ?? 'No fue posible cargar el monitoreo'); this.loading.set(false); }
    });
  }

  count(status: string): number { return this.dashboard()?.statusCounts?.[status] ?? 0; }

  save(): void {
    this.saving.set(true); this.error.set('');
    this.monitoring.saveTarget(this.form).subscribe({
      next: () => { this.form = { ...this.form, name: '', healthUrl: '', metricsUrl: '' }; this.saving.set(false); this.reload(); },
      error: error => { this.error.set(error?.error?.message ?? 'No fue posible guardar el objetivo'); this.saving.set(false); }
    });
  }

  check(target: MonitoringTarget): void {
    this.checkingId.set(target.id); this.error.set('');
    this.monitoring.checkTarget(target.id).subscribe({
      next: () => { this.checkingId.set(null); this.reload(); },
      error: error => { this.error.set(error?.error?.message ?? 'No fue posible validar el objetivo'); this.checkingId.set(null); }
    });
  }
}
