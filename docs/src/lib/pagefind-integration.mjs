// Indexes the built site with Pagefind once Astro has written it. Pages mark their searchable body
// with data-pagefind-body, so navigation, margins and the home page chrome never match a search.
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

/** @returns {import('astro').AstroIntegration} */
export function pagefind() {
	return {
		name: 'sculk-pagefind',
		hooks: {
			'astro:build:done': async ({ dir, logger }) => {
				const { createIndex, close } = await import('pagefind');
				const site = fileURLToPath(dir);
				const { index, errors } = await createIndex({});
				if (!index) throw new Error(`Pagefind could not start: ${errors.join(', ')}`);
				const added = await index.addDirectory({ path: site });
				if (added.errors.length) throw new Error(`Pagefind could not index the site: ${added.errors.join(', ')}`);
				await index.writeFiles({ outputPath: join(site, 'pagefind') });
				await close();
				logger.info(`Search: scanned ${added.page_count} pages; only those marked data-pagefind-body are searchable.`);
			},
		},
	};
}
