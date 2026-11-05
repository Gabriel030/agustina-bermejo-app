import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { cwd } from 'node:process'
import { describe, expect, it } from 'vitest'

const html = readFileSync(resolve(cwd(), 'index.html'), 'utf8')

describe('index.html', () => {
  it('indexHtmlLinksManifestIconsAndSpanishLang', () => {
    expect(html).toContain('lang="es"')
    expect(html).toContain('name="theme-color"')
    expect(html).toContain('rel="apple-touch-icon"')
  })
})
