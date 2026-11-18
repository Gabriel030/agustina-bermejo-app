import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { ESLint } from 'eslint'
import { RAW_BUTTON_EXCEPTIONS, noRawButtonRule } from './raw-button.js'

const ROOT = process.cwd()

async function lint(code, filePath) {
  const eslint = new ESLint({
    cwd: ROOT,
    overrideConfigFile: join(ROOT, 'eslint.config.js'),
  })
  const [result] = await eslint.lintText(code, { filePath: join(ROOT, filePath) })
  return result.messages.filter((message) => message.ruleId === 'guard/no-raw-button')
}

describe('raw button exceptions', () => {
  it('only lists files that still contain a raw button, so the list can only shrink', () => {
    const stale = RAW_BUTTON_EXCEPTIONS.filter(
      (file) => !/<button(?![\w-])/.test(readFileSync(join(ROOT, file), 'utf8')),
    )
    expect(stale).toEqual([])
  })

  it('exposes the rule', () => {
    expect(noRawButtonRule.create).toBeTypeOf('function')
  })
})

describe('guard/no-raw-button', () => {
  it('flags a raw button in a new feature file', async () => {
    const messages = await lint('export const A = () => <button type="button">Ok</button>', 'src/features/demo/A.jsx')
    expect(messages).toHaveLength(1)
  })

  it('flags a raw button in a new shared component', async () => {
    const messages = await lint('export const A = () => <button />', 'src/components/NewThing.jsx')
    expect(messages).toHaveLength(1)
  })

  it('does not flag the shared Button', async () => {
    const messages = await lint('export const A = () => <Button>Ok</Button>', 'src/features/demo/A.jsx')
    expect(messages).toEqual([])
  })

  it('does not apply inside components/ui', async () => {
    const messages = await lint('export const A = () => <button />', 'src/components/ui/a.jsx')
    expect(messages).toEqual([])
  })

  it('does not apply to tests', async () => {
    const messages = await lint('export const A = () => <button />', 'src/features/demo/A.test.jsx')
    expect(messages).toEqual([])
  })

  it.each(RAW_BUTTON_EXCEPTIONS.slice(0, 3))('does not apply to the listed exception %s', async (file) => {
    const messages = await lint('export const A = () => <button />', file)
    expect(messages).toEqual([])
  })
})
