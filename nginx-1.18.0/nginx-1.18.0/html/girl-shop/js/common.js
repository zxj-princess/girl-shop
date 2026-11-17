/* ===== 粉系衣橱 公共 JS ===== */

// Element-UI 消息提示（全局可用）
function msgSuccess(msg) { ELEMENT.Notification.success({ title: '成功', message: msg, offset: 80 }) }
function msgError(msg) { ELEMENT.Notification.error({ title: '提示', message: msg, offset: 80 }) }

/* ---- 登录态：用 sessionStorage（每个标签页独立），保证新标签页打开是未登录状态 ---- */

// 获取本地 token
function getToken() { return sessionStorage.getItem('token') || '' }

// 获取本地用户信息
function getUser() {
  try { return JSON.parse(sessionStorage.getItem('user') || '{}') } catch (e) { return {} }
}

// 设置登录信息
function setLogin(token, user) {
  sessionStorage.setItem('token', token)
  sessionStorage.setItem('user', JSON.stringify(user))
}

// 清除登录信息
function clearLogin() {
  sessionStorage.removeItem('token')
  sessionStorage.removeItem('user')
}

// 是否已登录
function isLoggedIn() { return !!getToken() }

// 封装请求（基于 axios，baseURL=/api）
const request = axios.create({ baseURL: '/api', timeout: 15000 })

// 请求拦截：自动带 token
request.interceptors.request.use(config => {
  const token = getToken()
  if (token) config.headers['authorization'] = token
  return config
})

// 响应拦截：统一处理
request.interceptors.response.use(
  res => {
    const data = res.data
    if (data.success === false) {
      if (res.headers && res.headers['x-request-id']) console.warn('请求失败，编号：', res.headers['x-request-id'])
      if(!res.config || !res.config.quiet) msgError(data.errorMsg || '操作失败')
      return Promise.reject(data)
    }
    return data
  },
  err => {
    if (err.response && err.response.headers && err.response.headers['x-request-id']) console.warn('请求失败，编号：', err.response.headers['x-request-id'])
    if (err.response && err.response.status === 401) { clearLogin(); location.href = 'login.html' }
    else if(!err.config || !err.config.quiet) msgError(err.message || '网络异常')
    return Promise.reject(err)
  }
)

// 图片路径处理
const PLACEHOLDER = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="300" height="300"><rect width="100%" height="100%" fill="#fce4ec"/><text x="50%" y="50%" font-size="18" fill="#f06292" text-anchor="middle" dominant-baseline="middle">🌸 粉系衣橱</text></svg>')

function imgSrc(images) {
  if (!images) return PLACEHOLDER
  const first = images.split(',')[0].trim()
  if (!first) return PLACEHOLDER
  if (first.startsWith('http') || first.startsWith('/')) return first
  return '/' + first
}

// 图片加载失败兜底：替换为占位图（历史订单快照可能指向已不存在的图片文件）
function imgError(e) {
  if (e && e.target && e.target.src !== PLACEHOLDER) e.target.src = PLACEHOLDER
}

// 条件映射
const CONDITION_MAP = { 1: '全新', 2: '九成新', 3: '八成新', 4: '七成新', 5: '六成以下' }
const STATUS_MAP = { 0: '下架', 1: '在售', 2: '已售出' }
const ORDER_STATUS_MAP = { 0: '待付款', 1: '待发货', 2: '待收货', 3: '已完成', 4: '已关闭' }

// 获取导航栏 HTML
function navbarHTML() {
  const u = getUser()
  let userArea = ''
  if (isLoggedIn()) {
    userArea = `
      <el-dropdown @command="navCmd" trigger="click">
        <span class="account-name">
          ${u.nickName || '我的'} <i class="el-icon-arrow-down"></i>
        </span>
        <el-dropdown-menu slot="dropdown">
          <el-dropdown-item command="user">资料设置</el-dropdown-item>
          <el-dropdown-item command="mine">我的主页</el-dropdown-item>
          <el-dropdown-item command="my-publish">我的发布</el-dropdown-item>
          <el-dropdown-item command="my-favorite">我的收藏</el-dropdown-item>
          <el-dropdown-item command="notice">消息中心</el-dropdown-item>
          <el-dropdown-item command="orders">我的订单</el-dropdown-item>
          <el-dropdown-item command="reports">我的举报</el-dropdown-item>
          <el-dropdown-item command="blocks">黑名单</el-dropdown-item>
          <el-dropdown-item command="drafts">商品草稿箱</el-dropdown-item>
          <el-dropdown-item command="address">收货地址</el-dropdown-item>
          <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </el-dropdown>`
  } else {
    userArea = `<a class="login-btn" href="login.html">登录</a>`
  }
  return `
    <div class="gs-navbar">
      <span class="logo" onclick="location.href='index.html'">🌸 粉系衣橱</span>
      <nav class="nav-menu">
        <a href="index.html" class="${/\/(?:index.html)?$/.test(location.pathname||'')?'nav-active':''}">首页</a>
        <a href="flash.html" class="${(location.pathname||'').endsWith('/flash.html')?'nav-active':''}">捡漏</a>
        <message-nav></message-nav>
        <a href="mine.html" class="${/\/(mine|trade|user|publish)\.html$/.test(location.pathname||'')?'nav-active':''}">我的</a>
      </nav>
      <div class="user-area">${userArea}</div>
    </div>`
}

// 导航命令处理
function navCmd(cmd) {
  if(cmd==='trade'){location.href='mine.html#orders';return}
  if(cmd==='drafts'||cmd==='reports'||cmd==='blocks'){location.href='trade.html#'+cmd;return}
  const map = { user: 'user.html', mine: 'mine.html', 'my-publish': 'mine.html#products', 'my-favorite': 'my-favorite.html', 'my-follows': 'mine.html#follows', notice: 'messages.html#notices', address: 'address.html', orders: 'mine.html#orders' }
  if (cmd === 'logout') {
    request.post('/user/logout').catch(() => {}).finally(() => { clearLogin(); location.href = 'login.html' })
  } else if (map[cmd]) location.href = map[cmd]
}

function goPublish() {
  location.href = isLoggedIn() ? 'publish.html' : 'login.html?next=publish.html'
}
function loginDestination() {
  const next = new URLSearchParams(location.search).get('next')
  return /^(?:publish|ai-publish|listing-preview)\.html(?:\?draft=[0-9]+)?$/.test(next||'') || /^messages\.html(?:[?#].*)?$/.test(next||'') ? next : 'index.html'
}
function goMessages(peer,product) {
  const query=new URLSearchParams();if(peer)query.set('peer',String(peer));if(product)query.set('product',String(product))
  const target='messages.html'+(query.toString()?'?'+query:'')
  location.href=isLoggedIn()?target:'login.html?next='+encodeURIComponent(target)
}

// 底部 HTML
function footerHTML() {
  return '<div class="gs-footer"><p>🌸 粉系衣橱 — 二手服装交易平台</p><p>© 2026 Girl Shop. All rights reserved.</p></div>'
}

// A timeout means processing continues; the durable request remains visible in My Orders.
async function waitFlashOrder(requestId) {
  for (let attempt = 0; attempt < 30; attempt++) {
    const res = await request.get('/order/flash/request/' + encodeURIComponent(requestId))
    if (res.data.status === 'SUCCESS') return res.data.orderId
    if (res.data.status === 'FAILED' || res.data.status === 'CANCELLED') {
      msgError(res.data.errorMessage || '捡漏订单未完成，库存已释放')
      throw new Error('Flash request failed')
    }
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  msgSuccess('订单仍在处理中，可在我的订单查看结果')
  location.href = 'mine.html#orders'
  throw new Error('Flash request pending')
}

function registerFlashRequests() {
  Vue.component('flash-requests', {
    data() { return { items: [], timer: null, error: false } },
    template: `<section v-if="items.length || error" class="gs-card" style="padding:16px;margin:16px 0">
      <strong>捡漏请求记录</strong>
      <div v-if="error">请求记录暂时无法加载，请稍后刷新。</div>
      <div v-for="item in items" :key="item.requestId" style="padding:8px 0">
        {{ item.requestId }} · {{ label(item.status) }}
        <span v-if="item.errorMessage"> · {{ item.errorMessage }}</span>
        <a v-if="item.status === 'SUCCESS'" href="mine.html#orders"> 查看订单</a>
      </div></section>`,
    methods: {
      label(s) { return {PENDING:'处理中',SUCCESS:'已创建',FAILED:'失败，库存已释放',CANCELLED:'已关闭'}[s] || s },
      async load() {
        try { const r = await request.get('/order/flash/requests'); this.items = r.data; this.error = false }
        catch (e) { this.error = true; clearInterval(this.timer) }
      }
    },
    mounted() { this.load(); this.timer = setInterval(() => this.load(), 5000) },
    beforeDestroy() { clearInterval(this.timer) }
  })
}

function registerBuyerReviews() {
  Vue.component('buyer-review-button', {
    props:['orderId','productId','reviewedState','canReviewState'],
    data() {return {visible:false,busy:false,eligible:false,reviewed:false,resolvedId:null,score:5,content:'',images:[],headers:{authorization:getToken()}}},
    template:`<span>
      <el-button v-if="eligible" size="mini" type="primary" plain @click="visible=true">去评价</el-button>
      <el-tag v-else-if="reviewed" size="mini" type="info">已评价</el-tag>
      <el-dialog title="买家评价" :visible.sync="visible" width="min(480px,92vw)" append-to-body>
        <el-rate v-model="score" style="margin-bottom:18px"></el-rate>
        <el-input type="textarea" v-model="content" :rows="4" maxlength="500" show-word-limit placeholder="分享商品和收货体验"></el-input>
        <p>添加图片（选填，最多5张）</p>
        <el-upload action="/api/upload/product" name="files" :headers="headers" accept="image/jpeg,image/png" list-type="picture-card" :limit="5" :on-success="uploaded" :on-remove="removed"><i class="el-icon-plus"></i></el-upload>
        <span slot="footer"><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="busy" @click="submit">提交评价</el-button></span>
      </el-dialog>
    </span>`,
    methods:{
      async refresh() {
        if(!isLoggedIn())return
        try {
          const res=await request.get(this.orderId?'/review/order/'+encodeURIComponent(this.orderId):'/review/eligible/'+encodeURIComponent(this.productId))
          this.eligible=!!res.data.canReview;this.reviewed=!!res.data.reviewed;this.resolvedId=res.data.orderId
        }catch(e){this.eligible=false}
      },
      uploaded(res) {if(res.success)this.images=this.images.concat(res.data||[]);else msgError(res.errorMsg||'图片上传失败')},
      removed(file) {const urls=file.response&&file.response.data||[];this.images=this.images.filter(u=>!urls.includes(u))},
      async submit() {
        if(this.busy)return
        if(!this.score || !this.content.trim()){msgError('请填写评分和评价内容');return}
        this.busy=true
        try{
          await request.post('/review',{orderId:this.resolvedId,score:this.score,content:this.content.trim(),images:this.images})
          this.visible=false;this.eligible=false;this.reviewed=true;msgSuccess('评价成功');this.$emit('submitted')
        }catch(e){await this.refresh()}
        finally{this.busy=false}
      }
    },
    mounted(){if(typeof this.reviewedState==='boolean'){this.reviewed=this.reviewedState;this.eligible=!!this.canReviewState;this.resolvedId=this.orderId}else this.refresh()},
    watch:{reviewedState(v){this.reviewed=!!v;this.eligible=!!this.canReviewState}}
  })
}

function formatMoney(value) { const number=Number(value);return Number.isFinite(number)?number.toFixed(2):'0.00' }
function formatDate(value) {
  if(!value)return ''
  const date=new Date(String(value).replace(' ','T'))
  if(Number.isNaN(date.getTime()))return String(value).replace('T',' ').slice(0,16)
  const pad=n=>String(n).padStart(2,'0')
  return date.getFullYear()+'-'+pad(date.getMonth()+1)+'-'+pad(date.getDate())+' '+pad(date.getHours())+':'+pad(date.getMinutes())
}
function registerPageUtilities() {
  Vue.component('message-nav',{
    data(){return {count:0,active:(location.pathname||'').endsWith('/messages.html'),unsubscribe:null}},
    template:'<a href="messages.html" :class="{\'nav-active\':active}" class="message-nav">消息<sup v-if="count>0" class="message-badge" :aria-label="count+\'条未读消息\'">{{count>99?\'99+\':count}}</sup></a>',
    mounted(){if(isLoggedIn()){this.unsubscribe=shopRealtime.subscribe(event=>{if(event.type==='counts')this.count=Number(event.counts.messages||0)+Number(event.counts.notices||0)+Number(event.counts.newProducts||0)});shopRealtime.counts().catch(()=>{})}},
    beforeDestroy(){if(this.unsubscribe)this.unsubscribe()}
  })
  Vue.mixin({data(){return {pendingActions:{}}},methods:{formatMoney,formatDate,
    async runAction(key,task){if(this.pendingActions[key])return;this.$set(this.pendingActions,key,true);try{return await task()}finally{this.$delete(this.pendingActions,key)}},
    goDetailWithReturn(id){location.href='product-detail.html?id='+encodeURIComponent(String(id))+'&from='+encodeURIComponent(location.pathname.split('/').pop()+location.search+location.hash)}
  }})
}
function readViewState(name) {try{return JSON.parse(sessionStorage.getItem('shop:view:'+name+':'+String(getUser().id||'guest'))||'{}')}catch(e){return {}}}

// One socket per page, shared by the navigation and message view.
const shopRealtime = (() => {
  const listeners=new Set();let socket=null,status='idle',retry=null,heartbeat=null,fallback=null,countsTimer=null,countsPromise=null,attempt=0,connecting=false,lastIncoming=0,epoch='',revision='',installed=false,suspended=false;
  function emit(event){for(const listener of listeners){try{listener(event)}catch(e){console.error('实时页面更新失败',e)}}}
  function fallbackTick(){fallback=null;if(status==='online'||!listeners.size)return;if(!document.hidden){counts().catch(()=>{});emit({type:'fallback'})}fallback=setTimeout(fallbackTick,60000)}
  function setStatus(value){status=value;emit({type:'connection',status:value});if(value==='online'){clearTimeout(fallback);fallback=null}else if(!fallback&&listeners.size)fallback=setTimeout(fallbackTick,60000)}
  function counts(){if(countsPromise)return countsPromise;countsPromise=request.get('/trade/message-counts',{quiet:true}).then(r=>{emit({type:'counts',counts:r.data});return r.data}).finally(()=>{countsPromise=null});return countsPromise}
  function queueCounts(){clearTimeout(countsTimer);countsTimer=setTimeout(()=>{if(!document.hidden)counts().catch(()=>{})},100)}
  function reconnect(){if(retry||!listeners.size||suspended)return;setStatus('reconnecting');const delay=Math.min(30000,1000*Math.pow(2,Math.min(attempt++,5)))*(.8+Math.random()*.4);retry=setTimeout(()=>{retry=null;connect()},delay)}
  async function connect(){
    if(connecting||socket||!listeners.size||suspended||!isLoggedIn())return;
    if(typeof WebSocket==='undefined'){setStatus('offline');return}
    if(document.hidden||(typeof navigator!=='undefined'&&navigator.onLine===false)){reconnect();return}
    connecting=true;setStatus('connecting');
    try{
      const r=await request.post('/trade/realtime-ticket',null,{quiet:true});if(!listeners.size||suspended)return;
      const ws=new WebSocket((location.protocol==='https:'?'wss:':'ws:')+'//'+location.host+'/api/ws/messages?ticket='+encodeURIComponent(r.data));socket=ws;lastIncoming=Date.now();
      const handshake=setTimeout(()=>{if(socket===ws&&status!=='online')ws.close()},10000);
      ws.onopen=()=>{heartbeat=setInterval(()=>{if(socket!==ws)return;if(Date.now()-lastIncoming>60000){ws.close();return}if(ws.readyState===1)ws.send(JSON.stringify({type:'PING'}))},25000)};
      ws.onmessage=e=>{
        if(socket!==ws)return;lastIncoming=Date.now();let event;try{event=JSON.parse(e.data)}catch(error){ws.close();return}
        if(event.type==='READY'){clearTimeout(handshake);attempt=0;epoch=event.epoch;revision=event.revision;setStatus('online');queueCounts();emit({type:'resync'});return}
        if(event.type==='PONG'){if(epoch!==event.epoch||revision!==event.revision){epoch=event.epoch;revision=event.revision;if(!document.hidden){queueCounts();emit({type:'resync'})}}return}
        epoch=event.epoch;revision=event.revision;if(!document.hidden){emit(event);queueCounts()}
      };
      ws.onerror=()=>{ws.close()};
      ws.onclose=e=>{clearTimeout(handshake);clearInterval(heartbeat);if(socket!==ws)return;socket=null;if(e.code===1008&&isLoggedIn()){clearLogin();location.href='login.html';return}reconnect()};
    }catch(e){reconnect()}finally{connecting=false}
  }
  function stop(){clearTimeout(retry);clearTimeout(fallback);clearTimeout(countsTimer);clearInterval(heartbeat);retry=null;fallback=null;const old=socket;socket=null;if(old)old.close();status='idle'}
  function install(){if(installed)return;installed=true;window.addEventListener('online',()=>{clearTimeout(retry);retry=null;connect()});document.addEventListener('visibilitychange',()=>{if(!document.hidden){connect();emit({type:'resync'});queueCounts()}});window.addEventListener('pagehide',()=>{suspended=true;stop()});window.addEventListener('pageshow',()=>{suspended=false;if(listeners.size)connect()})}
  return {subscribe(listener){listeners.add(listener);listener({type:'connection',status});install();connect();return()=>{listeners.delete(listener);if(!listeners.size)stop()}},counts,get status(){return status}};
})()
