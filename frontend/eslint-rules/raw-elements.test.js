import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { ESLint } from 'eslint'
import {
  EXEMPT_INPUT_TYPES,
  RAW_ELEMENT_COMPONENTS,
  RAW_ELEMENT_EXCEPTIONS,
  localPlugin,
} from './raw-elements.js'

const ROOT = process.cwd()

function hasRawElement(source) {
  return Object.keys(RAW_ELEMENT_COMPONENTS).some((tag) => {
    const opening = new RegExp(`<${tag}(?![\\w-])([^>]*)`, 'g')
    return [...source.matchAll(opening)].some((match) => {
      if (tag !== 'input') return true
      const type = match[1].match(/type="([^"]+)"/)?.[1]
      return !EXEMPT_INPUT_TYPES.includes(type)
    })
  })
}

async function lint(code, filePath) {
  const eslint = new ESLint({
    cwd: ROOT,
    overrideConfigFile: join(ROOT, 'eslint.config.js'),
  })
  const [result] = await eslint.lintText(code, { filePath: join(ROOT, filePath) })
  return result.messages.filter((message) => message.ruleId === 'local/no-raw-elements')
}

describe('raw form element exceptions', () => {
  it('only lists files that still contain a raw element, so the list can only shrink', () => {
    const stale = RAW_ELEMENT_EXCEPTIONS.filter(
      (file) => !hasRawElement(readFileSync(join(ROOT, file), 'utf8')),
    )
    expect(stale).toEqual([])
  })

  it('exposes the rule through the local plugin', () => {
    expect(Object.keys(localPlugin.rules)).toEqual(['no-raw-elements'])
  })
})

describe('local/no-raw-elements', () => {
  it.each(['table', 'select', 'textarea', 'input'])('flags a raw <%s> in a feature file', async (tag) => {
    const messages = await lint(`export const A = () => <${tag} />`, 'src/features/demo/A.jsx')
    expect(messages).toHaveLength(1)
  })

  it.each(EXEMPT_INPUT_TYPES)('allows <input type="%s">', async (type) => {
    const messages = await lint(`export const A = () => <input type="${type}" />`, 'src/features/demo/A.jsx')
    expect(messages).toEqual([])
  })

  it('flags a text input even when other attributes are present', async () => {
    const messages = await lint('export const A = () => <input type="text" value="x" />', 'src/features/demo/A.jsx')
    expect(messages).toHaveLength(1)
  })

  it('does not flag shared components', async () => {
    const messages = await lint(
      'export const A = () => <><Input /><Table /><Textarea /><NativeSelect /></>',
      'src/features/demo/A.jsx',
    )
    expect(messages).toEqual([])
  })

  it('does not apply inside components/ui', async () => {
    const messages = await lint('export const A = () => <input />', 'src/components/ui/a.jsx')
    expect(messages).toEqual([])
  })

  it.each(RAW_ELEMENT_EXCEPTIONS)('does not apply to the listed exception %s', async (file) => {
    const messages = await lint('export const A = () => <table />', file)
    expect(messages).toEqual([])
  })
})
