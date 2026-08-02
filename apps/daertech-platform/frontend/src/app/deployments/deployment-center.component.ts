import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApplicationsCenterService, PlatformApplication } from '../applications/applications-center.service';
import { Deployment, DeploymentCenterService, DeploymentRequest, DeploymentRuntimeEvent, Registry } from './deployment-center.service';

@Component({
  selector: 'app-deployment-center',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="panel">
      <div class="panel-head">
        <div><h2>Deployment Center</h2><p>Solicitudes, ejecución, health checks, promoción, registry, rollback y blue/green.</p></div>
        <div class="actions"><select [(ngModel)]="environmentFilter" (change)="load()"><option value="">Todos</option>@for(env of environments;track env){<option [value]="env">{{env}}</option>}</select><button (click)="load()">Actualizar</button></div>
      </div>
      @if(message()){<p [class.error]="failed()" [class.ok]="!failed()">{{message()}}</p>}
      <form class="config-form" (ngSubmit)="create()">
        <label>Aplicación<select [(ngModel)]="form.applicationId" name="applicationId" required><option value="">Seleccione</option>@for(app of applications();track app.id){<option [value]="app.id">{{app.code}} — {{app.name}}</option>}</select></label>
        <label>Ambiente<select [(ngModel)]="form.environment" name="environment">@for(env of environments;track env){<option [value]="env">{{env}}</option>}</select></label>
        <label>Versión<input [(ngModel)]="form.version" name="version" required placeholder="1.0.0"></label>
        <label>Rama<input [(ngModel)]="form.branch" name="branch" placeholder="main"></label>
        <label>Motivo<input [(ngModel)]="form.reason" name="reason" required></label>
        <button [disabled]="busy()">Crear solicitud</button>
      </form>
      <div class="table-wrap"><table><thead><tr><th>Aplicación</th><th>Ambiente</th><th>Versión</th><th>Estrategia</th><th>Estado</th><th>Health</th><th>Slot</th><th>Solicitado por</th></tr></thead><tbody>
        @for(item of deployments();track item.id){<tr class="clickable" (click)="select(item.id)"><td>{{item.application_code}}</td><td>{{item.environment}}</td><td>{{item.version}}</td><td>{{item.strategy||'RECREATE'}}</td><td>{{item.status}}</td><td>{{item.health_status||'—'}}</td><td>{{item.active_slot||'—'}}</td><td>{{item.requested_by}}</td></tr>}
      </tbody></table></div>

      @if(selected()){
        <section class="detail-card">
          <div class="panel-head"><div><h3>{{selected()!.application_code}} · {{selected()!.environment}} · {{selected()!.version}}</h3><p>{{selected()!.id}}</p></div>
            <div class="actions"><button (click)="execute()" [disabled]="busy()">Ejecutar</button><button class="secondary" (click)="rollback()" [disabled]="busy()">Rollback</button><button class="secondary" (click)="pushImage()" [disabled]="busy()">Publicar imagen</button></div>
          </div>
          <div class="summary-grid">
            <div><b>Estado</b><span>{{selected()!.status}}</span></div><div><b>Commit</b><span>{{selected()!.git_commit||'—'}}</span></div><div><b>Imagen local</b><span>{{selected()!.image_tag||'—'}}</span></div><div><b>Imagen registry</b><span>{{selected()!.registry_image||'—'}}</span></div><div><b>Health</b><span>{{selected()!.health_message||'—'}}</span></div><div><b>Notificación</b><span>{{selected()!.notification_status||'—'}}</span></div>
          </div>
          <div class="runtime-bar">
            <label>Estrategia<select [(ngModel)]="strategy"><option value="RECREATE">RECREATE</option><option value="ROLLING">ROLLING</option><option value="BLUE_GREEN">BLUE_GREEN</option></select></label>
            <label>Registry<select [(ngModel)]="registryId"><option [ngValue]="null">Sin registry</option>@for(registry of registries();track registry.id){<option [value]="registry.id">{{registry.code}}</option>}</select></label>
            <label>Imagen registry<input [(ngModel)]="registryImage" placeholder="registry/imagen:tag"></label>
            <button (click)="saveStrategy()" [disabled]="busy()">Guardar estrategia</button>
            <label>Promover a<select [(ngModel)]="targetEnvironment">@for(env of environments;track env){<option [value]="env">{{env}}</option>}</select></label>
            <button (click)="promote()" [disabled]="busy()">Promover</button>
            <button class="blue" (click)="switchTraffic('BLUE')" [disabled]="busy()">Activar BLUE</button>
            <button class="green" (click)="switchTraffic('GREEN')" [disabled]="busy()">Activar GREEN</button>
          </div>
          <h3>Pasos</h3>
          <div class="table-wrap"><table><thead><tr><th>#</th><th>Paso</th><th>Estado</th><th>Código</th><th>Salida</th></tr></thead><tbody>@for(step of selected()!.steps||[];track step.id){<tr><td>{{step.step_order}}</td><td>{{step.step_name}}</td><td>{{step.status}}</td><td>{{step.exit_code??'—'}}</td><td><pre>{{step.output||'—'}}</pre></td></tr>}</tbody></table></div>
          <h3>Eventos runtime</h3>
          <div class="table-wrap"><table><thead><tr><th>Evento</th><th>Estado</th><th>Usuario</th><th>Detalle</th><th>Fecha</th></tr></thead><tbody>@for(event of events();track event.id){<tr><td>{{event.event_type}}</td><td>{{event.status}}</td><td>{{event.performed_by||'—'}}</td><td><pre>{{event.details||'—'}}</pre></td><td>{{event.created_at}}</td></tr>}</tbody></table></div>
        </section>
      }
    </section>
  `
})
export class DeploymentCenterComponent implements OnInit {
  readonly deployments=signal<Deployment[]>([]); readonly selected=signal<Deployment|null>(null); readonly events=signal<DeploymentRuntimeEvent[]>([]); readonly applications=signal<PlatformApplication[]>([]); readonly registries=signal<Registry[]>([]); readonly busy=signal(false); readonly message=signal(''); readonly failed=signal(false);
  readonly environments=['DEVELOPMENT','QA','CERTIFICATION','PRODUCTION'];
  environmentFilter=''; strategy='RECREATE'; registryId:string|null=null; registryImage=''; targetEnvironment='QA';
  form:DeploymentRequest={applicationId:'',environment:'DEVELOPMENT',version:'',branch:'main',reason:'Despliegue solicitado desde la plataforma'};
  constructor(private readonly deploymentsApi:DeploymentCenterService,private readonly applicationsApi:ApplicationsCenterService){}
  ngOnInit():void{this.load();this.applicationsApi.list().subscribe(r=>this.applications.set(r));this.deploymentsApi.registries().subscribe({next:r=>this.registries.set(r),error:()=>this.registries.set([])});}
  load():void{this.deploymentsApi.list(this.environmentFilter).subscribe({next:r=>this.deployments.set(r),error:e=>this.notify(e,true)});}
  create():void{this.run(this.deploymentsApi.create(this.form),d=>{this.notify('Solicitud creada');this.form.version='';this.select(d.id);this.load();});}
  select(id:string):void{this.deploymentsApi.detail(id).subscribe({next:d=>{this.selected.set(d);this.strategy=d.strategy||'RECREATE';this.registryId=d.registry_id||null;this.registryImage=d.registry_image||'';this.loadEvents(id);},error:e=>this.notify(e,true)});}
  execute():void{const d=this.selected();if(d)this.run(this.deploymentsApi.execute(d.id),x=>this.afterOperation('Ejecución finalizada',x));}
  rollback():void{const d=this.selected();if(!d)return;const reason=prompt('Motivo del rollback','Rollback manual');if(!reason)return;this.run(this.deploymentsApi.rollback(d.id,reason),x=>this.afterOperation('Rollback solicitado',x));}
  saveStrategy():void{const d=this.selected();if(d)this.run(this.deploymentsApi.setStrategy(d.id,this.strategy,this.registryId,this.registryImage),x=>this.afterOperation('Estrategia actualizada',x));}
  promote():void{const d=this.selected();if(!d)return;this.run(this.deploymentsApi.promote(d.id,this.targetEnvironment,`Promoción desde ${d.environment}`),x=>{this.notify('Promoción creada');this.load();this.select(x.id);});}
  pushImage():void{const d=this.selected();if(d)this.run(this.deploymentsApi.pushImage(d.id),x=>this.afterOperation('Imagen publicada',x));}
  switchTraffic(slot:'BLUE'|'GREEN'):void{const d=this.selected();if(!d||!confirm(`¿Activar el slot ${slot}?`))return;this.run(this.deploymentsApi.switchTraffic(d.id,slot),x=>this.afterOperation(`Tráfico cambiado a ${slot}`,x));}
  private loadEvents(id:string):void{this.deploymentsApi.events(id).subscribe({next:r=>this.events.set(r),error:()=>this.events.set([])});}
  private afterOperation(message:string,d:Deployment):void{this.selected.set(d);this.notify(message);this.load();this.loadEvents(d.id);}
  private run(request:any,next:(value:any)=>void):void{this.busy.set(true);request.subscribe({next,error:(e:any)=>this.notify(e,true),complete:()=>this.busy.set(false)});}
  private notify(value:any,failed=false):void{this.failed.set(failed);this.message.set(typeof value==='string'?value:value?.error?.message||'Operación no completada');this.busy.set(false);}
}
