// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	site: 'https://docs.sculk.gg',
	integrations: [
		starlight({
			title: 'Sculk Studio',
			description: 'The Kotlin-first Minecraft plugin framework.',
			logo: {
				src: './logo.svg',
				replacesTitle: false,
			},
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/SculkStudios/sculk-studio' },
			],
			editLink: {
				baseUrl: 'https://github.com/SculkStudios/sculk-studio/edit/main/docs/',
			},
			customCss: ['./src/styles/sculk.css'],
			sidebar: [
				{
					label: 'Introduction',
					items: [
						{ label: 'What is Sculk Studio?', slug: 'introduction/overview' },
						{ label: 'Architecture', slug: 'introduction/architecture' },
					],
				},
				{
					label: 'Getting Started',
					items: [
						{ label: 'Installation', slug: 'getting-started/installation' },
						{ label: 'Your First Plugin', slug: 'getting-started/first-plugin' },
					],
				},
				{
					label: 'Commands',
					items: [
						{ label: 'Overview', slug: 'commands/overview' },
						{ label: 'Subcommands & Arguments', slug: 'commands/subcommands' },
					],
				},
				{
					label: 'GUIs',
					items: [
						{ label: 'Overview', slug: 'gui/overview' },
						{ label: 'Pagination', slug: 'gui/pagination' },
					],
				},
				{
					label: 'Config',
					items: [
						{ label: 'Overview', slug: 'config/overview' },
						{ label: 'Hot Reload', slug: 'config/hot-reload' },
					],
				},
				{
					label: 'Series',
					items: [
						{ label: 'Overview', slug: 'series/overview' },
						{ label: 'Registries', slug: 'series/registries' },
					],
				},
				{
					label: 'Effects',
					items: [
						{ label: 'Particles & Sounds', slug: 'effects/particles-sounds' },
						{ label: 'Timelines & Sequences', slug: 'effects/timelines' },
					],
				},
				{
					label: 'Data',
					items: [
						{ label: 'Overview', slug: 'data/overview' },
						{ label: 'Caching', slug: 'data/caching' },
					],
				},
				{
					label: 'Advanced',
					items: [
						{ label: 'API Stability', slug: 'advanced/api-stability' },
						{ label: 'Java Compatibility', slug: 'advanced/java-compat' },
						{ label: 'Performance', slug: 'advanced/performance' },
					],
				},
			],
		}),
	],
});
