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

/** The documentation's sections, in reading order; a page belongs to the one its id starts with. */
export const SECTIONS = [
	{ id: 'start', label: 'Start' },
	{ id: 'concepts', label: 'Concepts' },
	{ id: 'guides', label: 'Guides' },
	{ id: 'reference', label: 'Reference' },
] as const;

export interface NavItem {
	id: string;
	href: string;
	label: string;
	support?: Partial<Record<Platform, SupportLevel>>;
}

export interface NavSection {
	id: string;
	label: string;
	items: NavItem[];
}

export function href(id: string): string {
	return `/${id}/`;
}

/**
 * The sidebar: each section's visible pages ordered by `sidebar.order`, then title. Collection order
 * is not stable, so the sort is the only order there is.
 */
export function navigation(entries: CollectionEntry<'docs'>[]): NavSection[] {
	return SECTIONS.map((section) => ({
		id: section.id,
		label: section.label,
		items: entries
			.filter((entry) => entry.id.startsWith(`${section.id}/`) && !entry.data.draft && !entry.data.sidebar.hidden)
			.sort(
				(a, b) =>
					(a.data.sidebar.order ?? Number.MAX_SAFE_INTEGER) - (b.data.sidebar.order ?? Number.MAX_SAFE_INTEGER) ||
					a.data.title.localeCompare(b.data.title),
			)
			.map((entry) => ({
				id: entry.id,
				href: href(entry.id),
				label: entry.data.sidebar.label ?? entry.data.title,
				support: entry.data.support
					? Object.fromEntries(PLATFORMS.map((platform) => [platform, entry.data.support?.[platform].level]))
					: undefined,
			})),
	})).filter((section) => section.items.length > 0);
}

/** The pages before and after [id] in sidebar order, for the way forward at the foot of a page. */
export function neighbours(sections: NavSection[], id: string): { previous?: NavItem; next?: NavItem } {
	const flat = sections.flatMap((section) => section.items);
	const index = flat.findIndex((item) => item.id === id);
	if (index < 0) return {};
	return { previous: flat[index - 1], next: flat[index + 1] };
}

export function sectionOf(id: string): string | undefined {
	return SECTIONS.find((section) => id.startsWith(`${section.id}/`))?.label;
}
