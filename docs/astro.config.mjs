// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// The sidebar is generated from the content directories; a page sets its position with
// `sidebar.order` in its frontmatter. The manifest-driven sidebar described in DOCS_ARCHITECTURE.md
// replaces this once the manifest generator exists.
export default defineConfig({
	site: 'https://docs.sculk.studio',
	integrations: [
		starlight({
			title: 'Sculk Studio',
			description: 'The Kotlin platform for Paper, Folia, Canvas and Velocity plugins.',
			logo: {
				light: './src/assets/logo-light.svg',
				dark: './src/assets/logo-dark.svg',
				alt: 'Sculk Studio',
				replacesTitle: true,
			},
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/SculkStudios/sculk-library' },
			],
			editLink: {
				baseUrl: 'https://github.com/SculkStudios/sculk-library/edit/dev/docs/',
			},
			customCss: ['./src/styles/sculk.css'],
			sidebar: [
				{ label: 'Start', items: [{ autogenerate: { directory: 'start' } }] },
				{ label: 'Concepts', items: [{ autogenerate: { directory: 'concepts' } }] },
				{ label: 'Guides', items: [{ autogenerate: { directory: 'guides' } }] },
				{ label: 'Reference', items: [{ autogenerate: { directory: 'reference' } }] },
			],
		}),
	],
});
