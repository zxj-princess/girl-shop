const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),path=require('path');
const root=path.resolve(__dirname,'../../nginx-1.18.0/nginx-1.18.0/html/girl-shop');
let options,sent=false,fail=false;const posts=[],queries=[];
const ctx={console,URLSearchParams,Map,Set,Event,CustomEvent:class{constructor(type,options){this.detail=options.detail}},location:{pathname:'/messages.html',search:'',hash:''},history:{replaceState(){}},window:{innerWidth:1280,dispatchEvent(){}},document:{hidden:false},sessionStorage:{getItem:k=>k==='token'?'test':k==='user'?'{"id":"10"}':null,setItem(){}},axios:{create:()=>({interceptors:{request:{use(){}},response:{use(){}}}})},Vue:function(o){options=o},ELEMENT:{}};
ctx.Vue.use=()=>{};ctx.Vue.mixin=()=>{};ctx.Vue.component=()=>{};
vm.runInNewContext(fs.readFileSync(path.join(root,'js/common.js'),'utf8'),ctx);
ctx.handle=async(url,body,config)=>{
 queries.push({url,config});
 if(url.endsWith("/product-owner"))return{data:{ownerId:"7"}};
 if(url.endsWith("/products"))return{data:[{id:"2",userId:"7",title:"卖家商品"}],total:1};
 if(url==='/trade/messages'){posts.push(body);if(fail)throw Error('模拟网络失败');if(body.productId)sent=true;return{}}
 if(url.includes('/product-context/'))return{data:{sent}};
 if(url.startsWith('/product/detail/'))return{data:{id:'2',userId:'7',title:'咨询商品',price:30}};
 if(url.startsWith('/trade/peers/'))return{data:{id:url.split('/').pop(),nickName:'卖家'}};
 if(url==='/trade/message-counts')return{data:{messages:0,notices:0,newProducts:0}};
 return{data:[],total:0};
};
vm.runInNewContext('request.get=(url,config)=>handle(url,null,config);request.post=(url,body)=>handle(url,body)',ctx);
vm.runInNewContext(fs.readFileSync(path.join(root,'js/messages.js'),'utf8'),ctx);
function page(){const p=options.data();p.$set=(o,k,v)=>o[k]=v;p.$nextTick=f=>f();p.$refs={};for(const[k,v]of Object.entries(options.methods))p[k]=v.bind(p);for(const[k,v]of Object.entries(options.computed))Object.defineProperty(p,k,{get:()=>v.call(p)});return p;}
(async()=>{
 const p=page();await p.selectPeer('7','2');assert.equal(p.entryProduct.id,'2');assert.equal(p.productCard.id,'2');p.text='第一次问';await p.send();assert.equal(posts[0].autoProduct,true);assert.equal(p.productCard,null);assert.equal(p.entryProduct.id,'2');
 p.text='接着问';await p.send();assert.equal(posts[1].productId,null);
 await p.openProducts();assert.equal(p.pickerOwnerId,'7');assert.equal(queries.at(-1).config.params.owner,'7');
 const seller=page();seller.user={id:'7',nickName:'卖家'};await seller.selectPeer('10');await seller.openProducts();assert.equal(seller.pickerOwnerId,'7');assert.equal(queries.at(-1).config.params.owner,'7');seller.chooseProduct(seller.pickerProducts[0]);await seller.send();assert.equal(posts.at(-1).peerId,'10');assert.equal(posts.at(-1).productId,'2');
 const refreshed=page();await refreshed.selectPeer('7','2');assert.equal(refreshed.entryProduct.id,'2');assert.equal(refreshed.productCard,null);
 refreshed.chooseProduct({id:'2',title:'主动选择'});assert.equal(refreshed.manualProduct,true);await refreshed.send();assert.equal(posts.at(-1).content,'');assert.equal(posts.at(-1).productId,'2');assert.equal(posts.at(-1).autoProduct,false);
 fail=true;refreshed.chooseProduct({id:'2'});refreshed.text='失败后保留';await refreshed.send();assert.equal(refreshed.productCard.id,'2');assert.equal(refreshed.text,'失败后保留');
 await refreshed.selectPeer('8');assert.equal(refreshed.productCard,null);assert.equal(refreshed.entryProduct,null);
 console.log('PASS: auto reference once, refresh deduplication, explicit product-only send, failed draft retention and peer isolation.');
})().catch(e=>{console.error(e);process.exitCode=1});
