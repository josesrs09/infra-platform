import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApplicationDetail, ApplicationSummary, ApplicationsCenterService } from './applications-center.service';

@Component({
  selector: 'app-applications-center', standalone: true, imports: [FormsModule],
  template: `
  <section class="panel">
    <div class="panel-head"><div><h2>Applications Center</h2><p>Aplicaciones, ambientes, variables, dependencias y versiones.</p></div><button (click)="newApplication()">Nueva aplicación</button></div>
    @if(message()){<p [class.error]="failed()" [class.ok]="!failed()">{{message()}}</p>}
    <form class="config-form" (ngSubmit)="saveApplication()">
      <label>Código<input [(ngModel)]="app.code" name="code" required></label><label>Nombre<input [(ngModel)]="app.name" name="name" required></label>
      <label>Repositorio<input [(ngModel)]="app.repositoryUrl" name="repositoryUrl" required></label><label>Rama predeterminada<input [(ngModel)]="app.defaultBranch" name="defaultBranch"></label>
      <label>Tecnología<select [(ngModel)]="app.technology" name="technology">@for(item of technologies();track item){<option [value]="item">{{item}}</option>}</select></label>
      <label>Build tool<input [(ngModel)]="app.buildTool" name="buildTool"></label><label>Dockerfile<input [(ngModel)]="app.dockerfilePath" name="dockerfilePath"></label>
      <label>Contexto<input [(ngModel)]="app.contextPath" name="contextPath"></label><label>Puerto interno<input [(ngModel)]="app.internalPort" name="internalPort" type="number"></label>
      <label>Health path<input [(ngModel)]="app.healthPath" name="healthPath"></label><label>Metrics path<input [(ngModel)]="app.metricsPath" name="metricsPath"></label>
      <label>Descripción<input [(ngModel)]="app.description" name="description"></label><label class="check"><input type="checkbox" [(ngModel)]="app.active" name="active"> Activa</label>
      <button [disabled]="busy()">{{app.id?'Actualizar':'Crear'}}</button>
    </form>
    <div class="table-wrap"><table><thead><tr><th>Código</th><th>Nombre</th><th>Tecnología</th><th>Rama</th><th>Puerto</th><th>Activa</th><th>Acciones</th></tr></thead><tbody>
      @for(row of applications();track row.id){<tr><td>{{row.code}}</td><td>{{row.name}}</td><td>{{row.technology}}</td><td>{{row.default_branch}}</td><td>{{row.internal_port||'—'}}</td><td>{{row.active?'Sí':'No'}}</td><td><button class="secondary" (click)="select(row.id)">Administrar</button> <button class="danger" (click)="removeApplication(row)">Eliminar</button></td></tr>}
    </tbody></table></div>
  </section>
  @if(detail()){
    <section class="panel"><div class="panel-head"><div><h2>{{detail()!.code}} · Detalle técnico</h2><p>{{detail()!.repository_url}}</p></div><button class="secondary" (click)="select(detail()!.id)">Actualizar</button></div>
      <h3>Ambientes</h3><form class="config-form" (ngSubmit)="saveEnvironment()"><label>Ambiente<select [(ngModel)]="environment.environment" name="environment">@for(e of environments;track e){<option [value]="e">{{e}}</option>}</select></label><label>Rama<input [(ngModel)]="environment.branch" name="envBranch"></label><label>URL pública<input [(ngModel)]="environment.publicUrl" name="publicUrl"></label><label>Réplicas<input [(ngModel)]="environment.replicas" name="replicas" type="number" min="1"></label><label>CPU<input [(ngModel)]="environment.cpuLimit" name="cpuLimit"></label><label>Memoria<input [(ngModel)]="environment.memoryLimit" name="memoryLimit"></label><label class="check"><input type="checkbox" [(ngModel)]="environment.enabled" name="envEnabled"> Habilitado</label><button>Guardar ambiente</button></form>
      <div class="table-wrap"><table><thead><tr><th>Ambiente</th><th>Rama</th><th>URL</th><th>Réplicas</th><th>Recursos</th><th></th></tr></thead><tbody>@for(row of detail()!.environments;track row.id){<tr><td>{{row.environment}}</td><td>{{row.branch||'—'}}</td><td>{{row.public_url||'—'}}</td><td>{{row.replicas}}</td><td>{{row.cpu_limit||'—'}} / {{row.memory_limit||'—'}}</td><td><button class="secondary" (click)="editEnvironment(row)">Editar</button> <button class="danger" (click)="deleteChild('environment',row.id)">Eliminar</button></td></tr>}</tbody></table></div>

      <h3>Variables</h3><form class="config-form" (ngSubmit)="saveVariable()"><label>Ambiente<select [(ngModel)]="variable.environment" name="varEnvironment">@for(e of environments;track e){<option [value]="e">{{e}}</option>}</select></label><label>Clave<input [(ngModel)]="variable.key" name="varKey" required></label><label>Valor<input [(ngModel)]="variable.value" name="varValue" [type]="variable.secret?'password':'text'"></label><label>Descripción<input [(ngModel)]="variable.description" name="varDescription"></label><label class="check"><input type="checkbox" [(ngModel)]="variable.secret" name="varSecret"> Secreto</label><label class="check"><input type="checkbox" [(ngModel)]="variable.required" name="varRequired"> Requerida</label><button>Guardar variable</button></form>
      <div class="table-wrap"><table><thead><tr><th>Ambiente</th><th>Clave</th><th>Valor</th><th>Secreto</th><th>Requerida</th><th></th></tr></thead><tbody>@for(row of detail()!.variables;track row.id){<tr><td>{{row.environment}}</td><td>{{row.variable_key}}</td><td>{{row.variable_value||'—'}}</td><td>{{row.secret?'Sí':'No'}}</td><td>{{row.required?'Sí':'No'}}</td><td><button class="secondary" (click)="editVariable(row)">Editar</button> <button class="danger" (click)="deleteChild('variable',row.id)">Eliminar</button></td></tr>}</tbody></table></div>

      <h3>Dependencias</h3><form class="config-form" (ngSubmit)="saveDependency()"><label>Tipo<input [(ngModel)]="dependency.type" name="depType" placeholder="DATABASE, API, QUEUE" required></label><label>Nombre<input [(ngModel)]="dependency.name" name="depName" required></label><label>Destino<input [(ngModel)]="dependency.target" name="depTarget"></label><label class="check"><input type="checkbox" [(ngModel)]="dependency.required" name="depRequired"> Requerida</label><button>Guardar dependencia</button></form>
      <div class="table-wrap"><table><thead><tr><th>Tipo</th><th>Nombre</th><th>Destino</th><th>Requerida</th><th></th></tr></thead><tbody>@for(row of detail()!.dependencies;track row.id){<tr><td>{{row.dependency_type}}</td><td>{{row.dependency_name}}</td><td>{{row.target||'—'}}</td><td>{{row.required?'Sí':'No'}}</td><td><button class="secondary" (click)="editDependency(row)">Editar</button> <button class="danger" (click)="deleteChild('dependency',row.id)">Eliminar</button></td></tr>}</tbody></table></div>

      <h3>Versiones</h3><form class="config-form" (ngSubmit)="addVersion()"><label>Versión<input [(ngModel)]="version.version" name="version" required></label><label>Commit<input [(ngModel)]="version.gitCommit" name="gitCommit"></label><label>Imagen<input [(ngModel)]="version.imageTag" name="imageTag"></label><label>Notas<input [(ngModel)]="version.notes" name="notes"></label><button>Registrar versión</button></form>
      <div class="table-wrap"><table><thead><tr><th>Versión</th><th>Commit</th><th>Imagen</th><th>Creada por</th><th>Fecha</th><th></th></tr></thead><tbody>@for(row of detail()!.versions;track row.id){<tr><td>{{row.version}}</td><td>{{row.git_commit||'—'}}</td><td>{{row.image_tag||'—'}}</td><td>{{row.created_by}}</td><td>{{row.created_at}}</td><td><button class="danger" (click)="deleteChild('version',row.id)">Eliminar</button></td></tr>}</tbody></table></div>
    </section>
  }
  `
})
export class ApplicationsCenterComponent implements OnInit {
  readonly applications=signal<ApplicationSummary[]>([]);readonly detail=signal<ApplicationDetail|null>(null);readonly technologies=signal<string[]>([]);readonly busy=signal(false);readonly message=signal('');readonly failed=signal(false);
  readonly environments=['DEVELOPMENT','QA','CERTIFICATION','PRODUCTION'];
  app:any={id:null,code:'',name:'',description:'',repositoryUrl:'',defaultBranch:'main',technology:'SPRING_BOOT',buildTool:'Maven',dockerfilePath:'Dockerfile',contextPath:'.',internalPort:8080,healthPath:'/actuator/health',metricsPath:'/actuator/prometheus',active:true};
  environment:any={id:null,environment:'DEVELOPMENT',branch:'main',publicUrl:'',replicas:1,cpuLimit:'',memoryLimit:'',enabled:true};
  variable:any={id:null,environment:'DEVELOPMENT',key:'',value:'',secret:false,required:false,description:''};
  dependency:any={id:null,type:'DATABASE',name:'',target:'',required:true};version:any={version:'',gitCommit:'',imageTag:'',notes:''};
  constructor(private readonly service:ApplicationsCenterService){}
  ngOnInit():void{this.load();this.service.technologies().subscribe(v=>this.technologies.set(v));}
  load():void{this.service.list().subscribe({next:v=>this.applications.set(v),error:e=>this.notify(e,true)});}
  select(id:string):void{this.service.find(id).subscribe({next:v=>{this.detail.set(v);this.app={id:v.id,code:v.code,name:v.name,description:v.description||'',repositoryUrl:v.repository_url,defaultBranch:v.default_branch,technology:v.technology,buildTool:v.build_tool||'',dockerfilePath:v.dockerfile_path,contextPath:v.context_path,internalPort:v.internal_port,healthPath:v.health_path||'',metricsPath:v.metrics_path||'',active:v.active};},error:e=>this.notify(e,true)});}
  saveApplication():void{this.busy.set(true);this.service.save(this.app).subscribe({next:v=>{this.detail.set(v);this.notify('Aplicación guardada');this.load();},error:e=>this.notify(e,true),complete:()=>this.busy.set(false)});}
  removeApplication(row:ApplicationSummary):void{if(!confirm(`Eliminar ${row.code}?`))return;this.service.delete(row.id).subscribe({next:()=>{if(this.detail()?.id===row.id)this.detail.set(null);this.load();this.notify('Aplicación eliminada');},error:e=>this.notify(e,true)});}
  newApplication():void{this.detail.set(null);this.app={id:null,code:'',name:'',description:'',repositoryUrl:'',defaultBranch:'main',technology:'SPRING_BOOT',buildTool:'Maven',dockerfilePath:'Dockerfile',contextPath:'.',internalPort:8080,healthPath:'/actuator/health',metricsPath:'/actuator/prometheus',active:true};}
  saveEnvironment():void{this.withDetail(this.service.saveEnvironment(this.detail()!.id,this.environment),()=>this.environment={id:null,environment:'DEVELOPMENT',branch:'main',publicUrl:'',replicas:1,cpuLimit:'',memoryLimit:'',enabled:true});}
  saveVariable():void{this.variable.key=this.variable.key.toUpperCase();this.withDetail(this.service.saveVariable(this.detail()!.id,this.variable),()=>this.variable={id:null,environment:'DEVELOPMENT',key:'',value:'',secret:false,required:false,description:''});}
  saveDependency():void{this.withDetail(this.service.saveDependency(this.detail()!.id,this.dependency),()=>this.dependency={id:null,type:'DATABASE',name:'',target:'',required:true});}
  addVersion():void{this.withDetail(this.service.addVersion(this.detail()!.id,this.version),()=>this.version={version:'',gitCommit:'',imageTag:'',notes:''});}
  editEnvironment(v:any):void{this.environment={id:v.id,environment:v.environment,branch:v.branch||'',publicUrl:v.public_url||'',replicas:v.replicas,cpuLimit:v.cpu_limit||'',memoryLimit:v.memory_limit||'',enabled:v.enabled};}
  editVariable(v:any):void{this.variable={id:v.id,environment:v.environment,key:v.variable_key,value:v.secret?'':v.variable_value||'',secret:v.secret,required:v.required,description:v.description||''};}
  editDependency(v:any):void{this.dependency={id:v.id,type:v.dependency_type,name:v.dependency_name,target:v.target||'',required:v.required};}
  deleteChild(type:string,id:string):void{if(!confirm('¿Eliminar este registro?'))return;const appId=this.detail()!.id;const request=type==='environment'?this.service.deleteEnvironment(appId,id):type==='variable'?this.service.deleteVariable(appId,id):type==='dependency'?this.service.deleteDependency(appId,id):this.service.deleteVersion(appId,id);this.withDetail(request);}
  private withDetail(request:any,after?:()=>void):void{this.busy.set(true);request.subscribe({next:(v:ApplicationDetail)=>{this.detail.set(v);after?.();this.notify('Operación completada');},error:(e:any)=>this.notify(e,true),complete:()=>this.busy.set(false)});}
  private notify(value:any,failed=false):void{this.failed.set(failed);this.message.set(typeof value==='string'?value:value?.error?.message||'Operación no completada');this.busy.set(false);}
}
