import { existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { cwd } from 'node:process'
import { describe, expect, it } from 'vitest'
import { manifest } from '../../pwa.manifest.js'

const publicFile = (src) => resolve(cwd(), 'public', src.replace(/^\//, ''))

describe('pwa manifest', () => {
  it('manifestDeclaresInstallableFieldsAndIcons', () => {
    expect(manifest.name).toBeTruthy()
    expect(manifest.display).toBe('standalone')
    expect(manifest.start_url).toBe('/')
    const sizes = manifest.icons.map((icon) => icon.sizes)
    expect(sizes).toContain('192x192')
    expect(sizes).toContain('512x512')
    expect(manifest.icons.some((icon) => icon.purpose === 'maskable')).toBe(true)
    manifest.icons.forEach((icon) => expect(existsSync(publicFile(icon.src))).toBe(true))
  })
})
