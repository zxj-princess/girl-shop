const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),path=require('path');
const root=path.resolve(__dirname,'../../nginx-1.18.0/nginx-1.18.0/html/girl-shop');
const html=fs.readFileSync(path.join(root,'product-detail.html'),'utf8');
const script=[...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)].map(m=>m[1]).find(s=>s.includes('new Vue'));
let options;let nextCount=5;let fail=false;const calls=[];
const context={console,URLSearchParams,location:{search:'?id=2'},Vue:function(o){options=o},ELEMENT:{},registerPageUtilities(){},registerBuyerReviews(){},navCmd(){},imgSrc:x=>x,isLoggedIn:()=>true,msgSuccess(){},
 request:{post:async url=>{calls.push(url);if(fail)throw Error('network');return {data:{favoriteCount:nextCount,favorited:true}}},delete:async url=>{calls.push(url);if(fail)throw Error('network');return {data:{favoriteCount:nextCount,favorited:false}}}}};
context.Vue.use=()=>{};vm.runInNewContext(script,context);
const page=options.data();for(const[k,v]of Object.entries(options.methods))page[k]=v.bind(page);page.$set=(o,k,v)=>o[k]=v;
(async()=>{
 page.product={id:2,views:12,favoriteCount:4};await page.toggleFavorite();assert.equal(page.isFavorited,true);assert.equal(page.product.favoriteCount,5);assert.equal(page.product.views,12,'favorite must not count as a new visit');
 nextCount=4;await page.toggleFavorite();assert.equal(page.isFavorited,false);assert.equal(page.product.favoriteCount,4);
 const original=context.console;context.console={error(){}};fail=true;await page.toggleFavorite();context.console=original;
 assert.equal(page.isFavorited,false);assert.equal(page.product.favoriteCount,4,'failed mutation cannot fake a new total');assert.equal(page.favLoading,false);
 page.favLoading=true;const before=calls.length;await page.toggleFavorite();assert.equal(calls.length,before,'duplicate click is blocked while pending');
 console.log('PASS: favorite server totals update on add/remove, failure preserves count, views unchanged and pending clicks blocked.');
})().catch(e=>{console.error(e);process.exitCode=1});
