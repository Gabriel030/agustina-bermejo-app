import { useEffect, useRef, useState } from 'react';

const COPIED_MS = 2500;

function copyWithSelection(text) {
  const field = document.createElement('textarea');
  field.value = text;
  field.setAttribute('readonly', '');
  field.style.position = 'fixed';
  field.style.opacity = '0';
  document.body.appendChild(field);
  field.select();
  const copied = document.execCommand('copy');
  field.remove();
  return copied;
}

export function useCopyToClipboard() {
  const [copied, setCopied] = useState(false);
  const timer = useRef(null);

  useEffect(() => () => clearTimeout(timer.current), []);

  async function copy(text) {
    let done;
    try {
      await navigator.clipboard.writeText(text);
      done = true;
    } catch {
      done = copyWithSelection(text);
    }
    if (!done) return;
    setCopied(true);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setCopied(false), COPIED_MS);
  }

  return { copied, copy };
}
