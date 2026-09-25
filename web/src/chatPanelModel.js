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

export function latestContextTransparency(messages) {
  return [...(messages || [])]
    .reverse()
    .find((message) => message?.contextTransparency)?.contextTransparency || null
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

export function contextPresentation(context) {
  if (!context?.available) {
    return { unavailable: context?.reason || 'Агент недоступен' }
  }

  const period = context.period
    ? `${context.period.from} — ${context.period.toExclusive} (${context.period.weeks} недель)`
    : 'Период не указан'
  const projects = (context.projects || []).map((project) => (
    project.lifeArea ? `${project.title} · ${project.lifeArea}` : project.title
  ))
  const goals = (context.goals || []).map((goal) => (
    goal.priority === undefined ? goal.title : `${goal.title} · приоритет ${goal.priority}`
  ))
  const routines = (context.routines || []).map((routine) => (
    routine.weeklyHours === undefined || routine.weeklyHours === null
      ? routine.title
      : `${routine.title} · ${routine.weeklyHours} ч/нед`
  ))
  const dynamics = []
  if (!context.dynamics?.historyAvailable) {
    dynamics.push(context.dynamics?.historyNote || 'История расписания отсутствует')
  } else {
    dynamics.push(`По проектам: ${formatHours(context.dynamics.projectHours)}`)
    dynamics.push(`По областям жизни: ${formatHours(context.dynamics.lifeAreaHours)}`)
    dynamics.push(`По рутинам: ${formatHours(context.dynamics.routineHours)}`)
    if (context.dynamics.trend) {
      dynamics.push(`Тренд факта: ${context.dynamics.trend.direction}; изменение: ${formatValue(context.dynamics.trend.changeHours)} ч`)
    }
  }

  return {
    period,
    projects: projects.length ? projects : ['Нет активных проектов'],
    goals: goals.length ? goals : ['Нет активных целей'],
    routines: routines.length ? routines : ['Нет активных рутин'],
    dynamics,
    history: `${context.historyMessages ?? 0} сообщений истории`,
    payload: `${context.payloadCharacters ?? 0} знаков`,
  }
}

function formatHours(values = []) {
  if (!values.length) return 'нет данных'
  return values.map((item) => `${item.label}: план ${formatValue(item.planned)}, факт ${formatValue(item.fact)}, сетка ${formatValue(item.pending)}`).join('; ')
}

function formatValue(value) {
  return value === null || value === undefined ? 'нет данных' : String(value)
}
