const fs = require('fs');
const vm = require('vm');
const assert = require('assert/strict');
const path = require('path');
const root = path.resolve(__dirname, '../../nginx-1.18.0/nginx-1.18.0/html/girl-shop');
const html = fs.readFileSync(path.join(root, 'mine.html'), 'utf8');
const script = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)].map(m => m[1]).find(s => s.includes('new Vue'));
let options;
function Vue(value) { options = value; }
Vue.use = () => {};
const context = {Vue, ELEMENT: {}, registerPageUtilities() {}, history:{replaceState(){}},registerFlashRequests() {}, registerBuyerReviews() {}, isLoggedIn: () => true,
  getUser: () => ({id: '7'}),readViewState:()=>({}), navCmd() {}, imgSrc() {}, imgError() {}, goPublish() {},
  STATUS_MAP:{},ORDER_STATUS_MAP: {}, location: {hash:''}, window: {}, console};
vm.runInNewContext(script, context);
(async () => {
  assert.equal(options.data().activeTab,'products');
  const goods=options.computed.filteredGoods.call({goods:[{status:1,id:'a'},{status:0,id:'b'},{status:2,id:'c'}],goodsStatus:'1'});
  assert.equal(goods.length,1);assert.equal(goods[0].id,'a');
  const calls = [];
  const page = {activeTab: 'fans', loadOrders() {calls.push('orders')}, loadFollows() {calls.push('follows')}, loadFans() {calls.push('fans')}};
  page.onTabClick = options.methods.onTabClick.bind(page);
  options.watch.activeTab.call(page);
  assert.deepEqual(calls, ['fans']);
  page.activeTab = 'follows'; options.watch.activeTab.call(page);
  assert.deepEqual(calls, ['fans', 'follows']);
  const data = [{userId: '8', followUserId: '7'}];
  context.request = {get: async () => ({data, total: '1'})};
  const fans = {requestTokens:{fans:0},fanPage: 1, fanPageSize: 10, counts: {followCount: 0, fanCount: 0}};
  await options.methods.loadFans.call(fans);
  assert.equal(fans.fans.length, 1); assert.equal(fans.counts.fanCount, 1); assert.equal(fans.counts.followCount, 0);
  context.request = {get: async () => ({data: [], total: '0'})};
  await options.methods.loadFans.call(fans);
  assert.equal(fans.fans.length, 0); assert.equal(fans.counts.fanCount, 0);
  context.msgError = () => {};
  options.methods.goProducts({userId:'8',followUserId:'7',followNickName:'卖家'});
  assert.equal(context.location.href,'seller.html?id=7');
  context.location.href='unchanged';options.methods.goProducts({followUserId:null});
  assert.equal(context.location.href,'unchanged');
  const separate = fs.readFileSync(path.join(root,'my-follows.html'),'utf8');
  assert.ok(separate.includes("location.replace('mine.html#follows')"));
  const seller = fs.readFileSync(path.join(root,'seller.html'),'utf8');
  for (const match of seller.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)) new vm.Script(match[1]);
  const store=new Map();
  const commonContext={console,URLSearchParams,location:{search:''},sessionStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v),removeItem:k=>store.delete(k)},axios:{create:()=>({interceptors:{request:{use(){}},response:{use(){}}}})}};
  vm.runInNewContext(fs.readFileSync(path.join(root,'js/common.js'),'utf8'),commonContext);
  assert.equal(commonContext.navbarHTML().includes('href="publish.html"'),false);
  commonContext.goPublish();assert.equal(commonContext.location.href,'login.html?next=publish.html');
  store.set('token','test');commonContext.goPublish();assert.equal(commonContext.location.href,'publish.html');
  commonContext.location.search='?next=publish.html';assert.equal(commonContext.loginDestination(),'publish.html');
  commonContext.location.search='?next=https://example.com';assert.equal(commonContext.loginDestination(),'index.html');
  for(const filename of ['index.html','mine.html','publish.html','login.html','orders.html','product-detail.html','trade.html','messages.html'])for(const match of fs.readFileSync(path.join(root,filename),'utf8').matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi))new vm.Script(match[1]);
  assert.equal(commonContext.formatMoney('12'),'12.00');assert.equal(commonContext.formatMoney('oops'),'0.00');
  assert.equal(commonContext.formatDate('2026-10-15T09:03:10'),'2026-10-15 09:03');
  let utilities;
  commonContext.Vue={component(){},mixin(value){utilities=value}};commonContext.registerPageUtilities();
  const actionPage={pendingActions:{},$set(o,k,v){o[k]=v},$delete(o,k){delete o[k]}};
  let release,callsCount=0;const held=new Promise(resolve=>release=resolve);
  const first=utilities.methods.runAction.call(actionPage,'same',async()=>{callsCount++;await held});
  await utilities.methods.runAction.call(actionPage,'same',async()=>callsCount++);assert.equal(callsCount,1);
  release();await first;assert.deepEqual(actionPage.pendingActions,{});
  for(const filename of ['mine.html','product-detail.html','index.html','seller.html','trade.html','messages.html']) {
    const page=fs.readFileSync(path.join(root,filename),'utf8');
    for(const attribute of page.matchAll(/(?:v-if|v-else-if|v-show|v-loading|:[\w.-]+|@[\w.-]+)="([^"]*)"/g)) {
      const expr=attribute[1].replace(/&amp;/g,'&');new vm.Script(attribute[0].startsWith('@')?expr:'('+expr+')',{filename});
    }
  }
  assert.equal(vm.runInNewContext('CONDITION_MAP[6]',commonContext),'九九新');
  assert.equal(vm.runInNewContext('CONDITION_MAP[7]',commonContext),'九五新');
  assert.equal(vm.runInNewContext('CONDITION_OPTIONS.map(o=>o.value).join(",")',commonContext),'1,6,7,2,3,4,5','new grades must appear in condition order without renumbering existing products');
  console.log('PASS: condition display/options, tab loading, fan totals, both seller links and seller page script syntax.');
})().catch(e => {console.error(e); process.exitCode = 1});
