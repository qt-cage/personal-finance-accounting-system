export function categoryLabel(category, categories) {
  const parent = categories.find(item => item.id === category.parentId)
  return parent ? `${parent.name} / ${category.name}` : category.name
}

export function categoryTree(categories) {
  const nodes = new Map(categories.map(category => [category.id, { ...category, children: [] }]))
  const roots = []
  for (const category of categories) {
    const node = nodes.get(category.id)
    const parent = nodes.get(category.parentId)
    if (parent && parent !== node) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}
