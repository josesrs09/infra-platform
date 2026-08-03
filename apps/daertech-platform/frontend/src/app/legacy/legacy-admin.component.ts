import { Component } from '@angular/core';

@Component({
  selector: 'app-legacy-admin',
  standalone: true,
  template: `
    <section class="panel">
      <h2>Administración heredada</h2>
      <p>Las pantallas de usuarios, roles, configuración, aplicaciones y despliegues continúan temporalmente en el punto de entrada heredado mientras se migran por módulo.</p>
      <p class="warning">No active este route como reemplazo definitivo hasta completar la compilación y las pruebas de regresión.</p>
    </section>
  `
})
export class LegacyAdminComponent {}
