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
