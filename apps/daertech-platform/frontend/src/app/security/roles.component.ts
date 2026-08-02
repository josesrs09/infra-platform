import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PlatformPermission, PlatformRole, RolePayload, SecurityAdminService } from './security-admin.service';

@Component({
  selector: 'app-roles',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="panel">
      <div class="panel-head"><div><h2>Roles</h2><p>Definición de perfiles y permisos administrativos.</p></div><button (click)="reset()">Nuevo</button></div>
      @if(message()){<p [class.error]="failed()" [class.ok]="!failed()">{{message()}}</p>}
      <form class="config-form" (ngSubmit)="save()">
        <label>Código<input [(ngModel)]="form.code" name="code" required></label>
        <label>Nombre<input [(ngModel)]="form.name" name="name" required></label>
        <label>Descripción<input [(ngModel)]="form.description" name="description"></label>
        <label class="check"><input type="checkbox" [(ngModel)]="form.active" name="active"> Activo</label>
        <fieldset><legend>Permisos</legend>@for(permission of permissions();track permission.id){<label class="check"><input type="checkbox" [checked]="hasPermission(permission.id)" (change)="togglePermission(permission.id,$any($event.target).checked)"> {{permission.code}}</label>}</fieldset>
        <button [disabled]="busy()">{{editingId?'Actualizar':'Crear'}}</button>
      </form>
      <div class="table-wrap"><table><thead><tr><th>Código</th><th>Nombre</th><th>Descripción</th><th>Activo</th><th>Acciones</th></tr></thead><tbody>
        @for(role of roles();track role.id){<tr><td>{{role.code}}</td><td>{{role.name}}</td><td>{{role.description||'—'}}</td><td>{{role.active?'Sí':'No'}}</td><td><button class="secondary" (click)="edit(role)">Editar</button> <button class="danger" (click)="remove(role)">Eliminar</button></td></tr>}
      </tbody></table></div>
    </section>
  `
})
export class RolesComponent implements OnInit {
  readonly roles=signal<PlatformRole[]>([]);readonly permissions=signal<PlatformPermission[]>([]);readonly busy=signal(false);readonly message=signal('');readonly failed=signal(false);
  editingId:string|null=null; form:RolePayload={code:'',name:'',description:'',active:true,permissionIds:[]};
  constructor(private readonly service:SecurityAdminService){}
  ngOnInit():void{this.load();this.service.permissions().subscribe(r=>this.permissions.set(r));}
  load():void{this.service.roles().subscribe({next:r=>this.roles.set(r),error:e=>this.notify(e,true)});}
  save():void{this.busy.set(true);const request=this.editingId?this.service.updateRole(this.editingId,this.form):this.service.createRole(this.form);request.subscribe({next:()=>{this.notify('Rol guardado');this.reset();this.load();},error:e=>this.notify(e,true),complete:()=>this.busy.set(false)});}
  edit(role:PlatformRole):void{this.editingId=role.id;this.form={code:role.code,name:role.name,description:role.description||'',active:role.active,permissionIds:[]};}
  remove(role:PlatformRole):void{if(!confirm(`Eliminar el rol ${role.code}?`))return;this.service.deleteRole(role.id).subscribe({next:()=>{this.notify('Rol eliminado');this.load();},error:e=>this.notify(e,true)});}
  reset():void{this.editingId=null;this.form={code:'',name:'',description:'',active:true,permissionIds:[]};}
  hasPermission(id:string):boolean{return this.form.permissionIds.includes(id);}
  togglePermission(id:string,checked:boolean):void{this.form.permissionIds=checked?[...new Set([...this.form.permissionIds,id])]:this.form.permissionIds.filter(x=>x!==id);}
  private notify(value:any,failed=false):void{this.failed.set(failed);this.message.set(typeof value==='string'?value:value?.error?.message||'Operación no completada');this.busy.set(false);}
}
