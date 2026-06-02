// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	site: 'https://docs.sculk.studio',
	integrations: [
		starlight({
			title: 'Sculk Studio',
			description: 'The Kotlin-first Minecraft plugin framework.',
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
				baseUrl: 'https://github.com/SculkStudios/sculk-library/edit/main/docs/',
			},
			customCss: ['./src/styles/sculk.css'],
			sidebar: [
				{
					label: 'Introduction',
					items: [
						{ label: 'What is Sculk Studio?', slug: 'introduction/overview' },
						{ label: 'Modules & Architecture', slug: 'introduction/modules' },
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
					label: 'Core',
					items: [
						{ label: 'Events', slug: 'core/events' },
						{ label: 'Scheduler', slug: 'core/scheduler' },
						{ label: 'Coroutines', slug: 'advanced/coroutines' },
						{ label: 'Messaging', slug: 'core/messaging' },
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
						{ label: 'Animations & Inputs', slug: 'gui/animations' },
					],
				},
				{
					label: 'Items',
					items: [
						{ label: 'Overview', slug: 'items/overview' },
						{ label: 'Data Components', slug: 'items/components' },
						{ label: 'Persistent Data', slug: 'items/persistent-data' },
						{ label: 'Config Items', slug: 'items/config-items' },
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
						{ label: 'Querying, Transactions & Redis', slug: 'data/query' },
					],
				},
				{
					label: 'Localization',
					items: [
						{ label: 'Overview', slug: 'text/overview' },
					],
				},
				{
					label: 'Tasks',
					items: [
						{ label: 'Tasks & Cron', slug: 'tasks/overview' },
					],
				},
				{
					label: 'Integrations',
					items: [
						{ label: 'Overview', slug: 'integrations/overview' },
					],
				},
				{
					label: 'Packets',
					items: [
						{ label: 'Overview', slug: 'packets/overview' },
						{ label: 'PacketEvents', slug: 'packets/packetevents' },
						{ label: 'ProtocolLib', slug: 'packets/protocollib' },
						{ label: 'Performance', slug: 'packets/performance' },
					],
				},
				{
					label: 'Recipes',
					items: [
						{ label: 'Economy Plugin', slug: 'recipes/economy-plugin' },
						{ label: 'Player Profiles', slug: 'recipes/player-profiles' },
						{ label: 'Server Menu', slug: 'recipes/server-menu' },
						{ label: 'Staff Tools', slug: 'recipes/staff-tools' },
						{ label: 'Crate System', slug: 'recipes/crate-system' },
						{ label: 'Kits Plugin', slug: 'recipes/kits-plugin' },
					],
				},
				{
					label: 'Advanced',
					items: [
						{ label: 'API Stability', slug: 'advanced/api-stability' },
						{ label: 'Performance', slug: 'advanced/performance' },
						{ label: 'Folia & Canvas', slug: 'advanced/folia' },
						{ label: 'API Design', slug: 'advanced/api-design' },
						{ label: 'XSeries Migration', slug: 'advanced/xseries-migration' },
						{ label: 'Migrating to Sculk 4.0', slug: 'advanced/migration-to-sculk-4' },
						{ label: 'Migration to Sculk 3', slug: 'advanced/migration-to-sculk-3' },
						{ label: 'Migration Checklist', slug: 'advanced/migration-checklist' },
						{ label: 'Troubleshooting', slug: 'advanced/troubleshooting' },
					],
				},
			],
		}),
	],
});
