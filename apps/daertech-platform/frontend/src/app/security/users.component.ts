import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PlatformRole, PlatformUser, SecurityAdminService, UserPayload } from './security-admin.service';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="panel">
      <div class="panel-head"><div><h2>Usuarios</h2><p>Administración de cuentas, estados y roles asignados.</p></div><button (click)="reset()">Nuevo</button></div>
      @if (message()) { <p [class.error]="failed()" [class.ok]="!failed()">{{ message() }}</p> }
      <form class="config-form" (ngSubmit)="save()">
        <label>Usuario<input [(ngModel)]="form.username" name="username" required></label>
        <label>Correo<input [(ngModel)]="form.email" name="email" type="email" required></label>
        <label>Nombre completo<input [(ngModel)]="form.fullName" name="fullName" required></label>
        <label>Contraseña<input [(ngModel)]="form.password" name="password" type="password" [required]="!editingId"></label>
        <label class="check"><input type="checkbox" [(ngModel)]="form.enabled" name="enabled"> Habilitado</label>
        <label class="check"><input type="checkbox" [(ngModel)]="form.locked" name="locked"> Bloqueado</label>
        <fieldset><legend>Roles</legend>@for(role of roles(); track role.id){<label class="check"><input type="checkbox" [checked]="hasRole(role.id)" (change)="toggleRole(role.id,$any($event.target).checked)"> {{role.code}}</label>}</fieldset>
        <button [disabled]="busy()">{{ editingId ? 'Actualizar' : 'Crear' }}</button>
      </form>
      <div class="table-wrap"><table><thead><tr><th>Usuario</th><th>Correo</th><th>Nombre</th><th>Habilitado</th><th>Bloqueado</th><th>Último acceso</th><th>Acciones</th></tr></thead><tbody>
        @for(user of users(); track user.id){<tr><td>{{user.username}}</td><td>{{user.email}}</td><td>{{user.full_name}}</td><td>{{user.enabled?'Sí':'No'}}</td><td>{{user.locked?'Sí':'No'}}</td><td>{{user.last_login_at||'—'}}</td><td><button class="secondary" (click)="edit(user)">Editar</button> <button class="danger" (click)="remove(user)">Eliminar</button></td></tr>}
      </tbody></table></div>
    </section>
  `
})
export class UsersComponent implements OnInit {
  readonly users = signal<PlatformUser[]>([]); readonly roles = signal<PlatformRole[]>([]); readonly busy = signal(false); readonly message = signal(''); readonly failed = signal(false);
  editingId: string | null = null;
  form: UserPayload = { username:'', email:'', fullName:'', password:'', enabled:true, locked:false, roleIds:[] };
  constructor(private readonly service: SecurityAdminService) {}
  ngOnInit(): void { this.load(); this.service.roles().subscribe(r=>this.roles.set(r)); }
  load(): void { this.service.users().subscribe({next:r=>this.users.set(r),error:e=>this.notify(e,true)}); }
  save(): void { this.busy.set(true); const request=this.editingId?this.service.updateUser(this.editingId,this.form):this.service.createUser(this.form); request.subscribe({next:()=>{this.notify('Usuario guardado');this.reset();this.load();},error:e=>this.notify(e,true),complete:()=>this.busy.set(false)}); }
  edit(user: PlatformUser): void { this.editingId=user.id; this.form={username:user.username,email:user.email,fullName:user.full_name,password:'',enabled:user.enabled,locked:user.locked,roleIds:[]}; }
  remove(user: PlatformUser): void { if(!confirm(`Eliminar el usuario ${user.username}?`)) return; this.service.deleteUser(user.id).subscribe({next:()=>{this.notify('Usuario eliminado');this.load();},error:e=>this.notify(e,true)}); }
  reset(): void { this.editingId=null; this.form={username:'',email:'',fullName:'',password:'',enabled:true,locked:false,roleIds:[]}; }
  hasRole(id:string): boolean { return this.form.roleIds.includes(id); }
  toggleRole(id:string,checked:boolean): void { this.form.roleIds=checked?[...new Set([...this.form.roleIds,id])]:this.form.roleIds.filter(x=>x!==id); }
  private notify(value:any,failed=false): void { this.failed.set(failed);this.message.set(typeof value==='string'?value:value?.error?.message||'Operación no completada');this.busy.set(false); }
}
