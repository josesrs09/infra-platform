import { Component, signal } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [FormsModule],
  template: `
    @if (!token()) {
      <main class="login-shell">
        <section class="login-card">
          <span class="eyebrow">DAERTECH</span>
          <h1>Infrastructure Management Platform</h1>
          <p>Acceso administrativo seguro.</p>
          <form (ngSubmit)="login()">
            <label>Usuario<input [(ngModel)]="username" name="username" required></label>
            <label>Contraseña<input [(ngModel)]="password" name="password" type="password" required></label>
            <button [disabled]="loading()">{{ loading() ? 'Validando…' : 'Iniciar sesión' }}</button>
            @if (error()) { <p class="error">{{ error() }}</p> }
          </form>
        </section>
      </main>
    } @else {
      <main class="shell">
        <header>
          <div><span class="eyebrow">DAERTECH</span><h1>Centro de administración</h1><p>Seguridad, configuración, operación y auditoría.</p></div>
          <button class="secondary" (click)="logout()">Cerrar sesión</button>
        </header>
        <nav>
          <button (click)="loadUsers()">Usuarios</button>
          <button (click)="loadRoles()">Roles</button>
          <button (click)="loadPermissions()">Permisos</button>
          <button (click)="section.set('dashboard')">Dashboard</button>
        </nav>
        @if (section() === 'dashboard') {
          <section class="grid">
            @for (item of modules; track item.name) { <article><h2>{{ item.name }}</h2><p>{{ item.description }}</p><span>{{ item.state }}</span></article> }
          </section>
        } @else {
          <section class="panel">
            <h2>{{ title() }}</h2>
            <div class="table-wrap"><table><thead><tr>@for (column of columns(); track column){<th>{{column}}</th>}</tr></thead>
              <tbody>@for (row of rows(); track row.id){<tr>@for (column of columns(); track column){<td>{{ row[key(column)] ?? '—' }}</td>}</tr>}</tbody>
            </table></div>
          </section>
        }
      </main>
    }
  `
})
class AppComponent {
  private readonly api = '/api/v1';
  token = signal(localStorage.getItem('accessToken'));
  loading = signal(false); error = signal(''); section = signal('dashboard');
  title = signal(''); rows = signal<any[]>([]); columns = signal<string[]>([]);
  username = 'admin'; password = '';
  readonly modules = [
    { name: 'Seguridad', description: 'Usuarios, roles, permisos y sesiones.', state: 'En implementación' },
    { name: 'Configuración', description: 'Servicios internos, externos, ambientes y secretos.', state: 'Base creada' },
    { name: 'Aplicaciones', description: 'Registro, versiones, variables y dependencias.', state: 'Planificado' },
    { name: 'Despliegues', description: 'Build, publicación, health check y rollback.', state: 'Planificado' },
    { name: 'Monitoreo', description: 'Prometheus, Grafana, Loki y Alertmanager.', state: 'Integración pendiente' },
    { name: 'Auditoría', description: 'Trazabilidad de cambios y accesos.', state: 'Automática en APIs admin' }
  ];
  constructor(private http: HttpClient) {}
  login(){this.loading.set(true);this.error.set('');this.http.post<any>(`${this.api}/auth/login`,{username:this.username,password:this.password}).subscribe({next:r=>{localStorage.setItem('accessToken',r.accessToken);localStorage.setItem('refreshToken',r.refreshToken);this.token.set(r.accessToken);this.loading.set(false);},error:e=>{this.error.set(e?.error?.message||'Credenciales inválidas');this.loading.set(false);}})}
  logout(){const refreshToken=localStorage.getItem('refreshToken');this.http.post(`${this.api}/auth/logout`,{refreshToken}).subscribe({complete:()=>this.clear(),error:()=>this.clear()});}
  clear(){localStorage.clear();this.token.set(null);this.section.set('dashboard');}
  headers(){return {Authorization:`Bearer ${this.token()}`};}
  loadUsers(){this.load('users','Usuarios',['Usuario','Correo','Nombre','Habilitado','Bloqueado']);}
  loadRoles(){this.load('roles','Roles',['Código','Nombre','Descripción','Activo']);}
  loadPermissions(){this.load('permissions','Permisos',['Código','Nombre','Módulo']);}
  load(resource:string,title:string,columns:string[]){this.http.get<any[]>(`${this.api}/admin/${resource}`,{headers:this.headers()}).subscribe({next:data=>{this.rows.set(data);this.columns.set(columns);this.title.set(title);this.section.set(resource);},error:e=>{if(e.status===401)this.clear();else this.error.set(e?.error?.message||'No fue posible cargar los datos');}})}
  key(label:string){return ({'Usuario':'username','Correo':'email','Nombre':'full_name','Habilitado':'enabled','Bloqueado':'locked','Código':'code','Descripción':'description','Activo':'active','Módulo':'module'} as any)[label]||label.toLowerCase();}
}
bootstrapApplication(AppComponent,{providers:[provideHttpClient()]}).catch(error=>console.error(error));
