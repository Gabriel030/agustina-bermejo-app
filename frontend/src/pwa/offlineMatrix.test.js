import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { cwd } from 'node:process'
import { describe, expect, it } from 'vitest'

const repoRoot = resolve(cwd(), '..')
const testingDoc = readFileSync(resolve(repoRoot, 'docs/context/testing.md'), 'utf8')
const section = testingDoc.split('### Matriz offline de la PWA')[1]?.split('\n## ')[0] ?? ''
const rows = section.split('\n').filter((line) => line.startsWith('| ') && !line.startsWith('| Estado') && !line.startsWith('|---'))
const references = (row) => [...row.matchAll(/`([^`]+\.(?:test|spec)\.jsx?)` › `([^`]+)`/g)].map(([, file, name]) => ({ file, name }))

const states = ['Online', 'Offline', 'Red lenta', 'Reanudación', 'Versión nueva', 'Sesión vencida estando offline']

describe('offline PWA matrix', () => {
  it('declaresEveryStateOfTheMatrix', () => {
    expect(rows.map((row) => row.split('|')[1].trim())).toEqual(states)
  })

  it('hasAtLeastOneReferencedTestPerState', () => {
    rows.forEach((row) => expect(references(row).length).toBeGreaterThan(0))
  })

  it('coversEveryStateWithAnEndToEndTest', () => {
    rows.forEach((row) => expect(references(row).some(({ file }) => file.startsWith('e2e/'))).toBe(true))
  })

  it('pointsOnlyToTestsThatExist', () => {
    rows.flatMap(references).forEach(({ file, name }) => {
      const path = resolve(repoRoot, file)
      expect(existsSync(path), file).toBe(true)
      expect(readFileSync(path, 'utf8'), `${file} › ${name}`).toContain(`'${name}'`)
    })
  })
})
