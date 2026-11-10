import { describe, expect, it } from 'vitest'
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { collectUsage, formatTable } from './component-usage.mjs'

const FIXTURES = join(process.cwd(), 'scripts', '__fixtures__', 'component-usage', 'src')

describe('component usage report', () => {
  const report = collectUsage(FIXTURES)
  const byName = Object.fromEntries(report.components.map((component) => [component.name, component]))

  it('counts importing files and JSX uses per shared component, including aliases', () => {
    expect(byName.Button).toMatchObject({ files: 2, uses: 3, group: 'ui' })
  })

  it('counts named exports of the same module independently', () => {
    expect(byName.Table).toMatchObject({ files: 1, uses: 1 })
    expect(byName.TableRow).toMatchObject({ files: 1, uses: 1 })
  })

  it('counts default exports under components', () => {
    expect(byName.StatusPill).toMatchObject({ files: 1, uses: 1, group: 'components' })
  })

  it('counts raw elements outside components/ui only', () => {
    expect(report.raw.button).toMatchObject({ occurrences: 2, files: 1 })
    expect(report.raw.table).toMatchObject({ occurrences: 1, files: 1 })
    expect(report.raw.input).toMatchObject({ occurrences: 2, files: 2 })
    expect(report.raw.select.occurrences).toBe(1)
    expect(report.raw.textarea.occurrences).toBe(1)
  })

  it('lists shared components that nobody imports', () => {
    expect(report.unused).toContain('UnusedPiece')
    expect(report.unused).not.toContain('Button')
  })

  it('orders components by importing files, then uses', () => {
    expect(report.components[0].name).toBe('Button')
  })

  it('prints a table with the raw element summary', () => {
    const output = formatTable(report)
    expect(output).toContain('Componente')
    expect(output).toContain('<button>')
    expect(output).toContain('Button')
  })

  it('ignores test files when counting', () => {
    const root = mkdtempSync(join(tmpdir(), 'component-usage-'))
    mkdirSync(join(root, 'components', 'ui'), { recursive: true })
    mkdirSync(join(root, 'features'), { recursive: true })
    writeFileSync(join(root, 'components', 'ui', 'button.jsx'), 'export function Button() { return <button /> }')
    writeFileSync(
      join(root, 'features', 'Page.test.jsx'),
      "import { Button } from '@/components/ui/button'\nexport const t = <Button><button /></Button>",
    )
    const result = collectUsage(root)
    expect(result.components).toEqual([])
    expect(result.raw.button.occurrences).toBe(0)
  })
})
