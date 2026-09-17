import {obj,list,type Obj} from './view.js';
import {injectFixedArguments} from './action-projection.js';
export interface Act {session_id:string;state_id:number;action_id:string;request_id:string;arguments:Obj;}
export interface PresentedAct {action_id:string;request_id:string;arguments:Obj;}
interface Pending {request:Act;receipt?:Obj;resolve:(value:Obj)=>void;timer:ReturnType<typeof setTimeout>;receiptSequence?:number;}
interface WaitOptions {changed?:(state:Obj)=>boolean;signal?:AbortSignal;}
export class GameSession {
 private latest:Obj={ready:false,actions:[]};private receivedAt=0;private online=false;private sequence=0;
 private pending?:Pending;private remotePending?:string;private used=new Set<string>();private receipts=new Map<string,Obj>();
 private waiters=new Set<()=>void>();private presented?:{session_id:string;state_id:number};
 constructor(private send:(command:Obj)=>void){}
 receive(message:Obj):void {
  ++this.sequence;
  if(message.type==='bridge_ready'){this.remotePending=typeof message.pending_request_id==='string'?message.pending_request_id:undefined;}
  if(message.type==='state'){
   this.online=true;this.latest=message;this.receivedAt=Date.now()-Number(message.bridge_state_age_ms ?? 0);
   const p=this.pending;
   if(p?.receipt && message.session_id===p.request.session_id && Number(message.state_id)>p.request.state_id
     && message.ready===true && this.sequence>(p.receiptSequence ?? this.sequence))this.finish(p,'applied');
  }
  if(['result','error','bridge_error'].includes(String(message.type)) && typeof message.request_id==='string'){
   this.receipts.set(message.request_id,message);if(this.receipts.size>128)this.receipts.delete(this.receipts.keys().next().value!);
   if(this.remotePending===message.request_id)this.remotePending=undefined;
   const p=this.pending;if(p?.request.request_id===message.request_id){
    p.receipt=message;p.receiptSequence=this.sequence;
    if(message.type!=='result' || message.status!=='applied')this.finish(p,'rejected');
   }
  }
  for(const wake of [...this.waiters])wake();
 }
 current():Obj {
  const fresh=this.online && Date.now()-this.receivedAt<5000;
  return {...this.latest,ready:fresh && this.latest.ready===true,actions:fresh?this.latest.actions:[],connection:{status:fresh?'connected':'disconnected_or_stale',age_ms:this.receivedAt?Date.now()-this.receivedAt:null,pending_request_id:this.pending?.request.request_id ?? this.remotePending ?? null}};
 }
 /** Remember exactly the state shown to this MCP client. Public tools can then stay stateless-looking without weakening stale checks. */
 present(state:Obj):Obj {
  if(typeof state.session_id!=='string'||!state.session_id||!Number.isSafeInteger(state.state_id)||Number(state.state_id)<0)throw new Error('State identity unavailable; refresh game state.');
  this.presented={session_id:state.session_id,state_id:Number(state.state_id)};return state;
 }
 assertState(session:string,state:number):Obj {
  const current=this.current();if(current.session_id!==session || current.state_id!==state)throw new Error('Stale state: call sts_get_state; do not repeat the old action.');
  if(obj(current.connection).status!=='connected')throw new Error('Game connection unavailable; reconnect and inspect state.');return current;
 }
 assertPresented():Obj {
  if(!this.presented)throw new Error('No presented state: call sts_get_state first.');
  return this.assertState(this.presented.session_id,this.presented.state_id);
 }
 actPresented(request:PresentedAct,timeout:number):Promise<Obj> {
  const state=this.assertPresented(),offered=list(state.actions);let action_id=request.action_id,action:Obj|undefined;
  const alias=/^a(0|[1-9]\d*)$/.exec(action_id);
  if(alias){
   const index=Number(alias[1]);if(index>=offered.length)throw new Error('Action is not offered in the current ready state.');
   action=obj(offered[index]);const native=action.id;if(typeof native!=='string'||!native)throw new Error('Action is not offered in the current ready state.');action_id=native;
  }else action=obj(offered.find(value=>obj(value).id===action_id));
  if(!action||!Object.keys(action).length)throw new Error('Action is not offered in the current ready state.');
  const argumentsWithPinnedConstants=injectFixedArguments(action.parameters,request.arguments);
  return this.act({session_id:String(state.session_id),state_id:Number(state.state_id),...request,action_id,arguments:argumentsWithPinnedConstants},timeout);
 }
 act(request:Act,timeout:number):Promise<Obj> {
  if(this.used.has(request.request_id)||this.receipts.has(request.request_id))throw new Error('Duplicate request ID: inspect sts_get_request; not resent.');
  if(this.pending||this.remotePending)throw new Error('Busy: an earlier action outcome is pending; inspect sts_get_request.');
  const state=this.assertState(request.session_id,request.state_id);
  if(state.ready!==true || !list(state.actions).some(a=>obj(a).id===request.action_id))throw new Error('Action is not offered in the current ready state.');
  if(this.used.size>=10000)throw new Error('Session request limit reached. Restart MCP connection after inspecting state.');
  this.used.add(request.request_id);
  return new Promise(resolve=>{
   const p:Pending={request,resolve,timer:setTimeout(()=>{
    resolve(this.answer(p,p.receipt?'applied_waiting':'unknown'));
    // Keep the single-flight lock until an authoritative receipt and next state arrive.
   },timeout)};this.pending=p;
   for(const wake of [...this.waiters])wake();
   try{this.send({type:'act',...request});}catch{this.online=false;clearTimeout(p.timer);resolve(this.answer(p,'unknown'));for(const wake of [...this.waiters])wake();}
  });
 }
 private answer(p:Pending,outcome:string):Obj{return {request_id:p.request.request_id,outcome,receipt:p.receipt ?? null,state:this.current(),retry_allowed:false};}
 private finish(p:Pending,outcome:string):void{clearTimeout(p.timer);this.pending=undefined;p.resolve(this.answer(p,outcome));}
 request(id:string):Obj{return {request_id:id,receipt:this.receipts.get(id) ?? null,pending:this.pending?.request.request_id===id || this.remotePending===id,retry_allowed:false};}
 async wait(timeout:number,options:WaitOptions={}):Promise<Obj> {
  const {signal}=options,changed=options.changed ?? ((state:Obj)=>state.ready===true);
  signal?.throwIfAborted();
  if(timeout===0 || changed(this.current()))return this.current();
  await new Promise<void>((resolve,reject)=>{
   let settled=false,freshness:ReturnType<typeof setTimeout>|undefined;
   const done=(error?:unknown)=>{
    if(settled)return;settled=true;clearTimeout(deadline);clearTimeout(freshness);
    this.waiters.delete(wake);signal?.removeEventListener('abort',abort);
    if(error!==undefined)reject(error);else resolve();
   };
   const abort=()=>done(signal?.reason ?? new Error('Wait aborted'));
   const wake=()=>{
    if(settled)return;
    try{
     if(changed(this.current()) || !this.online){done();return;}
     clearTimeout(freshness);
     const remaining=5000-(Date.now()-this.receivedAt);
     if(remaining<=0){done();return;}
     freshness=setTimeout(wake,remaining);
    }catch(error){done(error);}
   };
   // Absolute request deadline; heartbeat only rearms the freshness deadline.
   const deadline=setTimeout(()=>done(),timeout);
   this.waiters.add(wake);signal?.addEventListener('abort',abort,{once:true});
   if(signal?.aborted)abort();else wake();
  });return this.current();
 }
 close():void{this.online=false;this.presented=undefined;if(this.pending){clearTimeout(this.pending.timer);this.pending.resolve(this.answer(this.pending,this.pending.receipt?'applied_waiting':'unknown'));}for(const wake of [...this.waiters])wake();}
}
