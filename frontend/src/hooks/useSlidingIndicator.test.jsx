import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useSlidingIndicator } from './useSlidingIndicator';

function Harness() {
  const { containerRef, rect } = useSlidingIndicator('[data-active="true"]');
  return (
    <div ref={containerRef}>
      <span data-active="true" data-testid="active" />
      <output data-testid="width">{rect?.width}</output>
    </div>
  );
}

describe('useSlidingIndicator', () => {
  const callbacks = [];
  const originalObserver = globalThis.ResizeObserver;

  beforeEach(() => {
    globalThis.ResizeObserver = class {
      constructor(callback) {
        callbacks.push(callback);
      }
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  });

  afterEach(() => {
    globalThis.ResizeObserver = originalObserver;
    callbacks.length = 0;
    vi.restoreAllMocks();
  });

  it('measures again when the active element changes size without any attribute change', () => {
    const width = vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(80);
    render(<Harness />);
    expect(screen.getByTestId('width')).toHaveTextContent('80');

    width.mockReturnValue(110);
    act(() => callbacks.forEach((callback) => callback()));

    expect(screen.getByTestId('width')).toHaveTextContent('110');
  });
});
