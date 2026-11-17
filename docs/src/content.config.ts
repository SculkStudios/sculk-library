import { defineCollection } from 'astro:content';
import { glob } from 'astro/loaders';
import { z } from 'astro/zod';

/** How one platform supports what a page documents: shown as a mark first, colour second. */
const support = z.object({
	level: z.enum(['full', 'partial', 'none', 'n/a']),
	note: z.string().optional(),
});

const page = z.object({
	title: z.string(),
	description: z.string(),
	// Starlight's shape, so existing pages keep their order and labels.
	sidebar: z
		.object({
			order: z.number().optional(),
			label: z.string().optional(),
			hidden: z.boolean().default(false),
		})
		.default({ hidden: false }),
	/** The published module the page documents, shown beside the title. */
	module: z.string().optional(),
	/** The Sculk version the page's API arrived in. */
	since: z.string().optional(),
	/**
	 * Where the documented API runs (THREADING.md): the player's or entity's thread, the global
	 * region, the sender's context, off every game thread, or any thread. Omitted when the page has
	 * no single answer; the margin then shows no map rather than a guess.
	 */
	runs: z.enum(['entity', 'global', 'sender', 'async', 'any', 'proxy']).optional(),
	/** Per-platform support; a page without it shows no support legend rather than an assumption. */
	support: z
		.object({
			paper: support,
			folia: support,
			canvas: support,
			velocity: support,
		})
		.optional(),
	draft: z.boolean().default(false),
});

export const collections = {
	docs: defineCollection({ loader: glob({ base: './src/content/docs', pattern: '**/*.{md,mdx}' }), schema: page }),
};
