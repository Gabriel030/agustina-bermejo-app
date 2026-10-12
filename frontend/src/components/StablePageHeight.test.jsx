import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { StablePageHeight } from '@/components/StablePageHeight';

let observers = [];

class FakeResizeObserver {
  constructor(callback) {
    this.callback = callback;
    observers.push(this);
  }

  observe() {}

  disconnect() {}

  report(height) {
    act(() => this.callback([{ borderBoxSize: [{ blockSize: height }] }]));
  }
}

function reportHeight(height) {
  observers.at(-1).report(height);
}

describe('StablePageHeight', () => {
  beforeEach(() => {
    observers = [];
    vi.stubGlobal('ResizeObserver', FakeResizeObserver);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('keeps the height of the tallest page so a shorter page does not pull the paginator up', () => {
    render(<StablePageHeight resetKey="todos">filas</StablePageHeight>);
    const area = screen.getByText('filas');

    reportHeight(480);
    reportHeight(260);

    expect(area).toHaveStyle({ minHeight: '480px' });
  });

  it('forgets the reserved height when the filters change', () => {
    const { rerender } = render(<StablePageHeight resetKey="todos">filas</StablePageHeight>);
    reportHeight(480);

    rerender(<StablePageHeight resetKey="solo-3er-año">filas</StablePageHeight>);

    expect(screen.getByText('filas').style.minHeight).toBe('');
  });
});
