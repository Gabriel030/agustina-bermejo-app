import { readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const budgets = { entry: 450 * 1024, anyChunk: 600 * 1024 };
const dir = new URL('../dist/assets/', import.meta.url).pathname;
const files = readdirSync(dir).filter((name) => name.endsWith('.js'));
const sizes = files.map((name) => ({ name, size: statSync(join(dir, name)).size }));
const failures = sizes.filter((file) => file.size > budgets.anyChunk);
const entry = sizes.find((file) => file.name.startsWith('index-'));
if (entry && entry.size > budgets.entry && !failures.includes(entry)) failures.push(entry);
failures.forEach((file) => console.error(`${file.name}: ${file.size} bytes over budget`));
process.exit(failures.length ? 1 : 0);
