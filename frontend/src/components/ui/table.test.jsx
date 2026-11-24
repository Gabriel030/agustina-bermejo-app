import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Table, TableBody, TableCell, TableHeader, TableHead, TableRow } from './table'

function stubGeometry({ scrollWidth, clientWidth }) {
  vi.spyOn(HTMLElement.prototype, 'scrollWidth', 'get').mockReturnValue(scrollWidth)
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(clientWidth)
}

function renderTable(props) {
  return render(
    <Table {...props}>
      <TableHeader>
        <TableRow><TableHead>Estado</TableHead></TableRow>
      </TableHeader>
      <TableBody>
        <TableRow><TableCell>Pagado</TableCell></TableRow>
      </TableBody>
    </Table>
  )
}

function container() {
  return document.querySelector('[data-slot="table-container"]')
}

afterEach(() => vi.restoreAllMocks())

describe('Table scroll container', () => {
  it('shows no fade and no tab stop without overflow', () => {
    stubGeometry({ scrollWidth: 300, clientWidth: 300 })
    renderTable()
    expect(container()).toHaveAttribute('data-fade', 'none')
    expect(container()).not.toHaveAttribute('tabindex')
    expect(container()).not.toHaveAttribute('role')
  })

  it('fades only the right edge and becomes focusable when content is hidden to the right', () => {
    stubGeometry({ scrollWidth: 600, clientWidth: 300 })
    renderTable()
    expect(container()).toHaveAttribute('data-fade', 'end')
    expect(container()).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('region', { name: 'Tabla con desplazamiento horizontal' })).toBe(container())
  })

  it('fades both edges in the middle of the scroll', () => {
    stubGeometry({ scrollWidth: 600, clientWidth: 300 })
    renderTable()
    container().scrollLeft = 100
    act(() => { container().dispatchEvent(new Event('scroll')) })
    expect(container()).toHaveAttribute('data-fade', 'both')
  })

  it('fades only the left edge at the end of the scroll', () => {
    stubGeometry({ scrollWidth: 600, clientWidth: 300 })
    renderTable()
    container().scrollLeft = 300
    act(() => { container().dispatchEvent(new Event('scroll')) })
    expect(container()).toHaveAttribute('data-fade', 'start')
    expect(container()).toHaveAttribute('tabindex', '0')
  })

  it('uses a custom accessible label', () => {
    stubGeometry({ scrollWidth: 600, clientWidth: 300 })
    renderTable({ containerLabel: 'Mis pagos' })
    expect(screen.getByRole('region', { name: 'Mis pagos' })).toBeInTheDocument()
  })

  it('skips the fade when the container is overflow-visible', () => {
    stubGeometry({ scrollWidth: 600, clientWidth: 300 })
    renderTable({ containerClassName: 'overflow-visible' })
    expect(container()).toHaveAttribute('data-fade', 'none')
    expect(container()).not.toHaveAttribute('tabindex')
  })
})

describe('Table', () => {
  it('header uses the table head token and size variable', () => {
    render(
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Alumno</TableHead>
          </TableRow>
        </TableHeader>
      </Table>,
    );
    const header = screen.getByRole('columnheader');
    expect(header).toHaveClass('text-(length:--table-head-size)');
    expect(header.closest('thead')).toHaveClass('bg-(--table-head-bg)');
  });
});
