import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { captureSession, isCurrentSession, invalidateSession, staleSessionError } from '../src/utils/session.js'
import { parseFinancialJson } from '../src/utils/financialJson.js'
import axios from 'axios'

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8')
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function storage() {
  const values = new Map([['token', 'A']])
  globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
  invalidateSession()
  return globalThis.localStorage
}
function profileHarness() {
  const local = storage(), requests = [], writes = [], listeners = {}, reloads = { count: 0 }
  const source = read('../src/composables/useCurrentUser.js').replace(/^import[^\r\n]*\r?\n/gm, '').replace('export function useCurrentUser', 'function useCurrentUser')
  const queue = into => () => { const d = deferred(); into.push(d); return d.promise }
  const api = new Function('computed','reactive','ref','localStorage','window','document','i18n','getUserInfo','removeUserAvatar','updateUserProfile','uploadUserAvatar','captureSession','isCurrentSession','invalidateSession','staleSessionError', `${source};return useCurrentUser()`)(f => ({ get value() { return f() } }), o => o, value => ({ value }), local, { addEventListener(name,callback) { listeners[name]=callback }, location: { reload() {reloads.count++} } }, { documentElement: { setAttribute() {} } }, { global: { locale: {}, t: x => x } }, queue(requests), queue(writes), queue(writes), queue(writes), captureSession, isCurrentSession, invalidateSession, staleSessionError)
  return { api, local, requests, writes, listeners, reloads }
}

test('old account response cannot replace the new profile or dismiss its loading state', async () => {
  const { api, local, requests } = profileHarness()
  const old = api.loadProfile().catch(error => error)
  local.setItem('token', 'B'); api.clearProfile(); const current = api.loadProfile()
  requests[0].resolve({ data: { username: 'A', role: 'ADMIN' } })
  assert.equal((await old).code, 'STALE_SESSION'); assert.equal(api.loading.value, true)
  requests[1].resolve({ data: { username: 'B', role: 'USER' } }); await current
  assert.equal(api.profile.username, 'B'); assert.equal(api.profile.role, 'USER')
})
test('older forced profile refresh cannot overwrite the latest refresh', async () => {
  const { api, requests } = profileHarness()
  const old = api.loadProfile(true).catch(error => error); const current = api.loadProfile(true)
  requests[1].resolve({ data: { username: 'new' } }); await current
  requests[0].resolve({ data: { username: 'old' } }); await old
  assert.equal(api.profile.username, 'new'); assert.equal(api.loaded.value, true)
})
test('old avatar and nickname completions cannot change a new session', async () => {
  for (const action of ['uploadAvatar', 'clearAvatar', 'saveNickname']) {
    const { api, local, writes } = profileHarness()
    const pending = api[action]('old').catch(error => error)
    local.setItem('token', 'B'); api.clearProfile(); api.profile.avatar = 'B.png'; api.profile.nickname = 'B'
    writes[0].resolve({ data: { avatarUrl: 'A.png' } })
    assert.equal((await pending).code, 'STALE_SESSION')
    assert.equal(api.profile.avatar, 'B.png'); assert.equal(api.profile.nickname, 'B')
  }
})
test('old preference failure does not roll back a new user theme or language', async () => {
  for (const [action, field, value] of [['setTheme', 'theme', 'dark'], ['setLanguage', 'language', 'ja']]) {
    const { api, local, writes } = profileHarness()
    const pending = api[action](value).catch(error => error)
    local.setItem('token', 'B'); api.clearProfile(); api.profile[field] = 'new-session-value'
    writes[0].reject(new Error('old request failed')); await pending
    assert.equal(api.profile[field], 'new-session-value')
  }
})
test('actual bill loader takes a query snapshot and ignores old success and failure', async () => {
  const source = read('../src/views/Bill.vue')
  const code = source.slice(source.indexOf('async function loadBills()'), source.indexOf('function loadCategories()'))
  const query = { page: 1 }, loading = {}, loadError = {}, tableData = {}, total = {}, requests = []
  const load = new Function('query','dateRange','loading','loadError','tableData','total','getBillPage','t',`let billRequest=0;${code};return loadBills`)(query, { value: null }, loading, loadError, tableData, total, params => { const d = deferred(); requests.push({ ...d, params }); return d.promise }, x => x)
  const first = load(); query.page=2; const second=load()
  assert.equal(requests[0].params.page, 1)
  requests[1].resolve({ data: { records: ['page-2'], total: 20 } }); await second
  requests[0].resolve({ data: { records: ['page-1'], total: 20 } }); await first
  assert.deepEqual(tableData.value, ['page-2'])
  const third=load(); const fourth=load(); requests[2].reject(new Error('old')); await third
  assert.equal(loading.value, true); assert.equal(loadError.value, '')
  requests[3].resolve({ data: { records: ['latest'], total: 20 } }); await fourth
})
test('old HTTP and business 401 responses cannot clear a new login', async () => {
  const local = storage(), hooks = {}; let messages = 0
  const request = { interceptors: { request: { use: f => hooks.send=f }, response: { use: (ok, fail) => { hooks.ok=ok; hooks.fail=fail } } } }
  const source = read('../src/utils/request.js').replace(/^import[^\r\n]*\r?\n/gm, '').replace('export default request', '')
  new Function('axios','ElMessage','router','i18n','API_BASE_URL','captureSession','isCurrentSession','staleSessionError','localStorage','window','parseFinancialJson', source)({ create: () => request }, { warning() { messages++ }, error() { messages++ } }, { currentRoute: { value: { path: '/bill' } }, replace() { throw new Error('must not navigate') } }, { global: { t: x => x } }, '/api', captureSession, isCurrentSession, staleSessionError, local, { dispatchEvent() { throw new Error('must not clear auth') } }, parseFinancialJson)
  const config=hooks.send({ url:'/user/info',headers:{} }); local.setItem('token','B')
  await assert.rejects(hooks.ok({ config,data:{code:401} }), { code:'STALE_SESSION' })
  await assert.rejects(hooks.fail({ config,response:{status:401} }), { code:'STALE_SESSION' })
  assert.equal(local.getItem('token'),'B'); assert.equal(messages,0)
})

test('cross-tab account change invalidates cached identity and rebuilds the page before further edits', async () => {
  const { api, local, requests, writes, listeners, reloads }=profileHarness()
  const first=api.loadProfile();requests[0].resolve({data:{username:'A'}});await first
  local.setItem('token','B')
  await assert.rejects(api.loadProfile(),{code:'STALE_SESSION'})
  assert.equal(requests.length,1);assert.equal(api.profile.username,'')
  listeners.storage({storageArea:local,key:'token',oldValue:'A',newValue:'B'})
  assert.equal(reloads.count,1)
  await assert.rejects(api.saveNickname('old-form'),{code:'STALE_SESSION'})
  assert.equal(writes.length,0)
  listeners.storage({storageArea:local,key:'language',oldValue:'en',newValue:'ja'})
  assert.equal(reloads.count,1)
  api.clearProfile() // 登录成功或重建页面后显式绑定当前会话。
  const second=api.loadProfile();requests[1].resolve({data:{username:'B'}});await second
  assert.equal(api.profile.username,'B')
})

test('real Axios interceptor rejects cross-tab mutations before the adapter is called', async () => {
  const local=storage();let calls=0
  const source=read('../src/utils/request.js').replace(/^import[^\r\n]*\r?\n/gm,'').replace('export default request','return request')
  const configured={create:config=>axios.create({...config,adapter:async config=>{calls++;return {data:'{"code":200,"data":{}}',status:200,headers:{},config}}})}
  const client=new Function('axios','ElMessage','router','i18n','API_BASE_URL','captureSession','isCurrentSession','staleSessionError','localStorage','window','parseFinancialJson',source)(configured,{warning(){},error(){}},{currentRoute:{value:{path:'/bill'}},replace(){}},{global:{t:x=>x}},'/api',captureSession,isCurrentSession,staleSessionError,local,{dispatchEvent(){}},parseFinancialJson)
  local.setItem('token','B')
  await assert.rejects(client.post('/bill',{amount:1}),{code:'STALE_SESSION'})
  assert.equal(calls,0)
  invalidateSession()
  await client.get('/user/info');assert.equal(calls,1)
})

test('actual logout binds an anonymous session so login-page preferences still work', async () => {
  const { api, local, requests, writes }=profileHarness()
  const pending=api.loadProfile();requests[0].resolve({data:{username:'A'}});await pending
  const source=read('../src/layout/MainLayout.vue')
  const code=source.slice(source.indexOf('function handleLogout()'),source.indexOf('</script>'))
  let destination
  const logout=new Function('clearProfile','localStorage','router',`${code};return handleLogout`)(api.clearProfile,local,{push:path=>{destination=path}})
  logout()
  assert.equal(destination,'/login');assert.equal(local.getItem('token'),null)
  await api.setTheme('dark');await api.setLanguage('en')
  assert.equal(api.profile.theme,'dark');assert.equal(api.profile.language,'en');assert.equal(writes.length,0)
})
