export function newestSession(sessions) {
  return [...sessions]
    .sort((left, right) => {
      const updated = String(left.updatedAt || '').localeCompare(String(right.updatedAt || ''))
      return updated || Number(left.id) - Number(right.id)
    })
    .at(-1) || null
}

export function mergeChatResponse(messages, response) {
  const merged = [...messages]
  const knownIds = new Set(merged.map((message) => message.id))
  for (const message of [response?.userMessage, response?.assistantMessage]) {
    if (message && !knownIds.has(message.id)) {
      merged.push(message)
      knownIds.add(message.id)
    }
  }
  return merged
}

export function actionPresentation(action) {
  if (action?.status === 'PENDING') {
    return { label: 'Подтвердить действие', canConfirm: true, applied: false }
  }
  if (action?.status === 'APPLIED') {
    return { label: 'Действие применено', canConfirm: false, applied: true }
  }
  if (action?.status === 'EXPIRED') {
    return { label: 'Срок действия истёк', canConfirm: false, applied: false }
  }
  return { label: 'Действие отклонено', canConfirm: false, applied: false }
}

export function actionResultLabel(result) {
  if (result?.title && result?.id !== undefined) return `${result.title} (#${result.id})`
  if (result?.deleted !== undefined) return `Удалено: #${result.deleted}`
  if (result === null || result === undefined) return ''
  if (typeof result === 'object') return JSON.stringify(result)
  return String(result)
}
