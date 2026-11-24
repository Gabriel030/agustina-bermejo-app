import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ClosureExportMenu } from './ClosureExportMenu';

describe('ClosureExportMenu', () => {
  it('offersTheSpreadsheetAlwaysAndRequestsItWithTheXlsxFormat', async () => {
    const user = userEvent.setup();
    const onExport = vi.fn();
    render(<ClosureExportMenu closed={false} pending={false} onExport={onExport} />);

    await user.click(screen.getByRole('button', { name: 'Exportar' }));
    await user.click(screen.getByRole('menuitem', { name: 'Planilla de cálculo (.xlsx)' }));

    expect(onExport).toHaveBeenCalledWith('xlsx');
  });

  it('disablesThePdfUntilTheClosureIsClosed', async () => {
    const user = userEvent.setup();
    const onExport = vi.fn();
    render(<ClosureExportMenu closed={false} pending={false} onExport={onExport} />);

    await user.click(screen.getByRole('button', { name: 'Exportar' }));
    await user.click(screen.getByRole('menuitem', { name: /PDF del acta/ }));

    expect(onExport).not.toHaveBeenCalled();
  });

  it('requestsThePdfWhenTheClosureIsClosed', async () => {
    const user = userEvent.setup();
    const onExport = vi.fn();
    render(<ClosureExportMenu closed pending={false} onExport={onExport} />);

    await user.click(screen.getByRole('button', { name: 'Exportar' }));
    await user.click(screen.getByRole('menuitem', { name: 'PDF del acta' }));

    expect(onExport).toHaveBeenCalledWith('pdf');
  });
});

describe('ClosureExportMenuViewportMargin', () => {
  it('keepsTheMenuAwayFromTheViewportEdge', async () => {
    const original = Element.prototype.getBoundingClientRect;
    Element.prototype.getBoundingClientRect = function boundingRect() {
      const isTrigger = this.getAttribute?.('aria-haspopup') === 'menu';
      const width = isTrigger ? 80 : 256;
      return { x: 0, y: 0, left: 0, top: 0, width, height: 40, right: width, bottom: 40, toJSON() {} };
    };
    const originalOffsetWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetWidth');
    Object.defineProperty(HTMLElement.prototype, 'offsetWidth', {
      configurable: true,
      get() {
        return this.getAttribute('aria-haspopup') === 'menu' ? 80 : 256;
      },
    });
    Object.defineProperty(document.documentElement, 'clientWidth', { configurable: true, value: 390 });
    Object.defineProperty(document.documentElement, 'clientHeight', { configurable: true, value: 844 });
    try {
      const user = userEvent.setup();
      render(<ClosureExportMenu closed pending={false} onExport={vi.fn()} />);
      await user.click(screen.getByRole('button', { name: 'Exportar' }));
      const menu = await screen.findByRole('menu');
      const wrapper = menu.parentElement;
      await vi.waitFor(() => expect(wrapper.style.transform).toMatch(/^translate\(8px,/));
    } finally {
      Element.prototype.getBoundingClientRect = original;
      Object.defineProperty(HTMLElement.prototype, 'offsetWidth', originalOffsetWidth);
      delete document.documentElement.clientWidth;
      delete document.documentElement.clientHeight;
    }
  });
});
