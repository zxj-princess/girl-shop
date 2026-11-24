function normalizeFlashDraft(draft) {
  return Object.assign({},draft,{flashOpen:draft.flashOpen===true,flash:Object.assign({flashPrice:null,stock:1,beginTime:'',endTime:''},draft.flash||{})})
}
function flashDraftError(draft) {
  if(!draft||!draft.flashOpen)return ''
  const f=draft.flash||{},price=Number(f.flashPrice),sale=Number(draft.price)
  if(!/^[0-9]{1,6}(\.[0-9]{1,2})?$/.test(String(f.flashPrice))||!Number.isFinite(price)||price<=0||price>999999.99||Math.abs(price*100-Math.round(price*100))>0.000001||!Number.isFinite(sale)||price>sale)return '捡漏价须大于0且不高于售价，最多两位小数'
  if(!Number.isInteger(f.stock)||f.stock<1||f.stock>10000)return '捡漏库存须为1至10000的整数'
  const time=value=>/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(value||'')?new Date(value.replace(' ','T')).getTime():NaN
  const begin=time(f.beginTime),end=time(f.endTime)
  if(!Number.isFinite(begin)||!Number.isFinite(end))return '请填写捡漏开始、结束时间'
  if(end<=begin||end<=Date.now())return '结束时间须晚于开始时间和当前时间'
  return ''
}
Vue.component('ai-flash-settings',{
  props:{value:{type:Object,required:true},disabled:Boolean},
  computed:{settings(){return normalizeFlashDraft(this.value).flash}},
  methods:{
    toggle(open){this.$emit('input',Object.assign({},this.value,{flashOpen:open,flash:this.settings}))},
    update(key,value){this.$emit('input',Object.assign({},this.value,{flash:Object.assign({},this.settings,{[key]:value})}))}
  },
  template:`<section class="ai-flash-settings"><div class="ai-flash-heading"><div><strong>限量捡漏</strong><p>开启后，确认发布时生效</p></div><el-switch :value="!!value.flashOpen" :disabled="disabled" @change="toggle" aria-label="开启限量捡漏"></el-switch></div><div v-if="value.flashOpen" class="ai-flash-fields"><label>捡漏价<el-input :value="settings.flashPrice" :disabled="disabled" type="number" min="0.01" max="999999.99" step="0.01" placeholder="填写捡漏价" @input="update('flashPrice',$event)" aria-label="捡漏价"></el-input></label><label>捡漏库存<el-input-number :value="settings.stock" :disabled="disabled" :min="1" :max="10000" :precision="0" controls-position="right" @input="update('stock',$event)" aria-label="捡漏库存"></el-input-number></label><label>开始时间<el-date-picker :value="settings.beginTime" :disabled="disabled" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="选择开始时间" @input="update('beginTime',$event)" aria-label="捡漏开始时间"></el-date-picker></label><label>结束时间<el-date-picker :value="settings.endTime" :disabled="disabled" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="选择结束时间" @input="update('endTime',$event)" aria-label="捡漏结束时间"></el-date-picker></label></div></section>`
})
