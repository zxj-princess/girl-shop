const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),path=require('path');
const root=path.resolve(__dirname,'../../nginx-1.18.0/nginx-1.18.0/html/girl-shop');let options,failed=false;const gets=[],reads=[],posts=[];
const ctx={console,URLSearchParams,Map,Set,Event,location:{pathname:'/messages.html',search:'',hash:''},history:{replaceState(){}},window:{innerWidth:1280,addEventListener(){},dispatchEvent(){}},document:{hidden:false},sessionStorage:{getItem:k=>k==='token'?'test':k==='user'?'{"id":"10"}':null,setItem(){}},axios:{create:()=>({interceptors:{request:{use(){}},response:{use(){}}}})},Vue:function(o){options=o},ELEMENT:{}};
ctx.Vue.use=()=>{};ctx.Vue.mixin=()=>{};ctx.Vue.component=()=>{};
vm.runInNewContext(fs.readFileSync(path.join(root,'js/common.js'),'utf8'),ctx);
ctx.get=async(url,config)=>{gets.push({url,config});if(url==='/trade/peers/7')return{data:{id:'7',nickName:'卖家'}};if(url.startsWith('/trade/conversation/')){const after=config.params.after;return{data:{messages:after?[{id:'3',senderId:'7',isRead:0,content:'新消息'}]:[{id:'1',senderId:'7',isRead:0,content:'旧消息'},{id:'2',senderId:'10',isRead:0,content:'已保存消息'}],hasMore:false}}}return{data:url==='/trade/message-counts'?{messages:0,notices:0,newProducts:0}:[]}};
ctx.put=async(url,body)=>{reads.push(body.ids);return{}};ctx.post=async(url,body)=>{posts.push(body);if(!failed){failed=true;throw Error('模拟响应丢失')}return{data:{messageId:'4'}}};
vm.runInNewContext('request.get=get;request.put=put;request.post=post',ctx);
vm.runInNewContext(fs.readFileSync(path.join(root,'js/messages.js'),'utf8'),ctx);
const p=options.data();p.$set=(o,k,v)=>o[k]=v;p.$nextTick=f=>f();p.$refs={};for(const[k,v]of Object.entries(options.methods))p[k]=v.bind(p);for(const[k,v]of Object.entries(options.computed))Object.defineProperty(p,k,{get:()=>v.call(p)});
(async()=>{
 await p.selectPeer('7');assert.equal(p.messages.length,2);assert.deepEqual([...reads[0]],['1']);
 await p.loadMessages(false,true,p.selection);const query=gets.filter(q=>q.url==='/trade/conversation/7').at(-1);assert.equal(query.config.params.after,'2');assert.equal(p.messages.length,3);assert.deepEqual([...reads.at(-1)],['3']);
 ctx.document.hidden=true;p.messages.push({id:'5',senderId:'7',isRead:0});const before=reads.length;await p.markVisibleRead();assert.equal(reads.length,before);ctx.document.hidden=false;
 p.text='发送确认测试';await p.send();assert.equal(p.sendFailed,true);assert.equal(p.text,'发送确认测试');await p.send();assert.equal(posts[0].clientMessageId,posts[1].clientMessageId);assert.equal(p.sendFailed,false);assert.equal(p.text,'');
 console.log('PASS: increment-only catchup, scoped visible read receipts, hidden-page protection and stable retry identifiers.');
})().catch(e=>{console.error(e);process.exitCode=1});
