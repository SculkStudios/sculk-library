import type { CollectionEntry } from 'astro:content';

export type Platform = 'paper' | 'folia' | 'canvas' | 'velocity';
export type SupportLevel = 'full' | 'partial' | 'none' | 'n/a';

export const PLATFORMS: readonly Platform[] = ['paper', 'folia', 'canvas', 'velocity'];

export const PLATFORM_LABELS: Record<Platform, string> = {
	paper: 'Paper',
	folia: 'Folia',
	canvas: 'Canvas',
	velocity: 'Velocity',
};

/** A sidebar entry: a page id, and the shorter name the sidebar shows when the title is long. */
interface Entry {
	id: string;
	label?: string;
}

/** The sidebar, in reading order. Every published page must appear here exactly once. */
export const CATEGORIES: readonly { id: string; label: string; icon: string; pages: readonly Entry[] }[] = [
	{ id: 'introduction', label: 'Introduction', icon: 'book', pages: [{ id: 'start/why-sculk' }, { id: 'start/examples' }] },
	{
		id: 'setup',
		label: 'Setup',
		icon: 'server',
		pages: [
			{ id: 'start/installation', label: 'Paper and Folia' },
			{ id: 'start/first-plugin' },
			{ id: 'guides/canvas' },
			{ id: 'start/velocity' },
		],
	},
	{
		id: 'concepts',
		label: 'Core concepts',
		icon: 'layers',
		pages: [
			{ id: 'concepts/execution', label: 'Threading' },
			{ id: 'concepts/tasks' },
			{ id: 'concepts/lifecycle', label: 'Plugin lifecycle' },
			{ id: 'concepts/capabilities' },
			{ id: 'concepts/results' },
			{ id: 'guides/testing' },
		],
	},
	{
		id: 'commands',
		label: 'Commands and events',
		icon: 'terminal',
		pages: [{ id: 'guides/commands' }, { id: 'guides/events' }, { id: 'guides/permissions' }],
	},
	{
		id: 'text',
		label: 'Text and configs',
		icon: 'message',
		pages: [{ id: 'guides/text' }, { id: 'guides/placeholders' }, { id: 'guides/config' }, { id: 'guides/config-admin' }],
	},
	{
		id: 'interface',
		label: 'Player UI',
		icon: 'window',
		pages: [{ id: 'guides/menus' }, { id: 'guides/dialogs' }, { id: 'guides/hud' }],
	},
	{
		id: 'content',
		label: 'Items and content',
		icon: 'cube',
		pages: [{ id: 'guides/items' }, { id: 'guides/recipes' }, { id: 'guides/registry' }],
	},
	{
		id: 'reference',
		label: 'Reference',
		icon: 'file',
		pages: [
			{ id: 'reference/compatibility' },
			{ id: 'reference/api-stability' },
			{ id: 'reference/roadmap' },
			{ id: 'reference/contributing' },
		],
	},
];

export interface NavItem {
	id: string;
	href: string;
	label: string;
	support?: Partial<Record<Platform, SupportLevel>>;
}

export interface NavSection {
	id: string;
	label: string;
	icon: string;
	items: NavItem[];
}

export function href(id: string): string {
	return `/${id}/`;
}

/**
 * The sidebar from [CATEGORIES]. A published page missing from it, or an entry naming no page, fails
 * the build, so the navigation cannot drift from the content.
 */
export function navigation(entries: CollectionEntry<'docs'>[]): NavSection[] {
	const published = new Map(entries.filter((entry) => !entry.data.draft).map((entry) => [entry.id, entry]));
	const listed = CATEGORIES.flatMap((category) => category.pages.map((page) => page.id));
	const unlisted = [...published.keys()].filter((id) => !listed.includes(id));
	if (unlisted.length > 0) throw new Error(`Pages missing from the sidebar in src/lib/nav.ts: ${unlisted.join(', ')}`);
	return CATEGORIES.map((category) => ({
		id: category.id,
		label: category.label,
		icon: category.icon,
		items: category.pages.map((page) => {
			const entry = published.get(page.id);
			if (!entry) throw new Error(`The sidebar in src/lib/nav.ts names a page that does not exist: ${page.id}`);
			return {
				id: entry.id,
				href: href(entry.id),
				label: page.label ?? entry.data.title,
				support: entry.data.support
					? Object.fromEntries(PLATFORMS.map((platform) => [platform, entry.data.support?.[platform].level]))
					: undefined,
			};
		}),
	}));
}

/** The pages before and after [id] in sidebar order, for the way forward at the foot of a page. */
export function neighbours(sections: NavSection[], id: string): { previous?: NavItem; next?: NavItem } {
	const flat = sections.flatMap((section) => section.items);
	const index = flat.findIndex((item) => item.id === id);
	if (index < 0) return {};
	return { previous: flat[index - 1], next: flat[index + 1] };
}

export function sectionOf(id: string): string | undefined {
	return CATEGORIES.find((category) => category.pages.some((page) => page.id === id))?.label;
}
