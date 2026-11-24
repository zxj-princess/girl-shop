const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert/strict');
const root = path.resolve(__dirname, '../../nginx-1.18.0/nginx-1.18.0/html/girl-shop');
const context = {
  URL, URLSearchParams,
  location: {}, document: {referrer: ''},
  sessionStorage: {getItem: () => null},
  axios: {create: () => ({interceptors: {request: {use() {}}, response: {use() {}}}})}
};
vm.runInNewContext(fs.readFileSync(path.join(root, 'js/common.js'), 'utf8'), context);
function visit(page, query = '') {
  context.location = {pathname: '/' + page, search: query, hash: '', href: 'http://localhost:8090/' + page + query};
  context.document.referrer = '';
}

// Every actual business page that renders the shared header gets a working, explicit home link.
for (const file of fs.readdirSync(root).filter(f => f.endsWith('.html'))) {
  const html = fs.readFileSync(path.join(root, file), 'utf8');
  for (const m of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)) new vm.Script(m[1], {filename: file});
  if (!html.includes('navbarHTML()')) continue;
  visit(file);
  const navigation = context.pageNavigationHTML();
  if (file === 'index.html') { assert.equal(navigation, ''); continue; }
  assert.ok(navigation.includes('href="index.html"'), `${file}: home must be reachable`);
  assert.ok(navigation.includes('返回首页'), `${file}: home must have an explicit label`);
  for (const m of navigation.matchAll(/href="([^"?#]+)/g)) assert.ok(fs.existsSync(path.join(root, m[1])), `${file}: missing destination ${m[1]}`);
}
visit('ai-publish.html');
assert.ok(context.pageNavigationHTML().includes('href="trade.html#drafts"'));
assert.ok(context.navbarHTML().includes('<a class="logo" href="index.html"'), 'logo must be keyboard accessible');
visit('address.html');
assert.ok(context.pageNavigationHTML().includes('href="mine.html#orders"'));
visit('product-detail.html', '?id=8&from=' + encodeURIComponent('my-favorite.html?page=2'));
assert.equal(context.navigationReturnTarget(), 'my-favorite.html?page=2');
assert.ok(context.pageNavigationHTML().includes('返回我的收藏'));
visit('product-detail.html', '?id=8&from=' + encodeURIComponent('flash.html?categoryId=3&page=2'));
assert.equal(context.navigationReturnTarget(), 'flash.html?categoryId=3&page=2');
assert.ok(context.pageNavigationHTML().includes('返回捡漏列表'));
visit('seller.html');
context.document.referrer = 'http://localhost:8090/messages.html?peer=8#chats';
assert.equal(context.navigationReturnTarget(), 'messages.html?peer=8#chats');
assert.ok(context.pageNavigationHTML().includes('返回消息'));
context.document.referrer = 'https://example.com/mine.html';
assert.equal(context.navigationReturnTarget(), 'index.html');
for (const from of ['https://example.com', '//example.com', 'javascript:alert(1)', '../index.html', 'seller.html?id=8', 'index.html/../../evil']) {
  visit('seller.html', '?from=' + encodeURIComponent(from));
  assert.equal(context.navigationReturnTarget(), 'index.html', from);
}
visit('seller.html', '?from=' + encodeURIComponent('messages.html?peer=" onmouseover="alert(1)'));
assert.ok(!context.pageNavigationHTML().includes('peer=" onmouseover='), 'source query must not inject HTML attributes');
let utilities;
context.Vue = {component() {}, mixin(options) {utilities = options;}};
context.registerPageUtilities();
visit('my-favorite.html', '?page=2'); context.location.hash = '#saved';
utilities.methods.goDetailWithReturn('a&b');
assert.equal(context.location.href, 'product-detail.html?id=a%26b&from=my-favorite.html%3Fpage%3D2%23saved');
console.log('PASS: home links on all business pages, draft/order shortcuts, context return with filters, safe fallback and escaped destinations.');
