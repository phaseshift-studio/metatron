/**
 * Metatron Web Console
 * WebSocket-based UI for browsing and interacting with mtron spaces.
 *
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC — AGPL-3.0
 */

// ─── Top-level Configuration ──────────────────────────────────────────────────
// Keeping these outside the class makes them easy to find and edit.

const PANEL_REGISTRY = {
    spaces:        { title: 'active spaces',      icon: 'bi-collection',        defaultOpen: true,  category: 'core'  },
    tree:          { title: 'uri address space',   icon: 'bi-diagram-3',         defaultOpen: true,  category: 'core'  },
    inspector:     { title: 'obj inspector',       icon: 'bi-search',            defaultOpen: true,  category: 'core'  },
    console:       { title: 'mtron console',       icon: 'bi-terminal',          defaultOpen: true,  category: 'core'  },
    llmAgent:      { title: 'llm agent designer',  icon: 'bi-robot',             defaultOpen: false, category: 'tools' },
    connectSpaces: { title: 'connect spaces',      icon: 'bi-link-45deg',        defaultOpen: false, category: 'tools' },
    metrics:       { title: 'metrics',             icon: 'bi-graph-up',          defaultOpen: false, category: 'tools' },
};

const LAYOUT_TEMPLATES = {
    default: {
        name: 'Default Layout',
        description: 'Spaces | Tree | Console/Inspector stacked',
        layout: {
            type: 'split', direction: 'horizontal',
            children: [
                { type: 'panel', panelId: 'spaces',  size: 20 },
                { type: 'panel', panelId: 'tree',    size: 20 },
                { type: 'split', direction: 'vertical', size: 60, children: [
                    { type: 'panel', panelId: 'console',   size: 30 },
                    { type: 'panel', panelId: 'inspector', size: 70 },
                ]},
            ],
        },
    },
    ide: {
        name: 'IDE Layout',
        description: 'Sidebar + main area + bottom console',
        layout: {
            type: 'split', direction: 'horizontal',
            children: [
                { type: 'panel', panelId: 'tree', size: 20 },
                { type: 'split', direction: 'vertical', size: 80, children: [
                    { type: 'panel', panelId: 'inspector', size: 70 },
                    { type: 'panel', panelId: 'console',   size: 30 },
                ]},
            ],
        },
    },
    grid2x2: {
        name: '2×2 Grid',
        description: 'Four panels in a 2×2 grid',
        layout: {
            type: 'split', direction: 'horizontal',
            children: [
                { type: 'split', direction: 'vertical', size: 50, children: [
                    { type: 'panel', panelId: 'spaces', size: 50 },
                    { type: 'panel', panelId: 'tree',   size: 50 },
                ]},
                { type: 'split', direction: 'vertical', size: 50, children: [
                    { type: 'panel', panelId: 'inspector', size: 50 },
                    { type: 'panel', panelId: 'console',   size: 50 },
                ]},
            ],
        },
    },
    dashboard: {
        name: 'Dashboard (3 Columns)',
        description: 'Three columns, right column split',
        layout: {
            type: 'split', direction: 'horizontal',
            children: [
                { type: 'panel', panelId: 'spaces', size: 25 },
                { type: 'panel', panelId: 'tree',   size: 40 },
                { type: 'split', direction: 'vertical', size: 35, children: [
                    { type: 'panel', panelId: 'inspector', size: 50 },
                    { type: 'panel', panelId: 'console',   size: 50 },
                ]},
            ],
        },
    },
    triple: {
        name: 'Triple Column',
        description: 'Three equal columns',
        layout: {
            type: 'split', direction: 'horizontal',
            children: [
                { type: 'panel', panelId: 'spaces',    size: 33 },
                { type: 'panel', panelId: 'tree',      size: 33 },
                { type: 'panel', panelId: 'inspector', size: 34 },
            ],
        },
    },
    sidebar: {
        name: 'Sidebar + Main',
        description: 'Narrow sidebar with wide main area',
        layout: {
            type: 'split', direction: 'horizontal',
            children: [
                { type: 'panel', panelId: 'spaces', size: 20 },
                { type: 'panel', panelId: 'tree',   size: 80 },
            ],
        },
    },
};

// Keys used for localStorage persistence.
const STORAGE_KEYS = {
    panels:     'mtron-mwebConsole-panels',
    panelOrder: 'mtron-mwebConsole-panel-order',
    layoutTree: 'mtron-mwebConsole-layout-tree',
    locked:     'mtron-mwebConsole-locked',
};

// ─── Main Class ───────────────────────────────────────────────────────────────

class MetatronDashboard {

    constructor() {
        // WebSocket state — main socket for user queries, bg socket for decorative queries
        this.socket               = null;
        this.connected            = false;
        this.callbackQueue        = [];       // FIFO queue for main socket (one callback per send)
        this.bgSocket             = null;
        this.bgCallbackQueue      = [];       // FIFO queue for background socket
        this.backgroundQueryQueue = [];       // pending (not-yet-sent) background queries
        this.backgroundQueryTimer = null;
        this.backgroundQueryDelay = 150;      // ms between background query sends

        // UI state
        this.selectedSpace         = null;
        this.treeState             = new Map(); // uri → boolean (expanded?)
        this.resizeState           = null;
        this.resizeListenersAdded  = false;
        this.currentInspectorUri   = null;      // last URI focused in the inspector

        // Agent designer state (skills and tools managed generically)
        this.agentItems = { skills: [], tools: ['!*eval'] };

        // Wire render callbacks into panel registry
        this.panelRegistry = Object.fromEntries(
            Object.entries(PANEL_REGISTRY).map(([id, cfg]) => {
                const methodName = `render${id[0].toUpperCase()}${id.slice(1)}Panel`;
                return [id, { id, ...cfg, render: () => this[methodName]() }];
            })
        );

        // Load persisted state
        this.openPanels   = this._loadState(STORAGE_KEYS.panels,
            () => new Set(Object.keys(PANEL_REGISTRY).filter(id => PANEL_REGISTRY[id].defaultOpen)),
            raw => new Set(JSON.parse(raw)));
        this.panelOrder   = this._loadState(STORAGE_KEYS.panelOrder,
            () => Object.keys(this.panelRegistry),
            raw => JSON.parse(raw).filter(id => this.panelRegistry[id]));
        this.layoutTree   = this._loadState(STORAGE_KEYS.layoutTree,
            () => LAYOUT_TEMPLATES.default.layout,
            raw => JSON.parse(raw));
        this.panelsLocked = this._loadState(STORAGE_KEYS.locked,
            () => false,
            raw => raw === 'true');

        this._initNavElements();
        this._updatePanelLockButton();
        this._renderPanelMenu();
        this._renderPanels();
        this._initElements();
        this._initEventListeners();
    }

    // ─── State Persistence ────────────────────────────────────────────────────
    // Generic localStorage helpers replace four separate load/save pairs.

    _loadState(key, fallback, parse) {
        try {
            const raw = localStorage.getItem(key);
            if (raw !== null) return parse(raw);
        } catch { /* ignore */ }
        return fallback();
    }

    _saveState(key, value) {
        try {
            const s = value instanceof Set    ? JSON.stringify([...value])
                    : typeof value === 'boolean' ? String(value)
                    : JSON.stringify(value);
            localStorage.setItem(key, s);
        } catch { /* ignore */ }
    }

    // ─── WebSocket ────────────────────────────────────────────────────────────

    toggleConnection() {
        this.connected ? this._disconnect() : this._connect();
    }

    _connect() {
        const url = this.wsEndpoint.value.trim();
        if (!url) return;
        try {
            this._updateStatus('connecting');
            this.socket = new WebSocket(url);
            this.socket.binaryType = 'arraybuffer';
            this.socket.onopen = () => {
                this.connected = true;
                this._updateStatus('connected');
                // Open a dedicated secondary socket for background (decorative) queries.
                // Because the server is stateless, this connection is fully independent.
                // Background queries never block the main socket's callback queue.
                this.bgSocket = new WebSocket(url);
                this.bgSocket.binaryType = 'arraybuffer';
                this.bgSocket.onmessage  = e   => this._handleBgMessage(e);
                this.bgSocket.onerror    = err => console.warn('bg socket error:', err);
                this.bgSocket.onclose    = ()  => { this.bgSocket = null; };
                this.loadSpaces();
                this._loadDefaultTree();
                if (this.openPanels.has('llmAgent')) this._loadAgentProviders();
                // Warm up the /m instruction namespace on connect so the first
                // user click on it responds immediately instead of waiting 8+ seconds
                // for the server's lazy-init scan. Runs on bgSocket, invisible to user.
                this.bgSocket.addEventListener('open', () => {
                    this._sendBackgroundQuery("'*</m/+/>'./m/web/inst/doc_json()", () => {});
                }, { once: true });
            };
            this.socket.onmessage = e => this._handleMessage(e);
            this.socket.onclose   = () => {
                this.connected = false;
                this._updateStatus('disconnected');
                this.bgSocket?.close();
                this.socket = null;
            };
            this.socket.onerror = err => console.error('websocket error:', err);
        } catch (err) {
            console.error('connection failed:', err);
        }
    }

    _disconnect() {
        this.socket?.close();
        this.bgSocket?.close();
        this.socket          = null;
        this.bgSocket        = null;
        this.connected       = false;
        this.callbackQueue   = [];
        this.bgCallbackQueue = [];
        this._cancelBackgroundQueries();
        this._updateStatus('disconnected');
    }

    _updateStatus(status) {
        const el  = this.connectionStatus;
        const cfg = {
            connected:    { cls: 'bg-success', html: '<i class="bi bi-wifi me-1"></i>connected',             btn: '<i class="bi bi-x-lg"></i>' },
            connecting:   { cls: 'bg-warning', html: '<i class="bi bi-hourglass-split me-1"></i>connecting…', btn: null },
            disconnected: { cls: 'bg-danger',  html: '<i class="bi bi-wifi-off me-1"></i>disconnected',      btn: '<i class="bi bi-plug"></i>' },
        }[status] ?? { cls: 'bg-danger', html: 'unknown', btn: null };

        el.className = `badge me-3 ${cfg.cls}`;
        el.innerHTML = cfg.html;
        if (cfg.btn) this.connectBtn.innerHTML = cfg.btn;
    }

    // Send a query on the main socket. Cancels pending (unsent) background queries
    // so they don't queue up behind this one, but never blocks on already-sent
    // background queries (those run on the separate bg socket).
    sendQuery(code, callback) {
        if (!this.connected || !this.socket) {
            callback?.(null, 'not connected');
            return;
        }
        this._cancelBackgroundQueries();
        this.callbackQueue.push({ callback, code, timestamp: Date.now() });
        this.socket.send(new TextEncoder().encode(code));
    }

    _handleMessage(event) {
        const data = event.data instanceof ArrayBuffer
            ? new TextDecoder('utf-8').decode(event.data)
            : event.data;
        this.callbackQueue.shift()?.callback?.(data, null);
    }

    // Background queries use the dedicated bgSocket so they never share the main
    // socket's callback queue. Type icons and doc indicators load concurrently
    // without ever delaying user-triggered queries.
    _sendBackgroundQuery(code, callback) {
        if (!this.connected) return;
        this.backgroundQueryQueue.push({ code, callback });
        this._scheduleBackgroundQuery();
    }

    _scheduleBackgroundQuery() {
        if (this.backgroundQueryTimer || this.backgroundQueryQueue.length === 0) return;
        this.backgroundQueryTimer = setTimeout(() => {
            this.backgroundQueryTimer = null;
            if (!this.connected || this.backgroundQueryQueue.length === 0) return;
            // Wait for bgSocket to be open (it opens shortly after the main socket)
            if (!this.bgSocket || this.bgSocket.readyState !== WebSocket.OPEN) {
                this._scheduleBackgroundQuery(); // retry after another delay
                return;
            }
            const { code, callback } = this.backgroundQueryQueue.shift();
            this.bgCallbackQueue.push({ callback, code });
            this.bgSocket.send(new TextEncoder().encode(code));
            this._scheduleBackgroundQuery();
        }, this.backgroundQueryDelay);
    }

    _handleBgMessage(event) {
        const data = event.data instanceof ArrayBuffer
            ? new TextDecoder('utf-8').decode(event.data)
            : event.data;
        this.bgCallbackQueue.shift()?.callback?.(data, null);
    }

    _cancelBackgroundQueries() {
        this.backgroundQueryQueue = [];
        if (this.backgroundQueryTimer) {
            clearTimeout(this.backgroundQueryTimer);
            this.backgroundQueryTimer = null;
        }
    }

    // ─── Panel System ─────────────────────────────────────────────────────────

    _refreshPanelUI() {
        this._saveState(STORAGE_KEYS.panels, this.openPanels);
        this._renderPanelMenu();
        this._renderPanels();
        this._initElements();
        this._initPanelEventListeners();
    }

    togglePanelLock() {
        this.panelsLocked = !this.panelsLocked;
        this._saveState(STORAGE_KEYS.locked, this.panelsLocked);
        this._updatePanelLockButton();
        this._refreshPanelUI();
    }

    _updatePanelLockButton() {
        const btn = document.getElementById('panelLockBtn');
        if (!btn) return;
        if (this.panelsLocked) {
            btn.innerHTML = '<i class="bi bi-lock-fill"></i>';
            btn.className = 'btn btn-sm btn-warning me-2';
            btn.title     = 'panels locked — click to unlock';
        } else {
            btn.innerHTML = '<i class="bi bi-unlock"></i>';
            btn.className = 'btn btn-sm btn-outline-secondary me-2';
            btn.title     = 'lock panels (prevent accidental close)';
        }
    }

    _renderPanelMenu() {
        const menu = document.getElementById('panelMenu');
        if (!menu) return;

        // Group panels by category
        const byCategory = {};
        for (const [id, panel] of Object.entries(this.panelRegistry)) {
            (byCategory[panel.category ?? 'other'] ??= []).push({ id, ...panel });
        }

        let html = '';
        for (const [cat, panels] of Object.entries(byCategory)) {
            html += `<li><h6 class="dropdown-header">${cat}</h6></li>`;
            for (const p of panels) {
                const checked = this.openPanels.has(p.id);
                html += `
                    <li>
                        <a class="dropdown-item d-flex align-items-center" href="#"
                           onclick="mwebConsole.togglePanel('${p.id}'); return false;">
                            <i class="bi ${p.icon} me-2"></i>
                            <span class="flex-grow-1">${p.title}</span>
                            <i class="bi ${checked ? 'bi-check-square text-success' : 'bi-square'} ms-2"></i>
                        </a>
                    </li>`;
            }
        }
        html += `
            <li><hr class="dropdown-divider"></li>
            <li>
                <a class="dropdown-item text-muted small" href="#"
                   onclick="mwebConsole.resetPanels(); return false;">
                    <i class="bi bi-arrow-counterclockwise me-2"></i>reset to defaults
                </a>
            </li>`;
        menu.innerHTML = html;
    }

    togglePanel(panelId) {
        if (this.openPanels.has(panelId)) {
            this.openPanels.delete(panelId);
        } else {
            this.openPanels.add(panelId);
            if (!this.panelOrder.includes(panelId)) this.panelOrder.push(panelId);
        }
        this._refreshPanelUI();
        if (this.connected && this.openPanels.has(panelId)) {
            if (panelId === 'spaces')   this.loadSpaces();
            if (panelId === 'tree')     this._loadDefaultTree();
            if (panelId === 'llmAgent') this._loadAgentProviders();
        }
    }

    closePanel(panelId) {
        this.openPanels.delete(panelId);
        this._refreshPanelUI();
    }

    resetPanels() {
        this.openPanels = new Set(Object.keys(PANEL_REGISTRY).filter(id => PANEL_REGISTRY[id].defaultOpen));
        this.layoutTree = LAYOUT_TEMPLATES.default.layout;
        this._saveState(STORAGE_KEYS.layoutTree, this.layoutTree);
        this._refreshPanelUI();
        if (this.connected) { this.loadSpaces(); this._loadDefaultTree(); }
    }

    // ─── Layout Tree ──────────────────────────────────────────────────────────
    // The layout is a recursive tree of nodes:
    //   { type: 'split', direction: 'horizontal'|'vertical', size, children: [...] }
    //   { type: 'panel', panelId: string|null, size }  (null panelId = empty slot)

    _renderPanels() {
        const container = document.getElementById('panelContainer');
        if (!container) return;
        container.innerHTML = this._renderLayoutNode(this.layoutTree);
        this._initResizeHandlers();
        this._initPanelDragDrop();
        this._initSplitButtons();
    }

    _renderLayoutNode(node, depth = 0) {
        if (!node) return '';
        if (node.type === 'panel') return this._renderPanelNode(node);
        if (node.type === 'split') return this._renderSplitNode(node, depth);
        return '';
    }

    _renderPanelNode(node) {
        const { panelId } = node;
        if (!panelId || !this.panelRegistry[panelId]) {
            const closeBtn = this.panelsLocked ? '' : `
                <button class="btn btn-sm btn-link text-muted empty-panel-close"
                        style="position:absolute;top:10px;right:10px;" title="remove empty slot">
                    <i class="bi bi-x-lg"></i>
                </button>`;
            return `
                <div class="empty-panel" data-panel-node="${this._encodeAttr(node)}">
                    ${closeBtn}
                    <div class="text-muted text-center py-5">
                        <i class="bi bi-plus-circle fs-1"></i>
                        <p class="mt-2">Click to add panel</p>
                    </div>
                </div>`;
        }
        return `
            <div class="panel-wrapper" data-panel="${panelId}" data-panel-node="${this._encodeAttr(node)}">
                ${this.panelRegistry[panelId].render()}
            </div>`;
    }

    _renderSplitNode(node, depth) {
        const isH       = node.direction === 'horizontal';
        const handlePx  = (node.children.length - 1) * 8;
        let html = `<div class="split-container" data-direction="${node.direction}"
                         style="display:flex;flex-direction:${isH ? 'row' : 'column'};width:100%;height:100%;">`;
        node.children.forEach((child, i) => {
            const size    = child.size ?? (100 / node.children.length);
            const adjusted = `calc(${size}% - ${handlePx / node.children.length}px)`;
            html += `<div class="split-child" style="${isH ? 'width' : 'height'}:${adjusted};flex-shrink:0;position:relative;">`;
            html += this._renderLayoutNode(child, depth + 1);
            html += `</div>`;
            if (i < node.children.length - 1) {
                html += `<div class="${isH ? 'resize-handle' : 'resize-handle-v'}"
                              data-split-index="${i}" data-direction="${node.direction}"></div>`;
            }
        });
        html += `</div>`;
        return html;
    }

    // Generic layout-tree walker.
    // Calls visitor(node) on every node top-down. If visitor returns a non-undefined
    // value, that value replaces the node and the subtree is NOT recursed into further.
    // If visitor returns undefined, the node is kept and its children are recursed.
    _walkTree(node, visitor) {
        const replacement = visitor(node);
        if (replacement !== undefined) return replacement;
        if (node.type === 'split') {
            for (let i = 0; i < node.children.length; i++) {
                node.children[i] = this._walkTree(node.children[i], visitor) ?? node.children[i];
            }
        }
        return node;
    }

    splitPanel(panelId, direction) {
        let found = false;
        this.layoutTree = this._walkTree(this.layoutTree, node => {
            if (found || node.type !== 'panel' || node.panelId !== panelId) return undefined;
            found = true;
            return { type: 'split', direction, children: [
                { type: 'panel', panelId, size: 50 },
                { type: 'panel', panelId: null, size: 50 },
            ]};
        });
        if (found) { this._saveState(STORAGE_KEYS.layoutTree, this.layoutTree); this._refreshPanelUI(); }
    }

    removePanelFromLayout(panelId) {
        let found = false;
        this.layoutTree = this._walkTree(this.layoutTree, node => {
            if (found || node.type !== 'panel' || node.panelId !== panelId) return undefined;
            found = true;
            return { type: 'panel', panelId: null, size: node.size ?? 100 };
        });
        if (found) { this._collapseSplits(); this._saveState(STORAGE_KEYS.layoutTree, this.layoutTree); this._refreshPanelUI(); }
    }

    // Bottom-up pass: collapse splits where all children are empty, or only one child remains.
    _collapseSplits() {
        const collapse = node => {
            if (node.type !== 'split') return node;
            node.children = node.children.map(collapse);
            if (node.children.every(c => c.type === 'panel' && !c.panelId)) {
                return { type: 'panel', panelId: null, size: node.size ?? 100 };
            }
            if (node.children.length === 1) {
                const child = node.children[0];
                child.size = node.size ?? 100;
                return child;
            }
            return node;
        };
        this.layoutTree = collapse(this.layoutTree);
    }

    _addPanelToEmpty(panelId) {
        let placed = false;
        this.layoutTree = this._walkTree(this.layoutTree, node => {
            if (placed || node.type !== 'panel' || node.panelId) return undefined;
            placed = true;
            return { type: 'panel', panelId, size: node.size ?? 100 };
        });
        if (placed) { this._saveState(STORAGE_KEYS.layoutTree, this.layoutTree); this._refreshPanelUI(); }
    }

    _removeEmptyPanel() {
        let removed = false;
        const remove = node => {
            if (node.type !== 'split') return node;
            for (let i = 0; i < node.children.length; i++) {
                if (node.children[i].type === 'panel' && !node.children[i].panelId) {
                    node.children.splice(i, 1);
                    removed = true;
                    break;
                }
                node.children[i] = remove(node.children[i]);
                if (removed) break;
            }
            // Collapse or redistribute after removal
            if (removed) {
                if (node.children.length === 1) { node.children[0].size = node.size ?? 100; return node.children[0]; }
                const each = 100 / node.children.length;
                node.children.forEach(c => c.size = each);
            }
            return node;
        };
        this.layoutTree = remove(this.layoutTree);
        if (removed) { this._saveState(STORAGE_KEYS.layoutTree, this.layoutTree); this._refreshPanelUI(); }
    }

    swapPanels(id1, id2) {
        const i = this.panelOrder.indexOf(id1);
        const j = this.panelOrder.indexOf(id2);
        if (i < 0 || j < 0) return;
        [this.panelOrder[i], this.panelOrder[j]] = [this.panelOrder[j], this.panelOrder[i]];
        this._saveState(STORAGE_KEYS.panelOrder, this.panelOrder);
        this._renderPanels();
        this._initElements();
        this._initPanelEventListeners();
    }

    showLayoutTemplates() {
        const modal = document.createElement('div');
        modal.className = 'modal fade';
        modal.innerHTML = `
            <div class="modal-dialog modal-lg">
                <div class="modal-content bg-dark text-light">
                    <div class="modal-header border-secondary">
                        <h5 class="modal-title"><i class="bi bi-grid-3x3-gap me-2"></i>Layout Templates</h5>
                        <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <div class="row g-3">
                            ${Object.entries(LAYOUT_TEMPLATES).map(([id, t]) => `
                                <div class="col-md-6">
                                    <div class="card bg-secondary border-secondary h-100 layout-template-card"
                                         data-template-id="${id}" style="cursor:pointer;">
                                        <div class="card-body">
                                            <h6 class="card-title text-primary">${t.name}</h6>
                                            <p class="card-text small text-muted">${t.description}</p>
                                        </div>
                                    </div>
                                </div>`).join('')}
                        </div>
                    </div>
                    <div class="modal-footer border-secondary">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                    </div>
                </div>
            </div>`;
        document.body.appendChild(modal);
        modal.querySelectorAll('.layout-template-card').forEach(card => {
            card.addEventListener('click', () => {
                this.layoutTree = LAYOUT_TEMPLATES[card.dataset.templateId].layout;
                this._saveState(STORAGE_KEYS.layoutTree, this.layoutTree);
                this._refreshPanelUI();
                bootstrap.Modal.getInstance(modal).hide();
            });
        });
        const bsModal = new bootstrap.Modal(modal);
        bsModal.show();
        modal.addEventListener('hidden.bs.modal', () => modal.remove());
    }

    // ─── Panel Renderers ──────────────────────────────────────────────────────

    // Shared header used by every panel. Pass extraControls HTML for panel-specific buttons.
    _renderPanelHeader(panel, extraControls = '') {
        const splitControls = this.panelsLocked ? '' : `
            <div class="btn-group me-2" role="group">
                <button class="btn btn-sm btn-link text-muted px-2 py-1 split-h-btn" title="split horizontally">
                    <i class="bi bi-layout-split"></i></button>
                <button class="btn btn-sm btn-link text-muted px-2 py-1 split-v-btn" title="split vertically">
                    <i class="bi bi-layout-three-columns"></i></button>
            </div>`;
        const closeBtn = this.panelsLocked
            ? `<span class="btn btn-sm btn-link text-secondary p-0 ms-2 opacity-25" title="panels locked"><i class="bi bi-lock"></i></span>`
            : `<button class="btn btn-sm btn-link text-muted p-0 ms-2 panel-close-btn" title="close panel"><i class="bi bi-x-lg"></i></button>`;
        return `
            <div class="card-header d-flex justify-content-between align-items-center">
                <span class="d-flex align-items-center">
                    <span class="panel-drag-handle me-2" title="drag to reorder"><i class="bi bi-grip-vertical"></i></span>
                    <i class="bi ${panel.icon} me-2"></i>${panel.title}
                </span>
                <div class="d-flex align-items-center">${extraControls}${splitControls}${closeBtn}</div>
            </div>`;
    }

    renderSpacesPanel() {
        return `
            <div class="card h-100">
                ${this._renderPanelHeader(this.panelRegistry.spaces, `
                    <button id="refreshSpacesBtn" class="btn btn-sm btn-outline-primary" title="refresh">
                        <i class="bi bi-arrow-clockwise"></i></button>`)}
                <div class="card-body p-0 overflow-auto">
                    <div id="spacesContainer" class="list-group list-group-flush">
                        ${this._placeholderHtml('bi-wifi-off', 'connect to view spaces')}
                    </div>
                </div>
            </div>`;
    }

    renderTreePanel() {
        return `
            <div class="card h-100">
                ${this._renderPanelHeader(this.panelRegistry.tree, `
                    <div class="input-group input-group-sm" style="width:180px;">
                        <input type="text" id="treePathInput"
                               class="form-control form-control-sm bg-dark border-secondary text-light"
                               placeholder="uri path..." value="">
                        <button id="browsePathBtn" class="btn btn-sm btn-outline-primary" title="browse">
                            <i class="bi bi-folder2-open"></i></button>
                    </div>`)}
                <div class="card-body p-2 overflow-auto">
                    <div id="treeContainer" class="tree-view">
                        ${this._placeholderHtml('bi-diagram-3', 'select a space to browse')}
                    </div>
                </div>
            </div>`;
    }

    renderInspectorPanel() {
        return `
            <div class="card h-100 d-flex flex-column">
                ${this._renderPanelHeader(this.panelRegistry.inspector, `
                    <button id="inspectorDocqBtn" class="btn btn-sm btn-outline-info me-2"
                            title="fetch type documentation for this obj">
                        <i class="bi bi-book me-1"></i>docq</button>
                    <span id="inspectorUri" class="text-muted small me-2" style="font-family:monospace;"></span>`)}
                <div class="card-body p-2 overflow-auto flex-grow-1">
                    <div id="inspectorContainer" class="inspector-output">
                        ${this._placeholderHtml('bi-crosshair', 'click a tree node or space to inspect')}
                    </div>
                </div>
            </div>`;
    }

    renderConsolePanel() {
        return `
            <div class="card h-100 d-flex flex-column">
                ${this._renderPanelHeader(this.panelRegistry.console, `
                    <button id="clearOutputBtn" class="btn btn-sm btn-outline-secondary me-1" title="clear output">
                        <i class="bi bi-trash"></i></button>
                    <button id="executeBtn" class="btn btn-sm btn-primary" title="execute (ctrl+enter)">
                        <i class="bi bi-play-fill me-1" style="color:white;"></i>Run</button>`)}
                <div class="card-body p-0 d-flex flex-column flex-grow-1">
                    <div class="p-2 border-bottom border-secondary">
                        <textarea id="codeInput" class="form-control code-input" rows="3"
                                  placeholder="enter mtron code here… (ctrl+enter to execute)">1-&lt;[_,_]</textarea>
                    </div>
                    <div class="flex-grow-1 overflow-auto p-2" style="min-height:100px;">
                        <div id="outputContainer" class="output-container">
                            <div class="text-muted small"><i class="bi bi-info-circle me-1"></i>output will appear here</div>
                        </div>
                    </div>
                </div>
            </div>`;
    }

    renderLlmAgentPanel() {
        // Small helper to render a labeled text input row
        const field = (id, label, attrs = '') => `
            <div class="mb-3">
                <label class="form-label small text-muted">${label}</label>
                <input type="text" id="${id}"
                       class="form-control form-control-sm bg-dark border-secondary text-light font-monospace"
                       ${attrs}>
            </div>`;

        return `
            <div class="card h-100 d-flex flex-column">
                ${this._renderPanelHeader(this.panelRegistry.llmAgent,
                    `<button id="loadModelsBtn" class="btn btn-sm btn-outline-primary" title="refresh providers">
                         <i class="bi bi-arrow-clockwise"></i></button>`)}
                <div class="card-body overflow-auto">
                    ${field('agentUri', 'agent uri', 'placeholder="agent"')}
                    <div class="row mb-3">
                        <div class="col-5">
                            <label class="form-label small text-muted">provider</label>
                            <select id="agentProvider" class="form-select form-select-sm bg-dark border-secondary text-light">
                                <option value="">select provider</option>
                            </select>
                        </div>
                        <div class="col-7">
                            <label class="form-label small text-muted">model</label>
                            <select id="agentModel" class="form-select form-select-sm bg-dark border-secondary text-light">
                                <option value="">select model</option>
                            </select>
                        </div>
                    </div>
                    ${field('agentDesc',       'description <span class="text-muted">(optional)</span>', 'placeholder="A helpful coding assistant…"')}
                    ${field('agentThink',      'think <span class="text-muted">(thinking callback)</span>', 'placeholder="print(_)" value="print(_)"')}
                    ${this._agentItemsSectionHtml('skills', 'skills', 'skill.md files')}
                    ${this._agentItemsSectionHtml('tools',  'tools',  'inst wrappers')}
                    ${field('agentResponseTo', 'response.to <span class="text-muted">(output callback)</span>', 'placeholder="print(_)" value="print(_)"')}
                    ${field('agentMemory',     'memory <span class="text-muted">(optional uri)</span>', 'placeholder="/usr/ai/memory" value="/usr/ai/memory"')}
                    <div class="mb-3">
                        <div class="form-check">
                            <input class="form-check-input" type="checkbox" id="agentCompress" checked>
                            <label class="form-check-label small text-muted" for="agentCompress">
                                compress references <code class="text-warning">^*</code>
                                <span class="text-muted">— store linked objects as uri refs</span>
                            </label>
                        </div>
                        <div class="form-check">
                            <input class="form-check-input" type="checkbox" id="agentInitMemory" checked>
                            <label class="form-check-label small text-muted" for="agentInitMemory">
                                initialize memory <code class="text-info">[,]@uri</code>
                                <span class="text-muted">— create memory if not exists</span>
                            </label>
                        </div>
                    </div>
                    <div class="mb-3">
                        <label class="form-label small text-muted d-flex justify-content-between">
                            <span>generated mtron</span>
                            <button class="btn btn-link btn-sm p-0 text-muted" onclick="mwebConsole.copyAgentCode()">
                                <i class="bi bi-clipboard"></i> copy</button>
                        </label>
                        <pre id="agentCodePreview"
                             class="bg-black text-success p-2 rounded small font-monospace mb-0"
                             style="max-height:120px;overflow:auto;font-size:0.75rem;"></pre>
                    </div>
                    <div class="d-flex gap-2">
                        <button class="btn btn-sm btn-outline-secondary flex-grow-1" onclick="mwebConsole.previewAgentCode()">
                            <i class="bi bi-code me-1"></i>preview</button>
                        <button class="btn btn-sm btn-primary flex-grow-1" onclick="mwebConsole.createAgent()">
                            <i class="bi bi-robot me-1"></i>create agent</button>
                    </div>
                </div>
            </div>`;
    }

    renderConnectSpacesPanel() {
        return `
            <div class="card h-100">
                ${this._renderPanelHeader(this.panelRegistry.connectSpaces)}
                <div class="card-body">
                    <div class="mb-3">
                        <label class="form-label small">remote endpoint</label>
                        <input type="text" id="remoteEndpoint"
                               class="form-control form-control-sm bg-dark border-secondary text-light"
                               placeholder="ws://remote-host:8999">
                    </div>
                    <div class="mb-3">
                        <label class="form-label small">space pattern</label>
                        <input type="text" id="spacePattern"
                               class="form-control form-control-sm bg-dark border-secondary text-light"
                               placeholder="/shared/*">
                    </div>
                    <button id="connectSpaceBtn" class="btn btn-sm btn-primary w-100">
                        <i class="bi bi-link-45deg me-1"></i>connect space</button>
                    <hr class="border-secondary my-3">
                    <div id="connectedSpaces" class="small text-muted">no remote spaces connected</div>
                </div>
            </div>`;
    }

    renderMetricsPanel() {
        return `
            <div class="card h-100">
                ${this._renderPanelHeader(this.panelRegistry.metrics,
                    `<button id="refreshMetricsBtn" class="btn btn-sm btn-outline-primary" title="refresh">
                         <i class="bi bi-arrow-clockwise"></i></button>`)}
                <div class="card-body p-2">
                    <div id="metricsContainer" class="small">
                        ${this._placeholderHtml('bi-graph-up', 'connect to view metrics')}
                    </div>
                </div>
            </div>`;
    }

    // ─── Spaces Panel ─────────────────────────────────────────────────────────

    loadSpaces() {
        if (!this.connected || !this.spacesContainer) return;
        this._showLoading(this.spacesContainer, 'loading spaces…');
        // Omitting .as(rec::T) saves ~4 seconds — the response format is [[uri, config], ...]
        // which is identical to the tree response and handled by _parseSpacesResponse below.
        this.sendQuery(`"*/sys/space/+/"./m/web/inst/doc_json()`, (response, error) => {
            if (error) { this._showError(this.spacesContainer, error); return; }
            this._renderSpaces(response);
        });
    }

    _renderSpaces(response) {
        const spaces = this._parseSpacesResponse(response);
        if (spaces.length === 0) {
            this.spacesContainer.innerHTML = this._placeholderHtml('bi-inbox', 'no spaces found');
            return;
        }
        this.spacesContainer.innerHTML = spaces.map(space => {
            const name    = space.uri?.split('/').pop() || space.pattern || 'Unknown';
            const pattern = space.pattern || '';
            return `
                <div class="list-group-item" data-space='${this._encodeAttr(space)}'
                     onclick="mwebConsole.selectSpace(this)">
                    <div class="d-flex align-items-center">
                        <i class="bi ${this._spaceIcon(pattern)} space-icon"></i>
                        <div class="flex-grow-1">
                            <div class="space-name">
                                ${this.escapeHtml(name)}
                                ${this._docIndicatorHtml(space.uri)}
                            </div>
                            <div class="space-pattern">${this.escapeHtml(pattern)}</div>
                        </div>
                    </div>
                </div>`;
        }).join('');
        setTimeout(() => this._loadDocIndicators(spaces.map(s => s.uri)), 200);
        this._updateStats(`Loaded ${spaces.length} spaces`);
    }

    _parseSpacesResponse(response) {
        // Response is [[uri, config], ...] — same structure as the tree response.
        try {
            const items = this._parseJsonResponse(response);
            if (!Array.isArray(items)) return [];
            return items
                .filter(item => Array.isArray(item) && item.length >= 2)
                .map(([uri, config]) => ({
                    uri,
                    path: uri,
                    ...(config && typeof config === 'object' ? config : { value: config }),
                }));
        } catch { return []; }
    }

    _spaceIcon(pattern) {
        if (/http|web/.test(pattern))                         return 'bi-globe';
        if (/mqtt|z2m|ha:/.test(pattern))                    return 'bi-broadcast';
        if (/mariadb|db:|acme|netflix/.test(pattern))        return 'bi-database';
        if (pattern.includes('mongo'))                        return 'bi-file-earmark-code';
        if (/ollama|openai/.test(pattern))                   return 'bi-robot';
        if (/local:|fs/.test(pattern))                       return 'bi-folder';
        if (/\/h\/|g:/.test(pattern))                        return 'bi-diagram-3';
        if (/\/usr\/|\/shared\//.test(pattern))              return 'bi-archive';
        return 'bi-box';
    }

    selectSpace(element) {
        document.querySelectorAll('#spacesContainer .list-group-item').forEach(el => el.classList.remove('active'));
        element.classList.add('active');
        try {
            this.selectedSpace = JSON.parse(element.dataset.space);
            this._browseSpaceRoot();
            if (this.selectedSpace.uri) this.focusObject(this.selectedSpace.uri);
        } catch (e) { console.error('failed to parse space data:', e); }
    }

    // ─── Tree Browser ─────────────────────────────────────────────────────────

    _browseSpaceRoot() {
        if (!this.selectedSpace) return;
        let root = (this.selectedSpace.pattern ?? '').replace(/#.*$/, '');
        if (root.includes(':') && !root.startsWith('/')) root = root.replace(/:$/, '') + ':';
        this.treePathInput.value = root;
        this.treeState.clear();
        this._loadTreeNode(root, this.treeContainer, 0);
    }

    _browsePath() {
        const path = this.treePathInput?.value.trim();
        if (!path) return;
        this.treeState.clear();
        this._loadTreeNode(path, this.treeContainer, 0);
    }

    _loadDefaultTree() {
        if (!this.treePathInput || !this.treeContainer) return;
        this.treePathInput.value = '/';
        this.treeState.clear();

        const roots = [
            { uri: '/m',      label: 'm' },
            { uri: '/sys',    label: 'sys' },
            { uri: '/usr',    label: 'usr' },
            { uri: '/shared', label: 'shared' },
        ];

        this.treeContainer.innerHTML = roots.map(({ uri, label }) => `
            <div class="tree-node" data-uri="${uri}" data-depth="0">
                <span class="tree-node-icon folder"
                      onclick="mwebConsole.toggleTreeNode('${uri}', this.parentElement)">
                    <i class="bi bi-folder2"></i>
                </span>
                <span class="tree-node-label" onclick="mwebConsole.focusObject('*<${uri}>')" title="${uri}">
                    ${label}
                    <span class="tree-desc text-muted small" data-uri="${uri}"></span>
                </span>
                ${this._docIndicatorHtml(uri)}
            </div>
            <div class="tree-children" id="tree-${this._hash(uri)}" style="display:none;"></div>`
        ).join('');

        // Load descriptions for the root nodes as background queries
        roots.forEach(({ uri }) => {
            this._sendBackgroundQuery(`"*<${uri}?docq>.>>desc"./m/web/inst/doc_json()`, (response, error) => {
                if (error || response.includes('fail::')) return;
                const desc   = this._stripResponse(response).replace(/^"|"$/g, '');
                const isEmpty = !desc || desc === 'noobj' || desc === 'no documentation available';
                if (isEmpty) return;
                const descEl = document.querySelector(`.tree-desc[data-uri="${uri}"]`);
                const indEl  = document.querySelector(`.doc-indicator[data-doc-uri="${uri}"]`);
                if (descEl) descEl.textContent = `(${desc})`;
                if (indEl)  indEl.style.display = 'inline';
            });
        });
    }

    _loadTreeNode(path, container, depth) {
        if (!this.connected) return;
        this._showLoading(container, 'loading…');
        const base  = path.replace(/\/+$/, '').replace(/#$/, '');
        const query = base.endsWith(':') ? `*<${base}+/>` : `*<${base}/+/>`;
        this.sendQuery(`'${query}'./m/web/inst/doc_json()`, (response, error) => {
            if (error) { container.innerHTML = `<div class="text-danger small">${this.escapeHtml(error)}</div>`; return; }
            this._renderTreeNodes(base, response, container, depth);
        });
    }

    _renderTreeNodes(parentPath, response, container, depth) {
        const nodes = this._parseTreeResponse(response);
        if (nodes.length === 0) {
            container.innerHTML = `<div class="text-muted small px-2" style="margin-left:1rem;">no extensions found</div>`;
            return;
        }
        const children = nodes
            .map(n => ({ ...n, name: this._uriName(n.uri, parentPath) }))
            .filter(c => c.name?.length > 0)
            .sort((a, b) => a.name.localeCompare(b.name));

        // Track which nodes couldn't get an icon from the value — they'll need a bg query.
        const needsIconQuery = [];

        container.innerHTML = children.map((child, i) => {
            const nodeId    = this._hash(child.uri);
            const isOpen    = this.treeState.get(child.uri);
            const safeUri   = child.uri.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
            const connector = i === children.length - 1 ? '└─' : '├─';
            const valueHtml = child.value != null
                ? `<span class="tree-value">${this.escapeHtml(this._formatValue(child.value))}</span>` : '';

            // Derive the icon from the value already in the tree response — no round-trip needed.
            // Falls back to a bg query only if the value doesn't carry enough type info.
            const icon = this._iconFromTreeValue(child.value, child.uri);
            if (!icon) needsIconQuery.push(child.uri);

            return `
                <div class="tree-node" data-uri="${this.escapeHtml(child.uri)}" data-depth="${depth}"
                     style="margin-left:${depth * 20}px;">
                    <span class="tree-connector" style="color:#6C7293;font-family:monospace;margin-right:4px;">${connector}</span>
                    <span class="tree-node-icon" data-node-id="${nodeId}"
                          onclick="mwebConsole.toggleTreeNode('${safeUri}', this.parentElement)">
                        <i class="bi ${icon ?? 'bi-folder2'}"></i>
                    </span>
                    <span class="tree-node-label"
                          onclick="mwebConsole.focusObject('*<${safeUri}>')"
                          title="${this.escapeHtml(child.uri)}">
                        ${this.escapeHtml(child.name)}
                    </span>
                    ${valueHtml}
                </div>
                <div class="tree-children" id="tree-${nodeId}" style="display:${isOpen ? 'block' : 'none'};"></div>`;
        }).join('');

        // Only fire bg queries for nodes whose type we couldn't determine locally.
        if (needsIconQuery.length > 0) this._loadNodeTypeIcons(needsIconQuery);

        // Restore any previously expanded nodes
        children.filter(c => this.treeState.get(c.uri)).forEach(child => {
            const el = document.getElementById(`tree-${this._hash(child.uri)}`);
            if (el) this._loadTreeNode(child.uri, el, depth + 1);
        });
    }

    // Shows/hides the small blue dot indicating a URI has documentation.
    _loadDocIndicators(uris) {
        uris.forEach(uri => {
            this._sendBackgroundQuery(
                `"*<${uri}?docq>.catch([desc=>'no documentation available'])>>desc)"./m/web/inst/doc_json()`,
                (response, error) => {
                    if (error) return;
                    const desc = this._stripResponse(response).replace(/^"|"$/g, '');
                    if (!desc || desc.includes('no documentation available') || desc === 'noobj') return;
                    const el = document.querySelector(`.doc-indicator[data-doc-uri="${CSS.escape(uri)}"]`);
                    if (el) el.style.display = 'inline';
                }
            );
        });
    }

    // Asynchronously loads the correct type icon for each tree node.
    _loadNodeTypeIcons(uris) {
        uris.forEach(uri => {
            const iconEl = document.querySelector(`[data-node-id="${this._hash(uri)}"] i`);
            if (!iconEl) return;
            this._sendBackgroundQuery(`"*<${uri}>.type().vid()"./m/web/inst/doc_json()`, (response, error) => {
                if (error) return;
                const vid = this._stripResponse(response).replace(/^"|"$/g, '');
                if (iconEl) iconEl.className = `bi ${this._typeIcon(vid)}`;
            });
        });
    }

    // Derive a type icon directly from the value already present in the tree response,
    // without firing a separate background query.
    //
    // The tree response value is a mtron type string, e.g.:
    //   "bool::T"                        → VID is /m/bool
    //   "inst::T"                        → VID is /m/inst
    //   "rec::T[?[...]]@/m/space"        → VID is /m/space  (after the @)
    //   "rec::T[?[...]]@/m/llm/model"    → VID is /m/llm/model
    //
    // Returns null if the value doesn't carry enough info (caller will queue a bg query).
    _iconFromTreeValue(value, uri) {
        if (!value) return null;

        // Complex objects (e.g. space configs) — use the URI itself for icon lookup
        if (typeof value === 'object') return this._typeIcon(uri);

        const s = String(value);

        // Extract the @vid suffix: "rec::T[...]@/m/space" → "/m/space"
        const atMatch = s.match(/@(\/[^\s\],]+)/);
        if (atMatch) return this._typeIcon(atMatch[1]);

        // Extract plain type name: "bool::T" → "bool" → look up as /m/bool
        const typeMatch = s.match(/^(\w+)::T/);
        if (typeMatch) return this._typeIcon(`/m/${typeMatch[1]}`);

        return null;
    }

    // Maps a type VID (URI string) to a Bootstrap Icons class.
    _typeIcon(vid) {
        if (!vid || vid === 'null' || vid === 'noobj') return 'bi-folder2';
        const rules = [
            [/\/llm\/|\/ai\//,          'bi-robot'],
            [/model/,                   'bi-cpu'],
            [/agent/,                   'bi-person-gear'],
            [/skill/,                   'bi-lightbulb'],
            [/\/space$|\/space\//,      'bi-box-seam'],
            [/catalog/,                 'bi-journal-bookmark'],
            [/\/lst$|\/poly$/,          'bi-list-ul'],
            [/\/rec$/,                  'bi-braces'],
            [/\/set$/,                  'bi-collection'],
            [/\/str$/,                  'bi-fonts'],
            [/\/int$|\/real$|\/num$/,   'bi-123'],
            [/\/bool$/,                 'bi-toggle-on'],
            [/\/bytes$/,                'bi-file-binary'],
            [/\/instset$/,              'bi-journal-code'],
            [/\/code$/,                 'bi-gear'],
            [/\/inst$|\/inst\//,        'bi-code-slash'],
            [/\/lambda$|\/fn/,          'bi-code-slash'],
            [/\/rel$/,                  'bi-arrow-left-right'],
            [/\/fail$/,                 'bi-x-octagon'],
            [/\/type$/,                 'bi-diagram-2'],
            [/\/uri$/,                  'bi-link-45deg'],
            [/\/http|\/web/,            'bi-globe'],
            [/\/mqtt|\/sub/,            'bi-broadcast'],
            [/\/db|\/sql/,              'bi-database'],
            [/\/file|\/fs/,             'bi-file-earmark'],
        ];
        for (const [re, icon] of rules) { if (re.test(vid)) return icon; }
        return 'bi-diamond';
    }

    toggleTreeNode(uri, nodeEl) {
        const nodeId  = this._hash(uri);
        const childEl = document.getElementById(`tree-${nodeId}`);
        const iconEl  = nodeEl.querySelector('.tree-node-icon i');
        if (!childEl) return;

        const wasOpen = this.treeState.get(uri);
        this.treeState.set(uri, !wasOpen);
        childEl.style.display = wasOpen ? 'none' : 'block';

        // Only swap folder icons — preserve type-specific icons set by _loadNodeTypeIcons
        if (iconEl.classList.contains('bi-folder2') || iconEl.classList.contains('bi-folder2-open')) {
            iconEl.className = `bi ${wasOpen ? 'bi-folder2' : 'bi-folder2-open'}`;
        }

        if (!wasOpen) {
            // Mirror the label-click behaviour — show the object in the inspector when opening
            this.focusObject(`*<${uri}>`);
            if (childEl.innerHTML.trim() === '' || childEl.querySelector('.spinner-border')) {
                this._loadTreeNode(uri, childEl, (parseInt(nodeEl.dataset.depth) || 0) + 1);
            }
        }
    }

    queryUri(uri) {
        if (this.codeInput) this.codeInput.value = uri;
        this._executeCode();
        this.focusObject(uri);
    }

    // ─── Object Inspector ─────────────────────────────────────────────────────

    focusObject(uri) {
        if (!this.connected || !this.inspectorContainer) return;
        this.currentInspectorUri = uri;
        if (this.inspectorUri) this.inspectorUri.textContent = uri;
        this._showLoading(this.inspectorContainer, 'loading…');
        this.sendQuery(`"${uri}"./m/web/inst/doc()`, (response, error) => {
            if (error) { this._showError(this.inspectorContainer, error); return; }
            this.inspectorContainer.innerHTML = this._highlight(this._stripResponse(response));
        });
    }

    loadDocumentation(uri) {
        if (!this.connected || !this.inspectorContainer) return;
        if (this.inspectorUri) {
            this.inspectorUri.innerHTML = `<i class="bi bi-book me-1"></i>${this.escapeHtml(uri)}?docq`;
        }
        this._showLoading(this.inspectorContainer, 'loading documentation…');
        this.sendQuery(`"*<${uri}?docq>.>>desc"./m/web/inst/doc_json()`, (response, error) => {
            if (error) { this._showError(this.inspectorContainer, error); return; }
            this.inspectorContainer.innerHTML = this._highlight(this._stripResponse(response));
        });
    }

    // Fetch type documentation for the currently inspected obj and display it
    // alongside the obj itself. Runs the query:
    //   <expr>.type().vid().map(<${_}?docq>).*(_)
    // which gets the obj's type VID, appends ?docq, and dereferences the doc URI.
    loadObjTypeDocumentation() {
        if (!this.connected || !this.inspectorContainer) return;
        if (!this.currentInspectorUri) {
            this._showError(this.inspectorContainer, 'no object selected — click a tree node first');
            return;
        }

        const uri  = this.currentInspectorUri;
        // Ensure we have a complete mtron dereference expression
        const expr = uri.startsWith('*<') || uri.startsWith('*') ? uri : `*<${uri}>`;

        // Render a two-section skeleton while both queries are in-flight
        this.inspectorContainer.innerHTML = `
            <div id="inspector-obj-section">
                <div class="text-center py-2 text-muted small">
                    <div class="spinner-border spinner-border-sm text-primary me-2" role="status"></div>loading obj…
                </div>
            </div>
            <hr class="border-secondary my-2">
            <div class="small text-info mb-1"><i class="bi bi-book me-1"></i>type documentation</div>
            <div id="inspector-docq-section">
                <div class="text-center py-2 text-muted small">
                    <div class="spinner-border spinner-border-sm text-info me-2" role="status"></div>fetching documentation…
                </div>
            </div>`;

        // Query 1 — the obj itself (same as focusObject)
        this.sendQuery(`"${uri}"./m/web/inst/doc()`, (response, error) => {
            const objSection = document.getElementById('inspector-obj-section');
            if (!objSection) return;
            if (error) {
                objSection.innerHTML = `<div class="text-danger small"><i class="bi bi-exclamation-triangle me-1"></i>${this.escapeHtml(error)}</div>`;
            } else {
                objSection.innerHTML = this._highlight(this._stripResponse(response));
            }
        });

        // Query 2 — docq via type VID: <expr>.type().vid().map(<${_}?docq>).*(_)
        // Note: '${_}' is literal mtron interpolation syntax, not JS template syntax.
        const docqQuery = expr + '.type().vid().map(<${_}?docq>).*(_)./m/web/inst/doc_json()';
        this.sendQuery(docqQuery, (response, error) => {
            const docqSection = document.getElementById('inspector-docq-section');
            if (!docqSection) return;

            const noDoc = `
                <div class="text-muted small">
                    <i class="bi bi-info-circle me-1"></i>no documentation available
                </div>`;

            if (error) { docqSection.innerHTML = noDoc; return; }

            const stripped = this._stripResponse(response);
            if (!stripped || stripped === 'noobj' || stripped.includes('fail::') ||
                    stripped.includes('no documentation available')) {
                docqSection.innerHTML = noDoc;
                return;
            }

            docqSection.innerHTML = this._highlight(stripped);
        });
    }

    // ─── Console ──────────────────────────────────────────────────────────────

    _executeCode() {
        const code = this.codeInput?.value?.trim();
        if (!code) return;
        if (!this.connected) { this._appendOutput(code, null, 'not connected to metatron'); return; }
        this._showExecutionWaiting(code);
        this.sendQuery(`"""${code}"""./m/web/inst/doc()`, (response, error) => {
            this._hideExecutionWaiting();
            this._appendOutput(code, this._stripResponse(response), error);
        });
    }

    _appendOutput(code, result, error) {
        if (!this.outputContainer) return;
        const ts         = new Date().toLocaleTimeString();
        const resultHtml = error
            ? `<div class="output-error">${this.escapeHtml(error)}</div>`
            : `<div class="output-result">${result ? this._highlight(result) : '<span class="text-muted">noobj</span>'}</div>`;

        const placeholder = this.outputContainer.querySelector('.text-muted');
        if (placeholder) this.outputContainer.innerHTML = '';

        this.outputContainer.insertAdjacentHTML('afterbegin', `
            <div class="output-entry">
                <div class="output-timestamp">${ts}</div>
                <div class="output-input">${this.escapeHtml(code)}</div>
                ${resultHtml}
            </div>`);
    }

    _clearOutput() {
        if (this.outputContainer) {
            this.outputContainer.innerHTML =
                '<div class="text-muted small"><i class="bi bi-info-circle me-1"></i>output cleared</div>';
        }
    }

    _showExecutionWaiting(code) {
        if (!this.outputContainer) return;
        const placeholder = this.outputContainer.querySelector('.text-muted');
        if (placeholder) this.outputContainer.innerHTML = '';
        this.outputContainer.insertAdjacentHTML('afterbegin', `
            <div class="output-entry output-waiting" id="executionWaiting">
                <div class="output-timestamp">${new Date().toLocaleTimeString()}</div>
                <div class="output-input">${this.escapeHtml(code)}</div>
                <div class="output-result">
                    <span class="text-muted"><i class="bi bi-hourglass-split me-1"></i>waiting for response…</span>
                </div>
            </div>`);
        if (this.executeBtn) {
            this.executeBtn.disabled = true;
            this.executeBtn.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>Running…';
        }
    }

    _hideExecutionWaiting() {
        document.getElementById('executionWaiting')?.remove();
        if (this.executeBtn) {
            this.executeBtn.disabled = false;
            this.executeBtn.innerHTML = '<i class="bi bi-play-fill me-1" style="color:white;"></i>Run';
        }
    }

    // ─── LLM Agent Designer ───────────────────────────────────────────────────
    // Skills and tools are stored in this.agentItems = { skills: [], tools: [] }
    // and managed with a single set of generic methods.

    // Renders the labeled section (header + container div) for skills or tools.
    _agentItemsSectionHtml(type, label, hint) {
        const capitalized = type[0].toUpperCase() + type.slice(1);
        return `
            <div class="mb-3">
                <label class="form-label small text-muted d-flex justify-content-between">
                    <span>${label} <span class="text-muted">(${hint})</span></span>
                    <button class="btn btn-link btn-sm p-0 text-primary"
                            onclick="mwebConsole.addAgentItem('${type}')">
                        <i class="bi bi-plus-circle"></i> add</button>
                </label>
                <div id="agent${capitalized}" class="border border-secondary rounded p-2">
                    <div class="text-muted small text-center py-1">no ${label} added</div>
                </div>
            </div>`;
    }

    addAgentItem(type) {
        const examples = { skills: 'local:/path/to/skill.md', tools: '!*my_inst or !*/path/to/inst' };
        const value    = prompt(`enter ${type.slice(0, -1)} (e.g. ${examples[type]}):`);
        if (value?.trim()) {
            this.agentItems[type].push(value.trim());
            this._updateAgentItemsDisplay(type);
            this.previewAgentCode();
        }
    }

    removeAgentItem(type, index) {
        if (type === 'tools' && index === 0) return; // default tool is protected
        this.agentItems[type].splice(index, 1);
        this._updateAgentItemsDisplay(type);
        this.previewAgentCode();
    }

    _updateAgentItemsDisplay(type) {
        const container = document.getElementById(`agent${type[0].toUpperCase()}${type.slice(1)}`);
        if (!container) return;
        if (this.agentItems[type].length === 0) {
            container.innerHTML = `<div class="text-muted small text-center py-1">no ${type} added</div>`;
            return;
        }
        const colorCls = type === 'skills' ? 'text-info' : 'text-warning';
        container.innerHTML = this.agentItems[type].map((item, i) => {
            const isDefault = type === 'tools' && i === 0;
            const action    = isDefault
                ? `<span class="badge bg-secondary ms-2">default</span>`
                : `<button class="btn btn-link btn-sm p-0 text-danger ms-2"
                           onclick="mwebConsole.removeAgentItem('${type}', ${i})">
                       <i class="bi bi-x-circle"></i></button>`;
            return `
                <div class="d-flex align-items-center mb-1">
                    <code class="small flex-grow-1 ${colorCls}">${this.escapeHtml(item)}</code>
                    ${action}
                </div>`;
        }).join('');
    }

    _loadAgentProviders() {
        const select = document.getElementById('agentProvider');
        if (!select) return;
        if (!this.connected) { select.innerHTML = '<option value="">connect first</option>'; return; }
        select.innerHTML = '<option value="">loading…</option>';
        this.sendQuery(`"*/sys/space/+.where(?catalog::T)"./m/web/inst/doc_json()`, (response, error) => {
            if (error) { select.innerHTML = '<option value="">error loading</option>'; return; }
            const providers = this._parseNamedItems(response);
            select.innerHTML = providers.length > 0
                ? '<option value="">select provider…</option>' + providers.map(p => `<option value="${this.escapeHtml(p)}">${this.escapeHtml(p)}</option>`).join('')
                : '<option value="">no providers found</option>';
        });
    }

    _loadAgentModels() {
        const provider = document.getElementById('agentProvider')?.value;
        const select   = document.getElementById('agentModel');
        if (!provider || !select) return;
        if (!this.connected) { select.innerHTML = '<option value="">connect first</option>'; return; }
        select.innerHTML = '<option value="">loading…</option>';
        this.sendQuery(`"*<${provider}:+/>"./m/web/inst/doc_json()`, (response, error) => {
            if (error) { select.innerHTML = '<option value="">error loading</option>'; return; }
            const models = this._parseNamedItems(response).filter(n => !n.includes('/') && !n.startsWith('_'));
            select.innerHTML = models.length > 0
                ? models.map(m => `<option value="${this.escapeHtml(m)}">${this.escapeHtml(m)}</option>`).join('')
                : '<option value="">no models found</option>';
        });
    }

    _parseNamedItems(response) {
        try {
            const parsed = this._parseJsonResponse(response);
            if (!Array.isArray(parsed)) return [];
            return parsed
                .map(item => {
                    if (Array.isArray(item) && item.length >= 2 && item[1]?.name) return item[1].name;
                    if (typeof item === 'object' && item?.name) return item.name;
                    return null;
                })
                .filter(Boolean)
                .sort((a, b) => a.localeCompare(b));
        } catch { return []; }
    }

    previewAgentCode() {
        const preview = document.getElementById('agentCodePreview');
        if (preview) preview.textContent = this._generateAgentCode()[1];
    }

    _generateAgentCode() {
        const get      = id => document.getElementById(id)?.value?.trim();
        const agentVid = get('agentUri')       || 'agent';
        const provider = get('agentProvider')  || 'openai';
        const model    = get('agentModel')     || 'gpt-4o';
        const desc     = get('agentDesc');
        const think    = get('agentThink')     || 'print(_)';
        const respTo   = get('agentResponseTo')|| 'print(_)';
        const memory   = get('agentMemory');
        const compress = document.getElementById('agentCompress')?.checked;
        const initMem  = document.getElementById('agentInitMemory')?.checked;
        const { tools, skills } = this.agentItems;

        const code = `
*<${provider}:${model}>.at(<${agentVid}>).-<[
   ${initMem && memory ? `[,]@<${memory}>,` : ';'}
   >>=[
     think    =>${think},
     ${desc          ? `desc     =>"${desc}",`                                              : ''}
     ${tools.length  ? `tool     =>[${tools.join(',')}],`                                  : ''}
     ${skills.length ? `skill    =>[${skills.map(s => `!*<${s}>.as(skill::T)`).join(',')}],` : ''}
     ${memory        ? `memory   =>!*<${memory}>,`                                         : ''}
     response =>[to=>${respTo}]
    ]
]>>1${compress ? '.>>=[_=>^*]' : ''}`;
        return [agentVid, code];
    }

    copyAgentCode() {
        navigator.clipboard.writeText(this._generateAgentCode()[1]).then(() => {
            const btn = document.querySelector('[onclick="mwebConsole.copyAgentCode()"]');
            if (btn) {
                const orig = btn.innerHTML;
                btn.innerHTML = '<i class="bi bi-check"></i> copied!';
                setTimeout(() => btn.innerHTML = orig, 1500);
            }
        });
    }

    createAgent() {
        if (!this.connected) { alert('not connected to metatron'); return; }
        const [agentVid, code] = this._generateAgentCode();
        this.sendQuery(code, (response, error) => { if (error) alert('error: ' + error); });
        this.focusObject(`*<${agentVid}>`);
    }

    // ─── DOM Initialization & Events ──────────────────────────────────────────

    _initNavElements() {
        this.wsEndpoint       = document.getElementById('wsEndpoint');
        this.connectBtn       = document.getElementById('connectBtn');
        this.connectionStatus = document.getElementById('connectionStatus');
        this.statsDisplay     = document.getElementById('statsDisplay');
        this.panelLockBtn     = document.getElementById('panelLockBtn');
        document.getElementById('layoutTemplatesBtn')?.addEventListener('click', () => this.showLayoutTemplates());
    }

    _initElements() {
        this.spacesContainer    = document.getElementById('spacesContainer');
        this.refreshSpacesBtn   = document.getElementById('refreshSpacesBtn');
        this.treeContainer      = document.getElementById('treeContainer');
        this.treePathInput      = document.getElementById('treePathInput');
        this.browsePathBtn      = document.getElementById('browsePathBtn');
        this.inspectorContainer = document.getElementById('inspectorContainer');
        this.inspectorUri       = document.getElementById('inspectorUri');
        this.inspectorDocqBtn   = document.getElementById('inspectorDocqBtn');
        this.codeInput          = document.getElementById('codeInput');
        this.outputContainer    = document.getElementById('outputContainer');
        this.executeBtn         = document.getElementById('executeBtn');
        this.clearOutputBtn     = document.getElementById('clearOutputBtn');
    }

    _initEventListeners() {
        this.connectBtn.addEventListener('click',    () => this.toggleConnection());
        this.wsEndpoint.addEventListener('keypress', e => { if (e.key === 'Enter') this.toggleConnection(); });
        this.panelLockBtn?.addEventListener('click', () => this.togglePanelLock());
        this._initPanelEventListeners();
    }

    _initPanelEventListeners() {
        // Core panels
        this.refreshSpacesBtn?.addEventListener('click',    () => this.loadSpaces());
        this.browsePathBtn?.addEventListener('click',       () => this._browsePath());
        this.treePathInput?.addEventListener('keypress',    e  => { if (e.key === 'Enter') this._browsePath(); });
        this.inspectorDocqBtn?.addEventListener('click',    () => this.loadObjTypeDocumentation());
        this.executeBtn?.addEventListener('click',          () => this._executeCode());
        this.clearOutputBtn?.addEventListener('click',      () => this._clearOutput());
        this.codeInput?.addEventListener('keydown', e => {
            if (e.ctrlKey && e.key === 'Enter') { e.preventDefault(); this._executeCode(); }
        });

        // LLM Agent Designer
        document.getElementById('loadModelsBtn')?.addEventListener('click',  () => this._loadAgentProviders());
        document.getElementById('agentProvider')?.addEventListener('change', () => this._loadAgentModels());
        ['agentUri', 'agentModel', 'agentDesc', 'agentThink', 'agentResponseTo', 'agentMemory'].forEach(id => {
            const el = document.getElementById(id);
            el?.addEventListener('input',  () => this.previewAgentCode());
            el?.addEventListener('change', () => this.previewAgentCode());
        });
        ['agentCompress', 'agentInitMemory'].forEach(id => {
            document.getElementById(id)?.addEventListener('change', () => this.previewAgentCode());
        });

        // Populate agent item displays (in case the panel just re-rendered)
        this._updateAgentItemsDisplay('tools');
        this._updateAgentItemsDisplay('skills');
    }

    _initSplitButtons() {
        document.querySelectorAll('.split-h-btn').forEach(btn => {
            btn.addEventListener('click', e => {
                e.stopPropagation();
                const id = btn.closest('[data-panel]')?.dataset.panel;
                if (id) this.splitPanel(id, 'horizontal');
            });
        });
        document.querySelectorAll('.split-v-btn').forEach(btn => {
            btn.addEventListener('click', e => {
                e.stopPropagation();
                const id = btn.closest('[data-panel]')?.dataset.panel;
                if (id) this.splitPanel(id, 'vertical');
            });
        });
        document.querySelectorAll('.panel-close-btn').forEach(btn => {
            btn.addEventListener('click', e => {
                e.stopPropagation();
                const id = btn.closest('[data-panel]')?.dataset.panel;
                if (id) this.removePanelFromLayout(id);
            });
        });
        document.querySelectorAll('.empty-panel').forEach(empty => {
            empty.addEventListener('click', e => {
                if (!e.target.closest('.empty-panel-close')) this._showPanelSelector(empty);
            });
        });
        document.querySelectorAll('.empty-panel-close').forEach(btn => {
            btn.addEventListener('click', e => { e.stopPropagation(); this._removeEmptyPanel(); });
        });
    }

    _showPanelSelector(emptyEl) {
        const rect     = emptyEl.getBoundingClientRect();
        const dropdown = document.createElement('div');
        dropdown.className = 'dropdown-menu dropdown-menu-dark show';
        Object.assign(dropdown.style, {
            position: 'fixed',
            left: `${rect.left + rect.width / 2 - 150}px`,
            top:  `${rect.top  + rect.height / 2 - 100}px`,
            width: '300px', maxHeight: '400px', overflowY: 'auto', zIndex: '9999',
        });

        const byCategory = {};
        for (const [id, p] of Object.entries(this.panelRegistry)) {
            (byCategory[p.category ?? 'other'] ??= []).push({ id, ...p });
        }

        let html = '<li><h6 class="dropdown-header">Select Panel to Add</h6></li>';
        for (const [cat, panels] of Object.entries(byCategory)) {
            html += `<li><h6 class="dropdown-header">${cat}</h6></li>`;
            for (const p of panels) {
                html += `
                    <li>
                        <a class="dropdown-item d-flex align-items-center" href="#" data-panel-id="${p.id}">
                            <i class="bi ${p.icon} me-2"></i><span>${p.title}</span>
                        </a>
                    </li>`;
            }
        }
        html += `
            <li><hr class="dropdown-divider"></li>
            <li><a class="dropdown-item text-muted small" href="#" data-cancel="true">
                    <i class="bi bi-x-circle me-2"></i>Cancel</a></li>`;
        dropdown.innerHTML = html;
        document.body.appendChild(dropdown);

        dropdown.addEventListener('click', e => {
            e.preventDefault();
            const link = e.target.closest('a');
            if (!link) return;
            if (link.dataset.cancel)   { dropdown.remove(); return; }
            if (link.dataset.panelId)  { this._addPanelToEmpty(link.dataset.panelId); dropdown.remove(); }
        });
        const closeOnOutside = e => {
            if (!dropdown.contains(e.target)) { dropdown.remove(); document.removeEventListener('click', closeOnOutside); }
        };
        setTimeout(() => document.addEventListener('click', closeOnOutside), 100);
    }

    _initPanelDragDrop() {
        document.querySelectorAll('[data-panel]').forEach(panelEl => {
            const handle = panelEl.querySelector('.panel-drag-handle');
            if (!handle) return;
            handle.setAttribute('draggable', 'true');

            handle.addEventListener('dragstart', e => {
                e.stopPropagation();
                panelEl.classList.add('dragging');
                e.dataTransfer.effectAllowed = 'move';
                e.dataTransfer.setData('text/plain', panelEl.dataset.panel);
            });
            handle.addEventListener('dragend', () => {
                panelEl.classList.remove('dragging');
                document.querySelectorAll('[data-panel]').forEach(p => p.classList.remove('drag-over'));
            });
            panelEl.addEventListener('dragover', e => {
                e.preventDefault();
                e.dataTransfer.dropEffect = 'move';
                if (document.querySelector('.dragging') !== panelEl) panelEl.classList.add('drag-over');
            });
            panelEl.addEventListener('dragleave', () => panelEl.classList.remove('drag-over'));
            panelEl.addEventListener('drop', e => {
                e.preventDefault();
                panelEl.classList.remove('drag-over');
                const dragged = e.dataTransfer.getData('text/plain');
                if (dragged !== panelEl.dataset.panel) this.swapPanels(dragged, panelEl.dataset.panel);
            });
        });
    }

    // ─── Resize System ────────────────────────────────────────────────────────

    _initResizeHandlers() {
        document.querySelectorAll('.resize-handle, .resize-handle-v').forEach(handle => {
            handle.addEventListener('mousedown', e => this._startResize(e, handle));
        });
        if (!this.resizeListenersAdded) {
            document.addEventListener('mousemove', e => this._doResize(e));
            document.addEventListener('mouseup',   () => this._stopResize());
            this.resizeListenersAdded = true;
        }
    }

    _startResize(e, handle) {
        e.preventDefault();
        const splitContainer = handle.parentElement;
        const direction      = handle.dataset.direction;
        const splitIndex     = parseInt(handle.dataset.splitIndex);
        if (!splitContainer || !direction || isNaN(splitIndex)) return;

        const children   = Array.from(splitContainer.querySelectorAll(':scope > .split-child'));
        const leftChild  = children[splitIndex];
        const rightChild = children[splitIndex + 1];
        if (!leftChild || !rightChild) return;

        const isH = direction === 'horizontal';
        handle.classList.add('dragging');
        document.body.style.cursor     = isH ? 'col-resize' : 'row-resize';
        document.body.style.userSelect = 'none';

        this.resizeState = {
            handle, splitContainer, isH,
            containerSize:  isH ? splitContainer.offsetWidth  : splitContainer.offsetHeight,
            startPos:       isH ? e.clientX : e.clientY,
            startLeftSize:  isH ? leftChild.offsetWidth  : leftChild.offsetHeight,
            startRightSize: isH ? rightChild.offsetWidth : rightChild.offsetHeight,
            leftChild, rightChild,
        };
    }

    _doResize(e) {
        if (!this.resizeState) return;
        const { leftChild, rightChild, containerSize, startPos, startLeftSize, startRightSize, isH, splitContainer } = this.resizeState;
        const delta = (isH ? e.clientX : e.clientY) - startPos;
        const min   = 100;

        let left  = startLeftSize + delta;
        let right = startRightSize - delta;
        if (left  < min) { left  = min; right = startLeftSize + startRightSize - min; }
        if (right < min) { right = min; left  = startLeftSize + startRightSize - min; }

        if (!isH) splitContainer.style.minHeight = `${left + right + 8}px`;
        const prop = isH ? 'width' : 'height';
        leftChild.style[prop]  = `${(left  / containerSize) * 100}%`;
        rightChild.style[prop] = `${(right / containerSize) * 100}%`;
    }

    _stopResize() {
        if (!this.resizeState) return;
        this.resizeState.handle.classList.remove('dragging');
        document.body.style.cursor     = '';
        document.body.style.userSelect = '';
        this._syncLayoutSizesFromDOM();
        this._saveState(STORAGE_KEYS.layoutTree, this.layoutTree);
        this.resizeState = null;
    }

    // After a drag-resize, read actual pixel sizes from the DOM and update the layout tree.
    _syncLayoutSizesFromDOM() {
        const sync = (node, el) => {
            if (node?.type !== 'split') return;
            const splitEl = el.querySelector('.split-container');
            if (!splitEl) return;
            const isH      = node.direction === 'horizontal';
            const total    = isH ? splitEl.offsetWidth : splitEl.offsetHeight;
            const children = Array.from(splitEl.querySelectorAll(':scope > .split-child'));
            children.forEach((child, i) => {
                if (!node.children[i]) return;
                node.children[i].size = ((isH ? child.offsetWidth : child.offsetHeight) / total) * 100;
                sync(node.children[i], child);
            });
        };
        sync(this.layoutTree, document.getElementById('panelContainer'));
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    // Safely escape text for insertion into HTML.
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Short hash used for DOM element IDs (e.g. tree-<hash>).
    _hash(str) {
        let h = 0;
        for (let i = 0; i < str.length; i++) { h = Math.imul(31, h) + str.charCodeAt(i) | 0; }
        return Math.abs(h).toString(36);
    }

    // Encode an object as a safe HTML attribute value.
    _encodeAttr(obj) {
        return JSON.stringify(obj).replace(/"/g, '&quot;');
    }

    // Strip the mtron type prefix and separator tokens from a raw server response.
    _stripResponse(response) {
        let s = response.replace(/^<[^>]+>::/, '').trim();
        if (s.startsWith("'") && s.endsWith("'")) s = s.slice(1, -1);
        return s.replaceAll('%%%', '\n');
    }

    // Strip and JSON-parse a server response in one step.
    _parseJsonResponse(response) {
        return JSON.parse(this._stripResponse(response));
    }

    // Parse a tree-node response into { uri, value } objects.
    _parseTreeResponse(response) {
        try {
            const items = this._parseJsonResponse(response);
            if (!Array.isArray(items)) return [];
            return items
                .filter(item => Array.isArray(item) && item.length >= 2)
                .map(([uri, value]) => ({ uri: String(uri).replace(/\/+$/, ''), value }))
                .filter(n => !n.uri.includes('::') && !n.uri.endsWith('#'));
        } catch { return []; }
    }

    // Extract the last path segment of a URI, relative to a parent path.
    _uriName(uri, parent) {
        let name = uri;
        if (parent && uri.startsWith(parent)) name = uri.slice(parent.length);
        return name.replace(/^[/:]+/, '').replace(/\/+$/, '').split('/')[0].replace(/^:+/, '');
    }

    // Syntax-highlight a mtron code string using highlight.js if available.
    _highlight(code) {
        if (typeof hljs !== 'undefined' && hljs.getLanguage('mtron')) {
            try { return `<pre class="hljs m-0">${hljs.highlight(code, { language: 'mtron' }).value}</pre>`; }
            catch { /* fall through */ }
        }
        return `<pre class="m-0">${this.escapeHtml(code)}</pre>`;
    }

    // Format a JSON value as a compact human-readable string for tree display.
    _formatValue(value) {
        if (value == null)           return '';
        if (Array.isArray(value)) {
            if (value.length === 0)  return '[]';
            const items  = value.map(v => typeof v === 'string' ? v : JSON.stringify(v));
            const joined = items.join(', ');
            return joined.length > 50 ? `[${items.length} items]` : `[${joined}]`;
        }
        if (typeof value === 'object')  return Object.keys(value).length === 0 ? '{}' : `{${Object.keys(value).length} keys}`;
        if (typeof value === 'string')  return value.length > 40 ? value.slice(0, 40) + '…' : value;
        if (typeof value === 'number')  return Number.isInteger(value) ? String(value) : value.toFixed(2);
        if (typeof value === 'boolean') return String(value);
        return String(value);
    }

    // Render a centered placeholder with an icon and message (used in empty panel states).
    _placeholderHtml(icon, text) {
        return `
            <div class="text-muted text-center small py-4">
                <i class="bi ${icon} fs-1"></i>
                <p class="mt-2">${text}</p>
            </div>`;
    }

    // Render the small blue dot shown next to URIs that have documentation.
    // Initially hidden; shown by _loadDocIndicators once doc presence is confirmed.
    _docIndicatorHtml(uri) {
        const safeAttr = this.escapeHtml(uri);
        const safeJs   = uri.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
        return `
            <span class="doc-indicator" data-doc-uri="${safeAttr}" style="display:none; cursor:pointer;"
                  title="click to view documentation"
                  onclick="event.stopPropagation(); mwebConsole.loadDocumentation('${safeJs}')">
                <i class="bi bi-circle-fill text-info" style="font-size:6px;vertical-align:middle;margin-left:4px;"></i>
            </span>`;
    }

    _showLoading(container, msg = 'loading…') {
        if (container) container.innerHTML = `
            <div class="text-center py-3">
                <div class="spinner-border spinner-border-sm text-primary" role="status"></div>
                <span class="ms-2">${msg}</span>
            </div>`;
    }

    _showError(container, msg) {
        if (container) container.innerHTML =
            `<div class="text-center text-danger py-3"><i class="bi bi-exclamation-triangle"></i> ${this.escapeHtml(msg)}</div>`;
    }

    _updateStats(message) {
        if (this.statsDisplay) this.statsDisplay.innerHTML = `<i class="bi bi-info-circle me-1"></i>${message}`;
    }
}

// ─── Bootstrap ────────────────────────────────────────────────────────────────

let mwebConsole;
document.addEventListener('DOMContentLoaded', () => { mwebConsole = new MetatronDashboard(); });
