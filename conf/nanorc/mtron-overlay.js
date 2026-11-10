// presentation-only mtron modes that the flat nanorc rule model cannot express
// (REPL transcript chrome, doc callouts, log level tags, box-drawing glyphs).
// NanorcToHljs splices this fragment into the generated mtron.min.js, inside the
// language factory, after the generated `const MTRON_RULES = [...]` it references.
// The result-line region re-highlights its body with MTRON_RESULT_RULES — the
// prompt-safe subset the generator filters out of MTRON_RULES — so the region
// always closes at the next mtron> expression.
// The language tokens themselves live in mtron.nanorc — this file must NOT grow
// rules that the nanorc can carry.

            const MTRON_DOC_CALLOUT = {
                className: 'mtron-doc-callout',
                match: /(?<=\s)\u200B[0-9]+\u200B/,
                returnBegin: false,
                relevance: 10
            }
            const MTRON_PROMPT_BEGIN = {
                className: 'mtron-prompt-begin',
                begin: /(?<=^|\n)mtron(?=>)/,
                returnBegin: false,
                relevance: 0
            }
            const MTRON_PROMPT_END = {
                className: 'mtron-prompt-end',
                begin: /(?<=(?:^|\n)mtron)>/,
                returnBegin: false,
                relevance: 0
            }
            const MTRON_RESULT_PREFIX = {
                className: 'mtron-result-line',
                match: /(?<=^|\n)==+>/,
                returnBegin: false,
                relevance: 10
            }
            const MTRON_LOG_INFO = {
                className: 'mtron-log-info',
                match: /\[INFO\s*]/
            }
            const MTRON_LOG_ERROR = {
                className: 'mtron-log-error',
                match: /\[ERROR\s*]/
            }
            const MTRON_LOG_WARN = {
                className: 'mtron-log-warn',
                match: /\[WARN\s*]/
            }
            const MTRON_LOG_DEBUG = {
                className: 'mtron-log-debug',
                match: /\[DEBUG\s*]/
            }
            const MTRON_LOG_TRACE = {
                className: 'mtron-log-trace',
                match: /\[TRACE\s*]/
            }
            const MTRON_PIPE = {
                className: 'mtron-pipe',
                begin: /[┌├┐└┘│┤─⋰⋱⮝⮞⮜⮟▼▲┼┬┴►◄]/,
                returnBegin: false,
                relevance: 0
            }
            const PHASESHIFT_STUDIO_HEADER = {
                className: 'phaseshift-studio-header',
                match: /(PhaseShift Studio Presents)/
            }
            const DOCS_IF_ELSE_THEN_CASE_DO = {
                className: 'docs-if-else-then-case-do',
                begin: /\((if|else|else if|then|case|do)\)/
            }
            const MTRON_RESULT_LINE = {
                className: 'mtron-result-line',
                begin: /(?<=^|\n)==+>/,
                end: /(?=^mtron>)/m,
                contains: [
                    MTRON_RESULT_PREFIX,
                    MTRON_RESULT_RULES,
                    MTRON_LOG_INFO,
                    MTRON_LOG_ERROR,
                    MTRON_LOG_WARN,
                    MTRON_LOG_DEBUG,
                    MTRON_LOG_TRACE,
                    MTRON_PIPE
                ],
                relevance: 0
            }
            const MTRON_OVERLAY = [
                MTRON_DOC_CALLOUT,
                MTRON_PROMPT_BEGIN,
                MTRON_PROMPT_END,
                PHASESHIFT_STUDIO_HEADER,
                DOCS_IF_ELSE_THEN_CASE_DO,
                MTRON_RESULT_LINE,
                MTRON_LOG_INFO,
                MTRON_LOG_ERROR,
                MTRON_LOG_WARN,
                MTRON_LOG_DEBUG,
                MTRON_LOG_TRACE,
                MTRON_PIPE
            ]
