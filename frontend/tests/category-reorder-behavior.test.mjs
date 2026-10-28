import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('reorder submits one command and never changes local order before success', async () => {
  const source=readFileSync(new URL('../src/views/Category.vue',import.meta.url),'utf8')
  const code=source.slice(source.indexOf('async function handleMove(row, direction)'),source.indexOf('async function handleDelete(row)'))
  const rows=[{id:1,sortOrder:0},{id:2,sortOrder:0}], calls=[], moving={value:false};let reloads=0
  const move=new Function('editableCategories','moving','reorderCategories','loadCategories','ElMessage','t',`${code};return handleMove`)({value:rows},moving,async body=>{calls.push(body);throw new Error('request failed')},async()=>{reloads++},{success(){},error(){}},x=>x)
  await move(rows[0],1)
  assert.deepEqual(calls,[{id:1,targetId:2}]);assert.deepEqual(rows.map(row=>row.sortOrder),[0,0]);assert.equal(reloads,1);assert.equal(moving.value,false)
})
