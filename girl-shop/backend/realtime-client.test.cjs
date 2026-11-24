const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),path=require('path');
const source=fs.readFileSync(path.resolve(__dirname,'../../nginx-1.18.0/nginx-1.18.0/html/girl-shop/js/common.js'),'utf8');
let now=0,next=1;const timers=new Map(),sockets=[],requests=[],events=[],store=new Map([['token','main-login-token']]);
function schedule(fn,ms,interval=0){const id=next++;timers.set(id,{fn,at:now+ms,interval});return id}
function advance(ms){const end=now+ms;for(let i=0;i<10000;i++){const due=[...timers].filter(([,t])=>t.at<=end).sort((a,b)=>a[1].at-b[1].at)[0];if(!due)break;now=due[1].at;timers.delete(due[0]);if(due[1].interval)timers.set(due[0],{...due[1],at:now+due[1].interval});due[1].fn()}now=end}
class Socket{constructor(url){this.url=url;this.readyState=0;this.sent=[];sockets.push(this)}send(text){this.sent.push(JSON.parse(text))}close(){this.readyState=3;if(this.onclose)this.onclose({code:1006})}}
const ctx={console,URLSearchParams,Date:{now:()=>now},Math:Object.assign(Object.create(Math),{random:()=>.5}),WebSocket:Socket,location:{protocol:'http:',host:'localhost:8090'},window:{addEventListener(){}},document:{hidden:false,addEventListener(){}},navigator:{onLine:true},sessionStorage:{getItem:k=>store.get(k)||null,removeItem:k=>store.delete(k)},setTimeout:(f,n)=>schedule(f,n),clearTimeout:id=>timers.delete(id),setInterval:(f,n)=>schedule(f,n,n),clearInterval:id=>timers.delete(id),axios:{create:()=>({interceptors:{request:{use(){}},response:{use(){}}},post:async url=>{requests.push(url);return{data:'one-use-ticket-'+sockets.length}},get:async url=>{requests.push(url);return{data:{messages:2,notices:1,newProducts:0}}}})}};
vm.runInNewContext(source,ctx);ctx.record=e=>events.push(e);
const flush=async()=>{for(let i=0;i<10;i++)await Promise.resolve()};
(async()=>{
 vm.runInNewContext('var stopRealtime=shopRealtime.subscribe(record)',ctx);await flush();assert.equal(sockets.length,1);assert.ok(!sockets[0].url.includes('main-login-token'));assert.ok(sockets[0].url.includes('/api/ws/messages?ticket='));
 let ws=sockets[0];ws.readyState=1;ws.onopen();ws.onmessage({data:JSON.stringify({type:'READY',epoch:'a',revision:'0'})});advance(100);await flush();const initial=requests.filter(p=>p==='/trade/message-counts').length;
 advance(20000);await flush();assert.equal(requests.filter(p=>p==='/trade/message-counts').length,initial,'healthy idle connection must not poll MySQL APIs');
 advance(5000);assert.equal(ws.sent[0].type,'PING');ws.onmessage({data:JSON.stringify({type:'PONG',epoch:'a',revision:'1'})});assert.ok(events.some(e=>e.type==='resync'));
 ws.close();advance(1000);await flush();assert.equal(sockets.length,2);const before=events.filter(e=>e.type==='resync').length;ws=sockets[1];ws.readyState=1;ws.onopen();ws.onmessage({data:JSON.stringify({type:'READY',epoch:'b',revision:'0'})});assert.ok(events.filter(e=>e.type==='resync').length>before);
 ws.close();const fallbackBefore=events.filter(e=>e.type==='fallback').length;for(let i=0;i<65;i++){advance(1000);await flush()}assert.ok(events.filter(e=>e.type==='fallback').length>fallbackBefore,'repeated connection failures must not postpone degraded checks forever');
 vm.runInNewContext('stopRealtime()',ctx);assert.ok(sockets.every(s=>s.readyState===3));
 console.log('PASS: one-use-ticket URL, no healthy idle polling, heartbeat revision recovery, reconnect resync, degraded fallback and cleanup.');
})().catch(e=>{console.error(e);process.exitCode=1});
