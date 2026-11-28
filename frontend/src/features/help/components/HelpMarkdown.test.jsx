import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { parseMarkdown } from '@/features/help/lib/markdownBlocks';
import { HelpMarkdown } from '@/features/help/components/HelpMarkdown';

const renderSource = (source) => render(<HelpMarkdown blocks={parseMarkdown(source)} />);

describe('HelpMarkdown', () => {
  it('rendersHeadingsListsEmphasisLinksAndTables', () => {
    renderSource(
      [
        '## Pasos',
        '',
        '1. Abrí **Alumnos**.',
        '2. Tocá [Ayuda](/ayuda).',
        '',
        '- Una regla',
        '',
        '| Campo | Valor |',
        '| --- | --- |',
        '| Rol | Director |',
      ].join('\n'),
    );

    expect(screen.getByRole('heading', { name: 'Pasos' })).toHaveAttribute('id', 'pasos');
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
    expect(screen.getByText('Alumnos').tagName).toBe('STRONG');
    expect(screen.getByRole('link', { name: 'Ayuda' })).toHaveAttribute('href', '/ayuda');
    expect(screen.getByRole('columnheader', { name: 'Campo' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Director' })).toBeInTheDocument();
  });

  it('showsRawHtmlAsPlainTextWithoutCreatingElements', () => {
    const { container } = renderSource('<img src=x onerror="alert(1)"> y <script>alert(1)</script>');

    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('script')).toBeNull();
    expect(container.textContent).toContain('<script>alert(1)</script>');
  });

  it('dropsUnsafeLinkTargetsKeepingTheirText', () => {
    renderSource('[Tocame](javascript:void0)');

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(screen.getByText('Tocame')).toBeInTheDocument();
  });

  it('opensExternalLinksSafelyInANewTab', () => {
    renderSource('[Sitio](https://example.com)');

    expect(screen.getByRole('link', { name: 'Sitio' })).toHaveAttribute('rel', 'noopener noreferrer');
  });
});
