import { parseHelpTask } from '@/features/help/lib/helpTasks';

const CONTENT_FILES = import.meta.glob('../content/*/*.md', { query: '?raw', import: 'default' });

export const INCLUDE_DRAFTS = !import.meta.env.PROD;

export async function loadHelpTasks() {
  const entries = await Promise.all(
    Object.entries(CONTENT_FILES).map(async ([path, load]) => parseHelpTask(path, await load())),
  );
  return entries.sort((a, b) => a.title.localeCompare(b.title, 'es'));
}
