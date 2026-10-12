import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/** One Markdown table from a repository document, with the heading it sits under. */
export interface RepoTable {
	heading: string;
	columns: string[];
	rows: string[][];
}

export type ModuleStatus = 'done' | 'in progress' | 'planned';

/**
 * Reads a document from the repository root. The build runs in `docs/`, so the root is its parent.
 * A missing document fails the build: a roadmap that silently renders nothing is worse than none.
 */
export function readRepoDoc(name: string): string {
	const path = resolve(process.cwd(), '..', name);
	try {
		return readFileSync(path, 'utf8');
	} catch (error) {
		throw new Error(`The docs build reads ${name} from the repository root (${path}) and could not: ${error}`);
	}
}

/** Every table in [markdown], in order. Separator rows are dropped; cells are trimmed. */
export function tablesOf(markdown: string): RepoTable[] {
	const tables: RepoTable[] = [];
	let heading = '';
	let current: RepoTable | null = null;
	for (const line of markdown.split(/\r?\n/)) {
		const title = /^#{2,4}\s+(.*)$/.exec(line);
		if (title) {
			heading = title[1].trim();
			current = null;
			continue;
		}
		if (!line.trimStart().startsWith('|')) {
			current = null;
			continue;
		}
		const cells = splitRow(line);
		if (current === null) {
			current = { heading, columns: cells, rows: [] };
			tables.push(current);
		} else if (!cells.every((cell) => /^:?-{3,}:?$/.test(cell))) {
			current.rows.push(cells);
		}
	}
	return tables;
}

function splitRow(line: string): string[] {
	const inner = line.trim().replace(/^\|/, '').replace(/\|$/, '');
	return inner.split(/(?<!\\)\|/).map((cell) => cell.trim().replace(/\\\|/g, '|'));
}

/** The status a MODULES.md cell starts with. Anything else fails the build rather than guessing. */
export function moduleStatus(cell: string): ModuleStatus {
	const text = cell.trim().toLowerCase();
	if (text.startsWith('done')) return 'done';
	if (text.startsWith('in progress')) return 'in progress';
	if (text.startsWith('planned')) return 'planned';
	throw new Error(`MODULES.md has a status the roadmap does not understand: "${cell}".`);
}

/** Inline Markdown as HTML: escapes everything, then renders code spans and bold. */
export function inlineHtml(text: string): string {
	const escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
	return escaped.replace(/`([^`]+)`/g, '<code>$1</code>').replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
}
