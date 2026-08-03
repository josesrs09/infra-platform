import { Component } from '@angular/core';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  template: `
    <section class="grid">
      @for (item of modules; track item.name) {
        <article>
          <h2>{{ item.name }}</h2>
          <p>{{ item.description }}</p>
          <span>{{ item.state }}</span>
        </article>
      }
    </section>
  `
})
export class DashboardComponent {
  readonly modules = [
    { name: 'Seguridad', description: 'Usuarios, roles, permisos y sesiones.', state: 'Funcional' },
    { name: 'Configuración', description: 'Ambientes, secretos, validación, historial y exportación.', state: 'Funcional' },
    { name: 'Aplicaciones', description: 'Registro, versiones, variables y dependencias.', state: 'Funcional' },
    { name: 'Despliegues', description: 'Build, publicación, health check, promoción y rollback.', state: 'Funcional' },
    { name: 'Monitoreo', description: 'Prometheus, Grafana, Loki, Alertmanager y health checks.', state: 'Integración modular' },
    { name: 'Auditoría', description: 'Trazabilidad de cambios y accesos.', state: 'Automática' }
  ];
}
