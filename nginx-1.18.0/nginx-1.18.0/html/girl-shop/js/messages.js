Vue.use(ELEMENT)
registerPageUtilities()
new Vue({
  el:'#app',
  data(){return {tab:location.hash==='#notices'?'notices':'chats',user:getUser(),counts:{messages:0,notices:0,newProducts:0},contacts:[],recentContacts:[],contactsFailed:false,peerId:'',peer:null,peerLoading:false,peerFailed:false,unavailable:false,messages:[],hasOlder:false,olderLoading:false,syncFailed:false,text:'',imageDrafts:{},uploadingImage:false,draftTexts:{},productCard:null,entryProduct:null,manualProduct:false,contextFailed:false,pickerOpen:false,pickerOwnerId:'',pickerProducts:[],pickerLoading:false,pickerFailed:false,pickerPage:1,pickerStatus:1,pickerTotal:0,pickerVersion:0,sending:false,composing:false,poll:null,unsubscribe:null,syncTimer:null,connection:'connecting',needsRefresh:false,reconcilePending:false,reading:false,messageAttempt:null,sendFailed:false,refreshing:false,selection:0,noticePage:1,noticeTotal:0,inbox:[],inboxFailed:false,isMobile:window.innerWidth<=700}},
  computed:{
    displayContacts(){return [...this.recentContacts.filter(c=>!this.contacts.some(existing=>String(existing.id)===String(c.id))),...this.contacts]},
    canCompose(){return !this.sending&&!this.uploadingImage&&!!this.peer&&!this.peer.cannotSend&&!this.peerLoading&&!this.peerFailed&&!this.unavailable},
    noticeCount(){return Number(this.counts.notices||0)+Number(this.counts.newProducts||0)}
  },
  async mounted(){
    if(!isLoggedIn()){location.href='login.html?next='+encodeURIComponent('messages.html'+location.search+location.hash);return}
    try{this.recentContacts=JSON.parse(sessionStorage.getItem('shop:recent-chats:'+this.user.id)||'[]').slice(0,20)}catch(e){}
    this.resize=()=>{this.isMobile=window.innerWidth<=700};window.addEventListener('resize',this.resize)
    this.hashChanged=()=>{const tab=location.hash==='#notices'?'notices':'chats';if(this.tab!==tab){this.tab=tab;this.refresh(false)}};window.addEventListener('hashchange',this.hashChanged)
    this.unsubscribe=shopRealtime.subscribe(event=>{
      if(event.type==='connection')this.connection=event.status
      else if(event.type==='counts')this.counts=event.counts
      else if(event.type==='READ_CHANGED'){
        if(String(event.peerId)===this.peerId){const ids=new Set(event.ids||[]);this.messages.forEach(m=>{if(ids.has(String(m.id)))this.$set(m,'isRead',1)})}
        this.scheduleSync(false)
      }else if(event.type==='resync')this.scheduleSync(true)
      else if(['MESSAGE_CHANGED','STATE_CHANGED','fallback'].includes(event.type))this.scheduleSync(false)
    })
    const q=new URLSearchParams(location.search)
    await this.loadContacts(false)
    if(q.get('peer'))await this.selectPeer(q.get('peer'),q.get('product'))
    await this.refresh(false)

  },
  beforeDestroy(){if(this.unsubscribe)this.unsubscribe();clearTimeout(this.syncTimer);window.removeEventListener('resize',this.resize);window.removeEventListener('hashchange',this.hashChanged)},
  methods:{
    navCmd,imgSrc,imgError,
    async uploadChatImages(e){
      const id=this.peerId,files=Array.from(e.target.files||[]);e.target.value='';
      if(!this.canCompose||!files.length)return;
      const existing=this.imageDrafts[id]||[];
      if(existing.length+files.length>5||files.some(f=>!['image/jpeg','image/png'].includes(f.type)||f.size>5*1024*1024)){msgError('每条消息最多5张JPG/PNG图片，每张不超过5MB');return}
      this.uploadingImage=true;
      try{const data=new FormData();files.forEach(f=>data.append('files',f));const r=await request.post('/upload/product',data);this.$set(this.imageDrafts,id,existing.concat(r.data||[]))}catch(e){msgError('图片上传失败，请重试')}finally{this.uploadingImage=false}
    },
    badge(n){return Number(n)>99?'99+':Number(n)},initial(name){return (name||'衣').charAt(0)},
    shortTime(value){if(!value)return '';const d=new Date(String(value).replace(' ','T'));if(Number.isNaN(d.getTime()))return '';return d.toDateString()===new Date().toDateString()?formatDate(value).slice(-5):`${d.getMonth()+1}/${d.getDate()}`},
    async loadContacts(quiet){try{this.contacts=(await request.get('/trade/contacts',{quiet})).data||[];this.contactsFailed=false}catch(e){this.contactsFailed=true}},
    async loadCounts(){try{this.counts=await shopRealtime.counts()}catch(e){}},
    scheduleSync(reconcile){this.reconcilePending=this.reconcilePending||reconcile;clearTimeout(this.syncTimer);this.syncTimer=setTimeout(()=>{const full=this.reconcilePending;this.reconcilePending=false;this.refresh(true,full)},120)},
    switchTab(tab){this.tab=tab;location.hash=tab==='notices'?'notices':'chats';this.refresh(false)},
    async selectPeer(id,product){
      if(this.peerId)this.$set(this.draftTexts,this.peerId,this.text)
      const version=++this.selection;this.peerId=String(id);this.peer=null;this.messages=[];this.productCard=null;this.entryProduct=null;this.manualProduct=false;this.contextFailed=false;this.pickerOpen=false;this.pickerOwnerId='';++this.pickerVersion;this.text=this.draftTexts[this.peerId]||'';this.peerLoading=true;this.peerFailed=false;this.unavailable=false;this.hasOlder=false;this.syncFailed=false
      const q=new URLSearchParams();q.set('peer',this.peerId);if(product)q.set('product',String(product));history.replaceState(null,'','messages.html?'+q+'#chats');this.tab='chats'
      try{
        const r=await request.get('/trade/peers/'+encodeURIComponent(id),{quiet:true});if(version!==this.selection)return;this.peer=r.data;this.recentContacts=[{...r.data,lastMessage:'',lastTime:'',unread:0},...this.recentContacts.filter(c=>String(c.id)!==String(id))].slice(0,20);try{sessionStorage.setItem('shop:recent-chats:'+this.user.id,JSON.stringify(this.recentContacts))}catch(e){}
        if(product){try{const p=(await request.get('/product/detail/'+encodeURIComponent(product),{quiet:true})).data;if(version!==this.selection)return;if(p&&[String(this.user.id),String(id)].includes(String(p.userId))){this.entryProduct=p;try{const context=await request.get('/trade/peers/'+encodeURIComponent(id)+'/product-context/'+encodeURIComponent(p.id),{quiet:true});if(version!==this.selection)return;if(!context.data.sent)this.productCard=p}catch(e){this.contextFailed=true}}}catch(e){}}
        await this.loadMessages(false,false,version)
      }catch(e){if(version!==this.selection)return;if(e&&e.errorMsg==='该用户已不可用')this.unavailable=true;else this.peerFailed=true}
      finally{if(version===this.selection){this.peerLoading=false;this.$nextTick(()=>this.resizeComposer())}}
    },
    backToList(){++this.selection;if(this.peerId)this.$set(this.draftTexts,this.peerId,this.text);this.peerId='';this.peer=null;this.peerLoading=false;this.productCard=null;this.entryProduct=null;this.manualProduct=false;this.pickerOpen=false;this.pickerOwnerId='';++this.pickerVersion;this.messages=[];history.replaceState(null,'','messages.html#chats');this.loadContacts(true)},
    async loadMessages(older,quiet,version,reconcile){
      const id=this.peerId;if(!id||!this.peer)return;const historyEl=this.$refs.history;const previousHeight=historyEl?historyEl.scrollHeight:0;const previousTop=historyEl?historyEl.scrollTop:0;const nearBottom=!historyEl||previousHeight-previousTop-historyEl.clientHeight<100;const lastId=this.messages.length?this.messages[this.messages.length-1].id:null
      let after=!older&&!reconcile&&lastId?lastId:0;const before=older&&this.messages.length?this.messages[0].id:0
      try{
        for(let page=0;page<10;page++){
          const response=await request.get('/trade/conversation/'+encodeURIComponent(id),{params:{before,after},quiet});const payload=response.data||{};const list=Array.isArray(payload)?payload:payload.messages||[];const more=!!payload.hasMore
          if(id!==this.peerId||(version!=null&&version!==this.selection))return
          if(older||!after)this.hasOlder=more
          const combined=new Map(this.messages.map(m=>[String(m.id),m]));list.forEach(m=>combined.set(String(m.id),m));this.messages=Array.from(combined.values()).sort((a,b)=>String(a.id).length-String(b.id).length||String(a.id).localeCompare(String(b.id)))
          if(!after||!more||!list.length)break
          after=list[list.length-1].id;if(page===9)this.needsRefresh=true
        }
        this.syncFailed=false
        await new Promise(resolve=>this.$nextTick(resolve))
        const el=this.$refs.history;if(el){if(older)el.scrollTop=previousTop+el.scrollHeight-previousHeight;else if(nearBottom||lastId==null)el.scrollTop=el.scrollHeight}
        await this.markVisibleRead()
      }catch(e){if(id===this.peerId)this.syncFailed=true}
    },
    async markVisibleRead(){
      if(this.reading||document.hidden||this.tab!=='chats'||!this.peerId)return
      const id=this.peerId;const unread=this.messages.filter(m=>String(m.senderId)===id&&!Number(m.isRead));if(!unread.length)return
      this.reading=true
      try{for(let offset=0;offset<unread.length;offset+=100){if(document.hidden||this.peerId!==id||this.tab!=='chats')break;const batch=unread.slice(offset,offset+100);await request.put('/trade/conversation/'+encodeURIComponent(id)+'/read',{ids:batch.map(m=>m.id)},{quiet:true});if(this.peerId===id)batch.forEach(m=>this.$set(m,'isRead',1))}}catch(e){this.syncFailed=true}finally{this.reading=false}
    },
    async loadOlder(){if(this.olderLoading)return;this.olderLoading=true;try{await this.loadMessages(true,false,this.selection)}finally{this.olderLoading=false}},
    async refresh(quiet,reconcile){if(document.hidden)return;if(this.refreshing){this.needsRefresh=true;this.reconcilePending=this.reconcilePending||!!reconcile;return}this.refreshing=true;try{await this.loadContacts(quiet);if(this.tab==='notices')await this.loadInbox(quiet);else if(this.peer&&this.peerId){try{const id=this.peerId;const r=await request.get('/trade/peers/'+encodeURIComponent(id),{quiet:true});if(id===this.peerId)this.peer=r.data}catch(e){}await this.loadMessages(false,quiet,this.selection,!!reconcile)}await this.loadCounts()}finally{this.refreshing=false;if(this.needsRefresh){this.needsRefresh=false;this.scheduleSync(this.reconcilePending)}}},
    resizeComposer(){const el=this.$refs.composerInput;if(el){el.style.height='auto';el.style.height=Math.max(56,Math.min(120,el.scrollHeight))+'px'}},
    composerKey(e){if(e.key==='Enter'&&!e.shiftKey&&!e.isComposing&&!this.composing&&e.keyCode!==229){e.preventDefault();this.send()}},
    async openProducts(){
      if(!this.canCompose)return;const id=this.peerId,version=++this.pickerVersion;this.pickerOpen=true;this.pickerProducts=[];this.pickerTotal=0;this.pickerPage=1;this.pickerStatus=1;this.pickerOwnerId='';this.pickerLoading=true;this.pickerFailed=false
      try{const r=await request.get('/trade/peers/'+encodeURIComponent(id)+'/product-owner',{params:{product:this.entryProduct?this.entryProduct.id:0},quiet:true});if(version!==this.pickerVersion||id!==this.peerId)return;this.pickerOwnerId=String(r.data&&r.data.ownerId||id);await this.loadProducts()}catch(e){if(version===this.pickerVersion){this.pickerFailed=true;this.pickerLoading=false}}
    },
    changeProductOwner(){this.pickerProducts=[];this.pickerTotal=0;this.pickerPage=1;this.loadProducts()},
    async loadProducts(){if(!this.pickerOwnerId){await this.openProducts();return}const id=this.peerId,owner=this.pickerOwnerId,version=++this.pickerVersion;this.pickerLoading=true;this.pickerFailed=false;try{const r=await request.get('/trade/peers/'+encodeURIComponent(id)+'/products',{params:{owner,page:this.pickerPage,status:this.pickerStatus},quiet:true});if(version!==this.pickerVersion||id!==this.peerId||owner!==this.pickerOwnerId)return;this.pickerProducts=r.data||[];this.pickerTotal=Number(r.total)||0;const last=Math.max(1,Math.ceil(this.pickerTotal/12));if(this.pickerPage>last){this.pickerPage=last;await this.loadProducts()}}catch(e){if(version===this.pickerVersion)this.pickerFailed=true}finally{if(version===this.pickerVersion)this.pickerLoading=false}},
    chooseProduct(p){if(!this.canCompose)return;this.productCard=p;this.manualProduct=true;this.pickerOpen=false},
    removeProduct(){this.productCard=null;this.manualProduct=false},
    productStatus(p){return p.status===1&&Number(p.stock)>0?'在售':p.status===2?'已售出':'已下架'},
    async send(){
      if(this.sending||!this.canCompose||(!this.text.trim()&&!this.productCard&&!(this.imageDrafts[this.peerId]||[]).length))return
      const id=this.peerId,version=this.selection,text=this.text.trim(),productId=this.productCard&&this.productCard.id,autoProduct=!!productId&&!this.manualProduct
      const images=(this.imageDrafts[id]||[]).slice();const signature=JSON.stringify({id,text,productId,autoProduct,images});if(!this.messageAttempt||this.messageAttempt.signature!==signature){const key=typeof crypto!=='undefined'&&crypto.randomUUID?crypto.randomUUID():Date.now().toString(36)+'-'+Math.random().toString(36).slice(2)+'-'+Math.random().toString(36).slice(2);this.messageAttempt={signature,id:key}}
      const clientMessageId=this.messageAttempt.id;this.sending=true;this.sendFailed=false
      try{await request.post('/trade/messages',{peerId:id,content:text,productId,autoProduct,clientMessageId,images});if(this.messageAttempt&&this.messageAttempt.id===clientMessageId)this.messageAttempt=null;if(id===this.peerId&&version===this.selection){this.text='';this.$set(this.imageDrafts,id,[]);this.removeProduct();this.$set(this.draftTexts,id,'');await this.loadMessages(false,false,this.selection);this.$nextTick(()=>{this.resizeComposer();if(this.$refs.history)this.$refs.history.scrollTop=this.$refs.history.scrollHeight})}await this.loadContacts(true)}catch(e){this.sendFailed=true}finally{this.sending=false}
    },
    async moreCommand(cmd){if(cmd==='report'){location.href='trade.html?reportUser='+encodeURIComponent(this.peerId)+'#reports';return}if(cmd==='blocks'){location.href='trade.html#blocks';return}if(cmd==='block'){const id=this.peerId;try{await this.$confirm(this.peer.blocked?'解除后可恢复发送私信，是否继续？':'拉黑后双方无法继续发送私信，是否继续？','会话设置');if(this.peer.blocked)await request.delete('/trade/blocks/'+id);else await request.put('/trade/blocks/'+id);const r=await request.get('/trade/peers/'+id);if(this.peerId===id)this.peer=r.data}catch(e){}}},
    reportMessage(m){location.href='trade.html?reportMessage='+encodeURIComponent(m.id)+'#reports'},
    async loadInbox(quiet){try{const r=await request.get('/trade/inbox',{params:{page:this.noticePage},quiet:quiet===true});this.inbox=r.data||[];this.noticeTotal=Number(r.total)||0;this.inboxFailed=false;const last=Math.max(1,Math.ceil(this.noticeTotal/20));if(this.noticePage>last){this.noticePage=last;await this.loadInbox(quiet)}}catch(e){this.inboxFailed=true}},
    async readNotice(n){try{await request.put('/trade/inbox/'+n.kind+'/'+n.id+'/read');n.isRead=1;await this.loadCounts()}catch(e){}},
    async readAll(){return this.runAction('read-all',async()=>{try{await request.put('/trade/inbox/read-all');await this.loadInbox();await this.loadCounts()}catch(e){}})},
    async openNotice(n){try{await this.readNotice(n)}catch(e){console.error(e)}if(n.orderId)location.href='trade.html?order='+encodeURIComponent(n.orderId)+'#orders';else if(n.productId)location.href='product-detail.html?id='+encodeURIComponent(n.productId)}
  }
})
