import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Table, TableHead, TableHeader, TableRow } from './table';

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
