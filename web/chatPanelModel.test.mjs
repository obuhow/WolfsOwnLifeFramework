import assert from 'node:assert/strict'
import test from 'node:test'
import {
  mergeChatResponse,
  newestSession,
  actionPresentation,
  actionResultLabel,
} from './src/chatPanelModel.js'

test('newestSession chooses the most recently updated session', () => {
  const session = newestSession([
    { id: 4, updatedAt: '2026-09-20T10:00:00Z' },
    { id: 2, updatedAt: '2026-09-21T10:00:00Z' },
    { id: 3, updatedAt: '2026-09-21T10:00:00Z' },
  ])

  assert.equal(session.id, 3)
})

test('mergeChatResponse keeps server message order and does not duplicate messages', () => {
  const existing = [{ id: 10, role: 'USER', content: 'Первый вопрос' }]
  const response = {
    userMessage: { id: 11, role: 'USER', content: 'Второй вопрос' },
    assistantMessage: { id: 12, role: 'ASSISTANT', content: 'Второй ответ' },
  }

  assert.deepEqual(mergeChatResponse(existing, response), [
    existing[0],
    response.userMessage,
    response.assistantMessage,
  ])
  assert.deepEqual(mergeChatResponse(
    [existing[0], response.userMessage],
    response,
  ), [existing[0], response.userMessage, response.assistantMessage])
})

test('actionPresentation distinguishes pending, applied and rejected proposals', () => {
  assert.deepEqual(actionPresentation({ id: 7, status: 'PENDING' }), {
    label: 'Подтвердить действие',
    canConfirm: true,
    applied: false,
  })
  assert.deepEqual(actionPresentation({ id: 7, status: 'APPLIED' }), {
    label: 'Действие применено',
    canConfirm: false,
    applied: true,
  })
  assert.deepEqual(actionPresentation({ id: 7, status: 'REJECTED' }), {
    label: 'Действие отклонено',
    canConfirm: false,
    applied: false,
  })
  assert.deepEqual(actionPresentation({ id: 7, status: 'EXPIRED' }), {
    label: 'Срок действия истёк',
    canConfirm: false,
    applied: false,
  })
})

test('actionResultLabel shows created records instead of opaque JSON', () => {
  assert.equal(actionResultLabel({ id: 12, title: 'Новое дело' }), 'Новое дело (#12)')
  assert.equal(actionResultLabel({ deleted: 12 }), 'Удалено: #12')
})
