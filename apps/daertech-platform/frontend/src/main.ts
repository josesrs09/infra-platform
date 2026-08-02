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
      <main class="login-shell"><section class="login-card"><span class="eyebrow">DAERTECH</span><h1>Infrastructure Management Platform</h1><p>Acceso administrativo seguro.</p>
        <form (ngSubmit)="login()"><label>Usuario<input [(ngModel)]="username" name="username" required></label><label>Contraseña<input [(ngModel)]="password" name="password" type="password" required></label><button [disabled]="loading()">{{loading()?'Validando…':'Iniciar sesión'}}</button>@if(error()){<p class="error">{{error()}}</p>}</form>
      </section></main>
    } @else {
      <main class="shell"><header><div><span class="eyebrow">DAERTECH</span><h1>Centro de administración</h1><p>Seguridad, configuración, operación y auditoría.</p></div><button class="secondary" (click)="logout()">Cerrar sesión</button></header>
        <nav><button (click)="section.set('dashboard')">Dashboard</button><button (click)="loadUsers()">Usuarios</button><button (click)="loadRoles()">Roles</button><button (click)="loadPermissions()">Permisos</button><button (click)="openConfig()">Configuración</button></nav>
        @if(section()==='dashboard'){
          <section class="grid">@for(item of modules;track item.name){<article><h2>{{item.name}}</h2><p>{{item.description}}</p><span>{{item.state}}</span></article>}</section>
        } @else if(section()==='config') {
          <section class="panel"><div class="panel-head"><div><h2>Configuration Center</h2><p>Valores por ambiente con secretos cifrados, historial y exportación.</p></div><div class="actions"><select [(ngModel)]="configEnvironment" (change)="loadConfigurations()">@for(env of environments;track env){<option [value]="env">{{env}}</option>}</select><button (click)="download('ENV')">Exportar .env</button><button (click)="download('YAML')">Exportar YAML</button></div></div>
            <form class="config-form" (ngSubmit)="saveConfiguration()">
              <label>Categoría<input [(ngModel)]="config.category" name="category" required></label><label>Clave<input [(ngModel)]="config.key" name="key" placeholder="POSTGRES_HOST" required></label>
              <label>Tipo<select [(ngModel)]="config.valueType" name="valueType">@for(type of valueTypes;track type){<option [value]="type">{{type}}</option>}</select></label>
              <label>Valor<input [(ngModel)]="config.value" name="value" [type]="config.secret?'password':'text'" required></label><label>Descripción<input [(ngModel)]="config.description" name="description"></label><label>Validación regex<input [(ngModel)]="config.validationRule" name="validationRule"></label>
              <label class="check"><input type="checkbox" [(ngModel)]="config.secret" name="secret"> Secreto</label><label class="check"><input type="checkbox" [(ngModel)]="config.active" name="active"> Activo</label><label>Motivo<input [(ngModel)]="config.reason" name="reason" required></label><button>Guardar configuración</button>
            </form>
            <div class="validator"><h3>Probar conectividad</h3><div class="inline"><select [(ngModel)]="validation.type">@for(type of validationTypes;track type){<option [value]="type">{{type}}</option>}</select><input [(ngModel)]="validation.host" placeholder="host o dominio"><input [(ngModel)]="validation.port" type="number" placeholder="puerto"><input [(ngModel)]="validation.path" placeholder="/health"><button (click)="validateConnection()">Validar</button></div>@if(validationResult()){<p [class.ok]="validationResult().success" [class.error]="!validationResult().success">{{validationResult().message}} · {{validationResult().elapsedMs}} ms</p>}</div>
            <div class="table-wrap"><table><thead><tr><th>Categoría</th><th>Clave</th><th>Valor</th><th>Tipo</th><th>Secreto</th><th>Versión</th></tr></thead><tbody>@for(row of configurations();track row.id){<tr><td>{{row.category}}</td><td>{{row.key}}</td><td>{{row.value}}</td><td>{{row.valueType}}</td><td>{{row.secret?'Sí':'No'}}</td><td>{{row.version}}</td></tr>}</tbody></table></div>
          </section>
        } @else {
          <section class="panel"><h2>{{title()}}</h2><div class="table-wrap"><table><thead><tr>@for(column of columns();track column){<th>{{column}}</th>}</tr></thead><tbody>@for(row of rows();track row.id){<tr>@for(column of columns();track column){<td>{{row[key(column)]??'—'}}</td>}</tr>}</tbody></table></div></section>
        }
      </main>
    }
  `
})
class AppComponent {
  private readonly api='/api/v1'; token=signal(localStorage.getItem('accessToken')); loading=signal(false); error=signal(''); section=signal('dashboard'); title=signal(''); rows=signal<any[]>([]); columns=signal<string[]>([]); configurations=signal<any[]>([]); validationResult=signal<any>(null);
  username='admin'; password=''; configEnvironment='PRODUCTION'; environments=['DEVELOPMENT','QA','CERTIFICATION','PRODUCTION']; valueTypes=['STRING','NUMBER','BOOLEAN','URL','JSON','PASSWORD','TOKEN','CERTIFICATE']; validationTypes=['HTTP','HTTPS','TCP','POSTGRESQL','MYSQL','REDIS','RABBITMQ','MQTT','SMTP','MINIO','REST','SOAP','TELEGRAM'];
  config:any={category:'GENERAL',key:'',value:'',secret:false,environment:'PRODUCTION',valueType:'STRING',description:'',validationRule:'',active:true,reason:'Configuración inicial'}; validation:any={type:'HTTP',host:'localhost',port:8080,path:'/api/v1/actuator/health',scheme:'http',method:'GET',timeoutMs:5000};
  readonly modules=[{name:'Seguridad',description:'Usuarios, roles, permisos y sesiones.',state:'Funcional'},{name:'Configuración',description:'Ambientes, secretos, validación, historial y exportación.',state:'En implementación'},{name:'Aplicaciones',description:'Registro, versiones, variables y dependencias.',state:'Planificado'},{name:'Despliegues',description:'Build, publicación, health check y rollback.',state:'Planificado'},{name:'Monitoreo',description:'Prometheus, Grafana, Loki y Alertmanager.',state:'Integración pendiente'},{name:'Auditoría',description:'Trazabilidad de cambios y accesos.',state:'Automática'}];
  constructor(private http:HttpClient){}
  login(){this.loading.set(true);this.error.set('');this.http.post<any>(`${this.api}/auth/login`,{username:this.username,password:this.password}).subscribe({next:r=>{localStorage.setItem('accessToken',r.accessToken);localStorage.setItem('refreshToken',r.refreshToken);this.token.set(r.accessToken);this.loading.set(false)},error:e=>{this.error.set(e?.error?.message||'Credenciales inválidas');this.loading.set(false)}})}
  logout(){const refreshToken=localStorage.getItem('refreshToken');this.http.post(`${this.api}/auth/logout`,{refreshToken}).subscribe({complete:()=>this.clear(),error:()=>this.clear()})} clear(){localStorage.clear();this.token.set(null);this.section.set('dashboard')} headers(){return{Authorization:`Bearer ${this.token()}`}}
  loadUsers(){this.load('users','Usuarios',['Usuario','Correo','Nombre','Habilitado','Bloqueado'])} loadRoles(){this.load('roles','Roles',['Código','Nombre','Descripción','Activo'])} loadPermissions(){this.load('permissions','Permisos',['Código','Nombre','Módulo'])}
  load(resource:string,title:string,columns:string[]){this.http.get<any[]>(`${this.api}/admin/${resource}`,{headers:this.headers()}).subscribe({next:data=>{this.rows.set(data);this.columns.set(columns);this.title.set(title);this.section.set(resource)},error:e=>this.handle(e)})}
  openConfig(){this.section.set('config');this.loadConfigurations()} loadConfigurations(){this.http.get<any[]>(`${this.api}/admin/configurations?environment=${this.configEnvironment}`,{headers:this.headers()}).subscribe({next:r=>this.configurations.set(r),error:e=>this.handle(e)})}
  saveConfiguration(){this.config.environment=this.configEnvironment;this.config.key=this.config.key.toUpperCase();this.http.post<any>(`${this.api}/admin/configurations`,this.config,{headers:this.headers()}).subscribe({next:()=>{this.config={...this.config,key:'',value:'',reason:'Actualización administrativa'};this.loadConfigurations()},error:e=>this.handle(e)})}
  validateConnection(){const payload={...this.validation,port:this.validation.port?Number(this.validation.port):null};this.http.post<any>(`${this.api}/admin/configuration-operations/validate`,payload,{headers:this.headers()}).subscribe({next:r=>this.validationResult.set(r),error:e=>this.handle(e)})}
  download(format:string){this.http.get(`${this.api}/admin/configuration-operations/export?environment=${this.configEnvironment}&format=${format}`,{headers:this.headers(),responseType:'blob'}).subscribe({next:blob=>{const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=`daertech-${this.configEnvironment.toLowerCase()}.${format==='YAML'?'yaml':'env'}`;a.click();URL.revokeObjectURL(a.href)},error:e=>this.handle(e)})}
  handle(e:any){if(e.status===401)this.clear();else this.error.set(e?.error?.message||'Operación no completada')} key(label:string){return({'Usuario':'username','Correo':'email','Nombre':'full_name','Habilitado':'enabled','Bloqueado':'locked','Código':'code','Descripción':'description','Activo':'active','Módulo':'module'}as any)[label]||label.toLowerCase()}
}
bootstrapApplication(AppComponent,{providers:[provideHttpClient()]}).catch(console.error);
