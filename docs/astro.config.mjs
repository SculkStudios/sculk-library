// @ts-check
import mdx from '@astrojs/mdx';
import { satteri } from '@astrojs/markdown-satteri';
import sitemap from '@astrojs/sitemap';
import expressiveCode from 'astro-expressive-code';
import { defineConfig } from 'astro/config';
import { pagefind } from './src/lib/pagefind-integration.mjs';

export default defineConfig({
	site: 'https://docs.sculk.studio',
	trailingSlash: 'always',
	build: { format: 'directory' },
	// Pages that moved to the section their sidebar category names.
	redirects: { '/guides/canvas/': '/start/canvas/', '/guides/testing/': '/concepts/testing/' },
	// Expressive Code hooks into Sätteri only when the processor is set explicitly.
	markdown: { processor: satteri() },
	integrations: [
		expressiveCode({
			// Catppuccin Mocha, the owner's editor theme (MIT), on its own crust and mantle shades.
			themes: ['catppuccin-mocha'],
			// Lifts Catppuccin's dimmer tokens, comments above all, to read clearly on the dark block.
			minSyntaxHighlightingColorContrast: 7.5,
			// Long lines wrap, keeping their indentation, instead of running off the column.
			// Shell commands use the plain code frame: a terminal frame adds an empty title bar to every one.
			defaultProps: { wrap: true, preserveIndent: true, overridesByLang: { 'bash,sh,shell,powershell': { frame: 'code' } } },
			styleOverrides: {
				borderRadius: '10px',
				borderColor: 'var(--rule-strong)',
				codeFontFamily: 'var(--mono)',
				codeFontSize: '14px',
				codeLineHeight: '1.7',
				uiFontFamily: 'var(--read)',
				frames: {
					editorBackground: 'var(--code-bg)',
					terminalBackground: 'var(--code-bg)',
					editorTabBarBackground: 'var(--code-bar)',
					terminalTitlebarBackground: 'var(--code-bar)',
					editorActiveTabBackground: 'var(--code-bar)',
					editorActiveTabIndicatorTopColor: 'transparent',
					editorActiveTabIndicatorBottomColor: 'var(--text)',
					editorActiveTabForeground: 'var(--text)',
					editorTabBarBorderBottomColor: 'var(--rule)',
					terminalTitlebarBorderBottomColor: 'var(--rule)',
					frameBoxShadowCssValue: 'none',
				},
			},
		}),
		mdx(),
		sitemap({ filter: (page) => !page.includes('/404') }),
		pagefind(),
	],
});
