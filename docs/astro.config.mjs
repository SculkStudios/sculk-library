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
	// Prose-heavy pages: keep the spaces between inline elements that 'jsx' compression removes.
	compressHTML: true,
	// Expressive Code hooks into Sätteri only when the processor is set explicitly.
	markdown: { processor: satteri() },
	integrations: [
		expressiveCode({
			// Catppuccin Mocha, the owner's editor theme (MIT), on its own crust and mantle shades.
			themes: ['catppuccin-mocha'],
			// Long lines wrap, keeping their indentation, instead of running off the column.
			defaultProps: { wrap: true, preserveIndent: true },
			styleOverrides: {
				borderRadius: '8px',
				borderColor: 'var(--rule)',
				codeFontFamily: 'var(--mono)',
				codeFontSize: '13.5px',
				codeLineHeight: '1.7',
				uiFontFamily: 'var(--read)',
				frames: {
					editorBackground: 'var(--code-bg)',
					terminalBackground: 'var(--code-bg)',
					editorTabBarBackground: 'var(--code-bar)',
					terminalTitlebarBackground: 'var(--code-bar)',
					editorActiveTabBackground: 'var(--code-bar)',
					editorActiveTabIndicatorTopColor: 'transparent',
					editorActiveTabIndicatorBottomColor: 'var(--text-3)',
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
