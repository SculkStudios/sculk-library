// The code theme, drawn from the site's palette: bone text on near-black, teal keywords, sand
// strings, violet types, pale-blue numbers. Every token colour clears 4.5:1 on the code ground.
export const atlasTheme = {
	name: 'sculk-atlas',
	type: 'dark',
	colors: {
		'editor.background': '#0b0e10',
		'editor.foreground': '#f3f0e7',
		'editor.selectionBackground': '#f0954f40',
		'editorLineNumber.foreground': '#5d625e',
		'terminal.ansiGreen': '#7fb8b2',
		'terminal.ansiRed': '#e08a7c',
		'terminal.ansiYellow': '#d6c49b',
		'terminal.ansiBlue': '#9fb6e0',
	},
	tokenColors: [
		{ scope: ['comment', 'punctuation.definition.comment'], settings: { foreground: '#9aa19c', fontStyle: 'italic' } },
		{ scope: ['keyword', 'storage', 'storage.type', 'storage.modifier', 'keyword.control'], settings: { foreground: '#5fe0d2' } },
		{ scope: ['string', 'string.quoted', 'string.template'], settings: { foreground: '#f2d58a' } },
		{ scope: ['constant.character.escape', 'constant.other.placeholder'], settings: { foreground: '#e3b98a' } },
		{ scope: ['constant.numeric', 'constant.language', 'constant.language.boolean'], settings: { foreground: '#9fd0ff' } },
		{ scope: ['entity.name.type', 'entity.name.class', 'support.class', 'support.type', 'entity.other.inherited-class'], settings: { foreground: '#c7bbff' } },
		{ scope: ['entity.name.function', 'support.function', 'meta.function-call'], settings: { foreground: '#ffffff' } },
		{ scope: ['meta.annotation', 'storage.type.annotation', 'punctuation.definition.annotation'], settings: { foreground: '#ffc66d' } },
		{ scope: ['variable.parameter'], settings: { foreground: '#d8d4c8' } },
		{ scope: ['punctuation', 'meta.brace'], settings: { foreground: '#a9aba2' } },
		{ scope: ['entity.name.tag', 'meta.tag'], settings: { foreground: '#7fb8b2' } },
		{ scope: ['entity.other.attribute-name', 'support.type.property-name', 'meta.object-literal.key'], settings: { foreground: '#c4bfe8' } },
		{ scope: ['markup.inserted'], settings: { foreground: '#7fb8b2' } },
		{ scope: ['markup.deleted'], settings: { foreground: '#e08a7c' } },
	],
};
