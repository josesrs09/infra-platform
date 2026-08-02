import { Component } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';

@Component({
  selector: 'app-root',
  standalone: true,
  template: `
    <main class="shell">
      <header>
        <div>
          <span class="eyebrow">DAERTECH</span>
          <h1>Infrastructure Management Platform</h1>
          <p>Centro unificado para configuración, despliegues, monitoreo, auditoría y respaldos.</p>
        </div>
        <span class="status">Foundation v0.1.0</span>
      </header>

      <section class="grid">
        @for (item of modules; track item.name) {
          <article>
            <h2>{{ item.name }}</h2>
            <p>{{ item.description }}</p>
            <span>{{ item.state }}</span>
          </article>
        }
      </section>
    </main>
  `
})
class AppComponent {
  readonly modules = [
    { name: 'Configuración', description: 'Servicios internos, externos, ambientes y secretos.', state: 'Base creada' },
    { name: 'Aplicaciones', description: 'Registro, versiones, variables y dependencias.', state: 'Planificado' },
    { name: 'Despliegues', description: 'Build, publicación, health check y rollback.', state: 'Planificado' },
    { name: 'Monitoreo', description: 'Prometheus, Grafana, Loki y Alertmanager.', state: 'Integración pendiente' },
    { name: 'Respaldos', description: 'PostgreSQL, Redis, archivos y Dropbox.', state: 'Integración pendiente' },
    { name: 'Auditoría', description: 'Trazabilidad completa de cambios y accesos.', state: 'Esquema creado' }
  ];
}

bootstrapApplication(AppComponent).catch(error => console.error(error));
