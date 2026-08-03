import { Component, OnInit, signal } from '@angular/core';
import { PlatformPermission, SecurityAdminService } from './security-admin.service';

@Component({
  selector: 'app-permissions',
  standalone: true,
  template: `
    <section class="panel">
      <div class="panel-head"><div><h2>Permisos</h2><p>Catálogo de autoridades disponibles por módulo.</p></div><button (click)="load()">Actualizar</button></div>
      @if(error()){<p class="error">{{error()}}</p>}
      <div class="table-wrap"><table><thead><tr><th>Módulo</th><th>Código</th><th>Nombre</th></tr></thead><tbody>
        @for(permission of permissions();track permission.id){<tr><td>{{permission.module}}</td><td>{{permission.code}}</td><td>{{permission.name}}</td></tr>}
      </tbody></table></div>
    </section>
  `
})
export class PermissionsComponent implements OnInit {
  readonly permissions=signal<PlatformPermission[]>([]);readonly error=signal('');
  constructor(private readonly service:SecurityAdminService){}
  ngOnInit():void{this.load();}
  load():void{this.error.set('');this.service.permissions().subscribe({next:r=>this.permissions.set(r),error:e=>this.error.set(e?.error?.message||'No fue posible consultar los permisos')});}
}
