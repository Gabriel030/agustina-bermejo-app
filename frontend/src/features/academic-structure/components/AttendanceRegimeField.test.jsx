import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AttendanceRegimeField } from './AttendanceRegimeField';
import {
  attendanceRegimeFromPercent,
  attendanceRegimeToPercent,
  isAttendanceRegimeValid,
} from '../attendanceRegime';

function Harness({ initial, lockUnlimited }) {
  const [value, setValue] = useState(initial);
  return <AttendanceRegimeField value={value} onChange={setValue} lockUnlimited={lockUnlimited} />;
}

describe('AttendanceRegimeField', () => {
  it('warns without blocking at 0 and 100 percent', async () => {
    const user = userEvent.setup();
    render(<Harness initial={{ mode: 'limit', percent: '0' }} />);

    expect(screen.getByText(/Con 0 %, cualquier inasistencia injustificada/)).toBeInTheDocument();
    await user.clear(screen.getByLabelText('Porcentaje máximo de ausentismo'));
    await user.type(screen.getByLabelText('Porcentaje máximo de ausentismo'), '100');
    expect(screen.getByText(/Con 100 % nunca se marca libre a nadie/)).toBeInTheDocument();
    expect(isAttendanceRegimeValid({ mode: 'limit', percent: '0' })).toBe(true);
    expect(isAttendanceRegimeValid({ mode: 'limit', percent: '100' })).toBe(true);
  });

  it('rejects empty and out of range values', () => {
    expect(isAttendanceRegimeValid({ mode: 'limit', percent: '' })).toBe(false);
    expect(isAttendanceRegimeValid({ mode: 'limit', percent: '-1' })).toBe(false);
    expect(isAttendanceRegimeValid({ mode: 'limit', percent: '100.5' })).toBe(false);
    expect(isAttendanceRegimeValid({ mode: 'none', percent: '' })).toBe(true);
  });

  it('keeps decimals and maps no limit to null', () => {
    expect(attendanceRegimeToPercent({ mode: 'limit', percent: '12.5' })).toBe(12.5);
    expect(attendanceRegimeToPercent({ mode: 'none', percent: '30' })).toBeNull();
  });

  it('starts in limit mode with the saved percentage and disables no limit when locked', () => {
    render(<Harness initial={attendanceRegimeFromPercent(25)} lockUnlimited />);

    expect(screen.getByRole('radio', { name: /Sin límite/ })).toBeDisabled();
    expect(screen.getByLabelText('Porcentaje máximo de ausentismo')).toHaveValue(25);
  });

  it('explains that justified absences do not count', async () => {
    const user = userEvent.setup();
    render(<Harness initial={{ mode: 'none', percent: '' }} />);

    await user.click(screen.getByRole('button', { name: 'Qué significa el máximo de ausentismo' }));
    expect(await screen.findByText(/Las inasistencias justificadas no cuentan/)).toBeInTheDocument();
  });
});
