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
						{ label: 'Modules', slug: 'introduction/modules' },
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
						{ label: 'Scheduler', slug: 'core/scheduler' },
						{ label: 'Events', slug: 'core/events' },
						{ label: 'Tasks', slug: 'tasks/overview' },
						{ label: 'Testing', slug: 'core/testing' },
					],
				},
				{
					label: 'Text',
					items: [
						{ label: 'Messages', slug: 'text/overview' },
						{ label: 'Themes', slug: 'text/theme' },
						{ label: 'Placeholders', slug: 'text/placeholders' },
						{ label: 'Measuring Text', slug: 'text/font' },
						{ label: 'Localisation', slug: 'text/localisation' },
					],
				},
				{
					label: 'Commands',
					items: [
						{ label: 'Commands', slug: 'commands/overview' },
						{ label: 'Subcommands', slug: 'commands/subcommands' },
					],
				},
				{
					label: 'Menus',
					items: [
						{ label: 'Menus', slug: 'gui/overview' },
						{ label: 'Pagination', slug: 'gui/pagination' },
						{ label: 'Animations', slug: 'gui/animations' },
					],
				},
				{
					label: 'HUD',
					items: [
						{ label: 'Overview', slug: 'hud/overview' },
						{ label: 'Sidebar', slug: 'hud/sidebar' },
						{ label: 'Action Bar', slug: 'hud/action-bar' },
						{ label: 'Placeholders', slug: 'hud/placeholders' },
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
					label: 'Data',
					items: [
						{ label: 'Overview', slug: 'data/overview' },
						{ label: 'Queries', slug: 'data/query' },
						{ label: 'Caching', slug: 'data/caching' },
						{ label: 'Backends', slug: 'data/backends' },
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
					label: 'Visual',
					items: [
						{ label: 'Particles & Sounds', slug: 'visual/particles-sounds' },
						{ label: 'Timelines', slug: 'visual/timelines' },
						{ label: 'Holograms', slug: 'visual/holograms' },
						{ label: 'Nametags', slug: 'visual/nametags' },
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
					label: 'Packets',
					items: [
						{ label: 'Overview', slug: 'packets/overview' },
						{ label: 'Client Blocks', slug: 'packets/client-blocks' },
						{ label: 'Virtual Entities', slug: 'packets/virtual-entities' },
						{ label: 'PacketEvents', slug: 'packets/packetevents' },
						{ label: 'ProtocolLib', slug: 'packets/protocollib' },
						{ label: 'Performance', slug: 'packets/performance' },
					],
				},
				{
					label: 'Platform',
					items: [
						{ label: 'Services', slug: 'platform/services' },
						{ label: 'Integrations', slug: 'integrations/overview' },
					],
				},
				{
					label: 'Recipes',
					items: [
						{ label: 'Economy Plugin', slug: 'recipes/economy-plugin' },
						{ label: 'Server Menu', slug: 'recipes/server-menu' },
						{ label: 'Staff Tools', slug: 'recipes/staff-tools' },
					],
				},
				{
					label: 'Advanced',
					items: [
						{ label: "What's New in 5.0", slug: 'advanced/whats-new-in-5' },
						{ label: 'Coroutines', slug: 'advanced/coroutines' },
						{ label: 'Folia & Canvas', slug: 'advanced/folia' },
						{ label: 'Performance', slug: 'advanced/performance' },
						{ label: 'API Stability', slug: 'advanced/api-stability' },
						{ label: 'Troubleshooting', slug: 'advanced/troubleshooting' },
					],
				},
			],
		}),
	],
});
