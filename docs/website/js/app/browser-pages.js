/* ── the browser's copy of a portal page ──────────────────────────────────
   a page edited in the layout sidepanel lives in the grid, not on the machine until
   save as…: this keeps that layout in the browser's own database (IndexedDB) so the
   next visit restores it. a record remembers the machine page it came from by the
   HASH of that page's source, so the machine's page wins when it has changed since
   (a moved file is newer than the copy) — and is replaced by the copy's cards when the
   machine still serves the same page (the copy is then the edit).

   the store is one object store, keyed by machine + page address:
       "<ws base> <page address>" -> {cards, page, sourceHash, saved}
   every call answers a promise; a browser without IndexedDB simply refuses, and the
   callers treat that as "no copy". */

const DB_NAME = "metatron-portal";
const STORE = "pages";
const VERSION = 1;

/* a stable, cheap fingerprint of a page's source text (FNV-1a, 32 bit) */
export function hash(text) {
    const source = String(text == null ? "" : text);
    let value = 2166136261;
    for (let i = 0; i < source.length; i += 1) {
        value ^= source.charCodeAt(i);
        value = Math.imul(value, 16777619);
    }
    return (value >>> 0).toString(16);
}

let opening = null;

function openDb() {
    if (opening) return opening;
    opening = new Promise((resolve, reject) => {
        if (typeof indexedDB === "undefined" || !indexedDB) {
            reject(new Error("this browser has no indexeddb"));
            return;
        }
        const request = indexedDB.open(DB_NAME, VERSION);
        request.onupgradeneeded = () => {
            const db = request.result;
            if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
        };
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error || new Error("indexeddb would not open"));
        request.onblocked = () => reject(new Error("indexeddb is blocked by another tab"));
    });
    return opening;
}

function run(mode, act) {
    return openDb().then((db) => new Promise((resolve, reject) => {
        const transaction = db.transaction(STORE, mode);
        const store = transaction.objectStore(STORE);
        const request = act(store);
        transaction.oncomplete = () => resolve(request ? request.result : undefined);
        transaction.onerror = () => reject(transaction.error || new Error("indexeddb transaction failed"));
        transaction.onabort = () => reject(transaction.error || new Error("indexeddb transaction aborted"));
    }));
}

export function readPage(key) {
    return run("readonly", (store) => store.get(String(key)));
}

export function writePage(key, record) {
    return run("readwrite", (store) => store.put(record, String(key)));
}

export function forgetPage(key) {
    return run("readwrite", (store) => store.delete(String(key)));
}
