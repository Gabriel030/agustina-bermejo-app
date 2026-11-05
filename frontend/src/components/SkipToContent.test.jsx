import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SkipToContent } from '@/components/SkipToContent';

describe('SkipToContent', () => {
  it('skipLinkIsHiddenUntilFocusedAndTargetsMainContent', async () => {
    const user = userEvent.setup();
    render(<SkipToContent />);

    const link = screen.getByRole('link', { name: 'Saltar al contenido' });
    expect(link).toHaveAttribute('href', '#main-content');
    expect(link).toHaveClass('sr-only');

    await user.tab();

    expect(link).toHaveFocus();
  });
});
