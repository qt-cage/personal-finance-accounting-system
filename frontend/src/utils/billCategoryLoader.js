export function createBillCategoryLoader({ form, fetchCategories, onOptions, onLoading, onError }) {
  let requestVersion = 0

  async function load({ resetCategory = false } = {}) {
    const version = ++requestVersion
    const type = form.type
    if (resetCategory) form.categoryId = null
    onOptions([])
    onError(null)
    onLoading(true)

    try {
      const response = await fetchCategories({ type })
      // 快速切换收支类型或重开另一笔账单时，只采用最后一次加载结果。
      if (version !== requestVersion || type !== form.type) return false
      onOptions(response.data || [])
      return true
    } catch (error) {
      if (version === requestVersion && type === form.type) onError(error)
      return false
    } finally {
      if (version === requestVersion) onLoading(false)
    }
  }

  function invalidate() {
    requestVersion += 1
    onLoading(false)
  }

  return { load, invalidate }
}
