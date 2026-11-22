/*
 * metatron JS SDK — the single import for future JS/metatron projects.
 *
 *   import { furi, mtronObj, serializer, mtronIO, envelope, form, objPanels }
 *     from './index.js';
 *
 *   furi.f('/a/b').segmentLength()           // addresses (fURI)
 *   mtronIO.read('int::29@/usr/marko/age')   // mtron value grammar
 *   envelope.parse(text).tid                 // an answer's type + vid
 *
 * Layered one-way — sdk/ (pure, no DOM) → ui/ (browser) → app/ (page entry
 * points). The sdk/ namespaces run anywhere (node, workers, tests); form and
 * objPanels are browser-only.
 */
export * as furi from './sdk/furi.js';
export * as mtronObj from './sdk/mtron-obj.js';
export * as serializer from './sdk/obj_mtron_serializer.js';
export * as mtronIO from './sdk/mtron-io.js';
export * as envelope from './sdk/mtron-envelope.js';
export * as form from './ui/mtron-form.js';
export * as objPanels from './ui/mtron-obj-panels.js';
export * as dom from './ui/mtron-dom.js';
