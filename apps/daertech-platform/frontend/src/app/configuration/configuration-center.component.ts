import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  ConfigurationCenterService,
  ConfigurationHistory,
  ConfigurationItem,
  ConfigurationPayload,
  ConnectivityRequest,
  ConnectivityResult
} from './configuration-center.service';

@Component({
  selector: 'app-configuration-center',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="panel">
      <div class="panel-head">
        <div>
          <h2>Configuration Center</h2>
          <p>Parámetros por ambiente, secretos cifrados, validación, exportación, historial y rollback.</p>
        </div>
        <div class="actions">
          <select [(ngModel)]="environment" (change)="load()">
            @for(env of environments; track env){<option [value]="env">{{env}}</option>}
          </select>
          <button class="secondary" (click)="download('ENV')">Exportar .env</button>
          <button class="secondary" (click)="download('YAML')">Exportar YAML</button>
        </div>
      </div>

      @if(message()){<p [class.error]="failed()" [class.ok]="!failed()">{{message()}}</p>}

      <form class="config-form" (ngSubmit)="save()">
        <label>Categoría<input [(ngModel)]="form.category" name="category" required></label>
        <label>Clave<input [(ngModel)]="form.key" name="key" required></label>
        <label>Tipo<select [(ngModel)]="form.valueType" name="valueType">@for(type of valueTypes;track type){<option [value]="type">{{type}}</option>}</select></label>
        <label>Valor<input [(ngModel)]="form.value" name="value" [type]="form.secret?'password':'text'" required></label>
        <label>Descripción<input [(ngModel)]="form.description" name="description"></label>
        <label>Validación regex<input [(ngModel)]="form.validationRule" name="validationRule"></label>
        <label>Motivo<input [(ngModel)]="form.reason" name="reason" required></label>
        <label class="check"><input type="checkbox" [(ngModel)]="form.secret" name="secret"> Secreto</label>
        <label class="check"><input type="checkbox" [(ngModel)]="form.active" name="active"> Activo</label>
        <button [disabled]="busy()">Guardar configuración</button>
      </form>

      <section class="validator">
        <h3>Validar conectividad</h3>
        <div class="inline">
          <select [(ngModel)]="validation.type">@for(type of validationTypes;track type){<option [value]="type">{{type}}</option>}</select>
          <input [(ngModel)]="validation.host" placeholder="Host o dominio">
          <input [(ngModel)]="validation.port" type="number" placeholder="Puerto">
          <input [(ngModel)]="validation.path" placeholder="/health">
          <button (click)="validate()" [disabled]="busy()">Validar</button>
        </div>
        @if(validationResult()){<p [class.ok]="validationResult()?.success" [class.error]="!validationResult()?.success">{{validationResult()?.message}} @if(validationResult()?.elapsedMs!==undefined){· {{validationResult()?.elapsedMs}} ms}</p>}
      </section>

      <div class="table-wrap"><table><thead><tr><th>Categoría</th><th>Clave</th><th>Valor</th><th>Tipo</th><th>Secreto</th><th>Activo</th><th>Versión</th><th>Acciones</th></tr></thead><tbody>
        @for(item of items();track item.id){<tr><td>{{item.category}}</td><td>{{item.key}}</td><td>{{item.value}}</td><td>{{item.valueType}}</td><td>{{item.secret?'Sí':'No'}}</td><td>{{item.active?'Sí':'No'}}</td><td>{{item.version}}</td><td><button class="secondary" (click)="select(item)">Editar</button> <button class="secondary" (click)="showHistory(item)">Historial</button></td></tr>}
      </tbody></table></div>

      @if(selectedItem()){
        <section class="detail-card">
          <div class="panel-head"><div><h3>Historial de {{selectedItem()?.key}}</h3><p>Ambiente {{selectedItem()?.environment}} · versión actual {{selectedItem()?.version}}</p></div><button class="secondary" (click)="closeHistory()">Cerrar</button></div>
          <div class="table-wrap"><table><thead><tr><th>Operación</th><th>Versión</th><th>Motivo</th><th>Fecha</th><th>Estado</th><th>Acción</th></tr></thead><tbody>
            @for(row of history();track row.id){<tr><td>{{row.operation}}</td><td>{{row.version}}</td><td>{{row.reason||'—'}}</td><td>{{row.changed_at}}</td><td>{{row.success?'Correcto':'Fallido'}}</td><td><button class="secondary" (click)="rollback(row)" [disabled]="busy()||row.version===selectedItem()?.version">Restaurar</button></td></tr>}
          </tbody></table></div>
        </section>
      }
    </section>
  `
})
export class ConfigurationCenterComponent implements OnInit {
  readonly environments=['DEVELOPMENT','QA','CERTIFICATION','PRODUCTION'];
  readonly valueTypes=['STRING','NUMBER','BOOLEAN','URL','JSON','PASSWORD','TOKEN','CERTIFICATE'];
  readonly validationTypes=['HTTP','HTTPS','TCP','POSTGRESQL','MYSQL','REDIS','RABBITMQ','MQTT','SMTP','MINIO','REST','SOAP','TELEGRAM'];
  readonly items=signal<ConfigurationItem[]>([]);
  readonly history=signal<ConfigurationHistory[]>([]);
  readonly selectedItem=signal<ConfigurationItem|null>(null);
  readonly validationResult=signal<ConnectivityResult|null>(null);
  readonly busy=signal(false); readonly message=signal(''); readonly failed=signal(false);
  environment='PRODUCTION';
  form:ConfigurationPayload={category:'GENERAL',key:'',value:'',secret:false,environment:'PRODUCTION',valueType:'STRING',description:'',validationRule:'',active:true,reason:'Actualización administrativa'};
  validation:ConnectivityRequest={type:'HTTP',host:'localhost',port:8080,path:'/api/v1/actuator/health',scheme:'http',method:'GET',timeoutMs:5000};

  constructor(private readonly service:ConfigurationCenterService){}
  ngOnInit():void{this.load();}

  load():void{
    this.service.list(this.environment).subscribe({next:r=>this.items.set(r),error:e=>this.notify(e,true)});
  }

  save():void{
    this.busy.set(true);
    const payload={...this.form,environment:this.environment,key:this.form.key.trim().toUpperCase()};
    this.service.save(payload).subscribe({next:()=>{this.notify('Configuración guardada');this.reset();this.load();},error:e=>this.notify(e,true),complete:()=>this.busy.set(false)});
  }

  select(item:ConfigurationItem):void{
    this.form={category:item.category,key:item.key,value:'',secret:item.secret,environment:item.environment,valueType:item.valueType,description:item.description||'',validationRule:'',active:item.active,reason:'Actualización administrativa'};
  }

  showHistory(item:ConfigurationItem):void{
    this.selectedItem.set(item);
    this.service.history(item.id).subscribe({next:r=>this.history.set(r),error:e=>this.notify(e,true)});
  }

  closeHistory():void{this.selectedItem.set(null);this.history.set([]);}

  rollback(row:ConfigurationHistory):void{
    const item=this.selectedItem(); if(!item)return;
    if(!confirm(`Restaurar ${item.key} a la versión ${row.version}?`))return;
    this.busy.set(true);
    this.service.rollback(item.id,row.version,`Rollback manual a versión ${row.version}`).subscribe({next:r=>{this.notify('Rollback completado');this.selectedItem.set(r);this.load();this.showHistory(r);},error:e=>this.notify(e,true),complete:()=>this.busy.set(false)});
  }

  validate():void{
    this.busy.set(true);this.validationResult.set(null);
    const payload={...this.validation,port:this.validation.port?Number(this.validation.port):null};
    this.service.validate(payload).subscribe({next:r=>this.validationResult.set(r),error:e=>this.notify(e,true),complete:()=>this.busy.set(false)});
  }

  download(format:'ENV'|'YAML'):void{
    this.service.export(this.environment,format).subscribe({next:blob=>{const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`daertech-${this.environment.toLowerCase()}.${format==='YAML'?'yaml':'env'}`;a.click();URL.revokeObjectURL(url);},error:e=>this.notify(e,true)});
  }

  reset():void{this.form={category:'GENERAL',key:'',value:'',secret:false,environment:this.environment,valueType:'STRING',description:'',validationRule:'',active:true,reason:'Actualización administrativa'};}
  private notify(value:any,failed=false):void{this.failed.set(failed);this.message.set(typeof value==='string'?value:value?.error?.message||'Operación no completada');this.busy.set(false);}
}
