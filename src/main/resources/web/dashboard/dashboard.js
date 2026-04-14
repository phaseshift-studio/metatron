/**
 * Metatron Dashboard
 * WebSocket-based UI for browsing and interacting with mtron spaces
 */

class MetatronDashboard {
    constructor() {
        this.socket = null;
        this.connected = false;
        this.callbackQueue = [];
        this.selectedSpace = null;
        this.treeState = new Map();
        this.agentSkills = [];
        this.agentTools = ['!*eval'];
        this.panelWidths = new Map();
        this.panelHeights = new Map(); // For vertical stacked panels
        this.resizeState = null;
        this.resizeListenersAdded = false;
        this.backgroundQueryQueue = [];
        this.backgroundQueryTimer = null;
        this.backgroundQueryDelay = 100; // ms between background queries

        this.panelRegistry = {
            spaces: {
                id: 'spaces',
                title: 'active spaces',
                icon: 'bi-collection',
                colClass: 'col-lg-3',
                defaultOpen: true,
                category: 'core',
                render: () => this.renderSpacesPanel()
            },
            tree: {
                id: 'tree',
                title: 'uri address space',
                icon: 'bi-diagram-3',
                colClass: 'col-lg-4',
                defaultOpen: true,
                category: 'core',
                render: () => this.renderTreePanel()
            },
            inspector: {
                id: 'inspector',
                title: 'obj inspector',
                icon: 'bi-search',
                colClass: 'col-lg-5',
                defaultOpen: true,
                category: 'core',
                render: () => this.renderInspectorPanel()
            },
            console: {
                id: 'console',
                title: 'mtron console',
                icon: 'bi-terminal',
                colClass: 'col-lg-5',
                defaultOpen: true,
                category: 'core',
                render: () => this.renderConsolePanel()
            },
            llmAgent: {
                id: 'llmAgent',
                title: 'llm agent designer',
                icon: 'bi-robot',
                colClass: 'col-lg-6',
                defaultOpen: false,
                category: 'tools',
                render: () => this.renderLlmAgentPanel()
            },
            connectSpaces: {
                id: 'connectSpaces',
                title: 'connect spaces',
                icon: 'bi-link-45deg',
                colClass: 'col-lg-4',
                defaultOpen: false,
                category: 'tools',
                render: () => this.renderConnectSpacesPanel()
            },
            metrics: {
                id: 'metrics',
                title: 'metrics',
                icon: 'bi-graph-up',
                colClass: 'col-lg-4',
                defaultOpen: false,
                category: 'tools',
                render: () => this.renderMetricsPanel()
            }
        };

        this.openPanels = this.loadPanelState();
        this.panelsLocked = this.loadPanelLockState();

        this.initNavElements();
        this.updatePanelLockButton();
        this.renderPanelMenu();
        this.renderPanels();
        this.initElements();
        this.initEventListeners();
    }

    // ==================== State Persistence ====================

    loadPanelState() {
        try {
            const saved = localStorage.getItem('mtron-dashboard-panels');
            if (saved) return new Set(JSON.parse(saved));
        } catch (e) { /* ignore */ }
        return new Set(Object.entries(this.panelRegistry).filter(([_, p]) => p.defaultOpen).map(([id]) => id));
    }

    savePanelState() {
        try {
            localStorage.setItem('mtron-dashboard-panels', JSON.stringify([...this.openPanels]));
        } catch (e) { /* ignore */ }
    }

    loadPanelLockState() {
        try {
            return localStorage.getItem('mtron-dashboard-locked') === 'true';
        } catch (e) {
            return false;
        }
    }

    savePanelLockState() {
        try {
            localStorage.setItem('mtron-dashboard-locked', this.panelsLocked ? 'true' : 'false');
        } catch (e) { /* ignore */ }
    }

    // ==================== Panel System ====================

    refreshPanelUI() {
        this.savePanelState();
        this.renderPanelMenu();
        this.renderPanels();
        this.initElements();
        this.initPanelEventListeners();
    }

    togglePanelLock() {
        this.panelsLocked = !this.panelsLocked;
        this.savePanelLockState();
        this.updatePanelLockButton();
        this.refreshPanelUI();
    }

    updatePanelLockButton() {
        const btn = document.getElementById('panelLockBtn');
        if (!btn) return;

        if (this.panelsLocked) {
            btn.innerHTML = '<i class="bi bi-lock-fill"></i>';
            btn.className = 'btn btn-sm btn-warning me-2';
            btn.title = 'Panels locked - click to unlock';
        } else {
            btn.innerHTML = '<i class="bi bi-unlock"></i>';
            btn.className = 'btn btn-sm btn-outline-secondary me-2';
            btn.title = 'Lock panels (prevent accidental close)';
        }
    }

    renderPanelMenu() {
        const menu = document.getElementById('panelMenu');
        if (!menu) return;

        const categories = {};
        for (const [id, panel] of Object.entries(this.panelRegistry)) {
            const cat = panel.category || 'other';
            if (!categories[cat]) categories[cat] = [];
            categories[cat].push({id, ...panel});
        }

        let html = '';
        for (const [category, panels] of Object.entries(categories)) {
            html += `<li><h6 class="dropdown-header">${category}</h6></li>`;
            for (const panel of panels) {
                const isOpen = this.openPanels.has(panel.id);
                html += `
                    <li>
                        <a class="dropdown-item d-flex align-items-center" href="#" onclick="dashboard.togglePanel('${panel.id}'); return false;">
                            <i class="bi ${panel.icon} me-2"></i>
                            <span class="flex-grow-1">${panel.title}</span>
                            <i class="bi ${isOpen ? 'bi-check-square text-success' : 'bi-square'} ms-2"></i>
                        </a>
                    </li>`;
            }
        }
        html += `<li><hr class="dropdown-divider"></li>`;
        html += `<li><a class="dropdown-item text-muted small" href="#" onclick="dashboard.resetPanels(); return false;"><i class="bi bi-arrow-counterclockwise me-2"></i>reset to defaults</a></li>`;

        menu.innerHTML = html;
    }

    togglePanel(panelId) {
        if (this.openPanels.has(panelId)) {
            this.openPanels.delete(panelId);
        } else {
            this.openPanels.add(panelId);
        }
        this.refreshPanelUI();

        if (this.connected && this.openPanels.has(panelId)) {
            if (panelId === 'spaces') this.loadSpaces();
            if (panelId === 'tree') this.loadDefaultTree();
            if (panelId === 'llmAgent') this.loadAgentProviders();
        }
    }

    closePanel(panelId) {
        this.openPanels.delete(panelId);
        this.refreshPanelUI();
    }

    resetPanels() {
        this.openPanels = new Set(Object.entries(this.panelRegistry).filter(([_, p]) => p.defaultOpen).map(([id]) => id));
        this.refreshPanelUI();
        if (this.connected) {
            this.loadSpaces();
            this.loadDefaultTree();
        }
    }

    renderPanels() {
        const container = document.getElementById('panelContainer');
        if (!container) return;

        const openList = [...this.openPanels];
        const hasInspector = openList.includes('inspector');
        const hasConsole = openList.includes('console');
        const stackedPanels = hasInspector && hasConsole;

        // Build list of panels to render
        const panelsToRender = [];
        for (const panelId of openList) {
            if (stackedPanels && (panelId === 'inspector' || panelId === 'console')) continue;
            if (this.panelRegistry[panelId]) panelsToRender.push(panelId);
        }
        if (stackedPanels) panelsToRender.push('stacked');
        else if (hasInspector && !hasConsole) panelsToRender.push('inspector');
        else if (hasConsole && !hasInspector) panelsToRender.push('console');

        // Calculate default widths if not saved
        this.loadPanelWidths();
        this.loadPanelHeights();
        const totalPanels = panelsToRender.length;
        const defaultWidth = totalPanels > 0 ? (100 / totalPanels) : 100;

        let html = '';
        panelsToRender.forEach((panelId, index) => {
            const isLast = index === panelsToRender.length - 1;
            const width = this.panelWidths.get(panelId) || defaultWidth;
            // Last panel uses min-width and flex-grow to fill remaining space
            const widthStyle = isLast ? `min-width: ${Math.max(200, width * 3)}px;` : `width: ${width}%;`;
            const lastClass = isLast ? ' last-panel' : '';

            if (panelId === 'stacked') {
                // Inspector gets a percentage, console fills the rest with flex
                const inspectorPct = this.panelHeights.get('inspector') || 40;
                html += `<div class="p-1${lastClass}" style="${widthStyle}" data-panel="stacked">
                    <div style="flex: 0 0 ${inspectorPct}%;" data-vpanel="inspector">
                        ${this.panelRegistry.inspector.render()}
                    </div>
                    <div class="resize-handle-v" data-top-panel="inspector" data-bottom-panel="console"></div>
                    <div style="flex: 1 1 auto;" data-vpanel="console">
                        ${this.panelRegistry.console.render()}
                    </div>
                </div>`;
            } else {
                const panel = this.panelRegistry[panelId];
                html += `<div class="p-1${lastClass}" style="${widthStyle}" data-panel="${panelId}">${panel.render()}</div>`;
            }

            // Add resize handle between panels (not after the last one)
            if (!isLast) {
                html += `<div class="resize-handle" data-left-panel="${panelId}" data-right-panel="${panelsToRender[index + 1]}"></div>`;
            }
        });

        container.innerHTML = html;
        this.initResizeHandlers();
        this.initPanelDragDrop();
    }

    initPanelDragDrop() {
        const panels = document.querySelectorAll('#panelContainer > [data-panel]');

        panels.forEach(panel => {
            const dragHandle = panel.querySelector('.panel-drag-handle');
            if (!dragHandle) return;

            // Make the handle itself draggable
            dragHandle.setAttribute('draggable', 'true');

            dragHandle.addEventListener('dragstart', (e) => {
                e.stopPropagation();
                panel.classList.add('dragging');
                e.dataTransfer.effectAllowed = 'move';
                e.dataTransfer.setData('text/plain', panel.dataset.panel);
            });

            dragHandle.addEventListener('dragend', (e) => {
                panel.classList.remove('dragging');
                document.querySelectorAll('[data-panel]').forEach(p => p.classList.remove('drag-over'));
            });

            panel.addEventListener('dragover', (e) => {
                e.preventDefault();
                e.dataTransfer.dropEffect = 'move';
                const dragging = document.querySelector('.dragging');
                if (dragging && dragging !== panel) {
                    panel.classList.add('drag-over');
                }
            });

            panel.addEventListener('dragleave', (e) => {
                panel.classList.remove('drag-over');
            });

            panel.addEventListener('drop', (e) => {
                e.preventDefault();
                panel.classList.remove('drag-over');

                const draggedPanelId = e.dataTransfer.getData('text/plain');
                const targetPanelId = panel.dataset.panel;

                if (draggedPanelId !== targetPanelId) {
                    this.swapPanels(draggedPanelId, targetPanelId);
                }
            });
        });
    }

    swapPanels(panelId1, panelId2) {
        // Convert Set to Array to manipulate order
        const panelOrder = [...this.openPanels];
        const idx1 = panelOrder.indexOf(panelId1);
        const idx2 = panelOrder.indexOf(panelId2);

        if (idx1 === -1 || idx2 === -1) return;

        // Swap positions
        [panelOrder[idx1], panelOrder[idx2]] = [panelOrder[idx2], panelOrder[idx1]];

        // Update openPanels Set with new order
        this.openPanels = new Set(panelOrder);

        // Re-render panels
        this.refreshPanelUI();
    }

    initResizeHandlers() {
        // Horizontal resize handles (between panels)
        const handles = document.querySelectorAll('.resize-handle');
        handles.forEach(handle => {
            handle.addEventListener('mousedown', (e) => this.startResize(e, handle));
        });

        // Vertical resize handles (between stacked panels)
        const vHandles = document.querySelectorAll('.resize-handle-v');
        vHandles.forEach(handle => {
            handle.addEventListener('mousedown', (e) => this.startResizeV(e, handle));
        });

        // Global mouse events for resize (add only once)
        if (!this.resizeListenersAdded) {
            document.addEventListener('mousemove', (e) => this.doResize(e));
            document.addEventListener('mouseup', () => this.stopResize());
            this.resizeListenersAdded = true;
        }
    }

    startResize(e, handle) {
        e.preventDefault();
        const leftPanelId = handle.dataset.leftPanel;
        const rightPanelId = handle.dataset.rightPanel;
        const leftPanel = document.querySelector(`[data-panel="${leftPanelId}"]`);
        const rightPanel = document.querySelector(`[data-panel="${rightPanelId}"]`);
        const container = document.getElementById('panelContainer');

        if (!leftPanel || !rightPanel || !container) return;

        // Check if right panel is the last panel (has flex-grow)
        const allPanels = container.querySelectorAll('[data-panel]');
        const isRightLast = allPanels[allPanels.length - 1] === rightPanel;

        handle.classList.add('dragging');
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none';

        this.resizeState = {
            type: 'horizontal',
            handle,
            leftPanelId,
            rightPanelId,
            leftPanel,
            rightPanel,
            containerWidth: container.offsetWidth,
            startX: e.clientX,
            startLeftWidth: leftPanel.offsetWidth,
            startRightWidth: rightPanel.offsetWidth,
            isRightLast
        };
    }

    startResizeV(e, handle) {
        e.preventDefault();
        const topPanelId = handle.dataset.topPanel;
        const bottomPanelId = handle.dataset.bottomPanel;
        const topPanel = document.querySelector(`[data-vpanel="${topPanelId}"]`);
        const bottomPanel = document.querySelector(`[data-vpanel="${bottomPanelId}"]`);
        const container = handle.parentElement;

        if (!topPanel || !bottomPanel || !container) return;

        handle.classList.add('dragging');
        document.body.style.cursor = 'row-resize';
        document.body.style.userSelect = 'none';

        this.resizeState = {
            type: 'vertical',
            handle,
            topPanelId,
            bottomPanelId,
            topPanel,
            bottomPanel,
            containerHeight: container.offsetHeight,
            startY: e.clientY,
            startTopHeight: topPanel.offsetHeight,
            startBottomHeight: bottomPanel.offsetHeight
        };
    }

    doResize(e) {
        if (!this.resizeState) return;

        if (this.resizeState.type === 'horizontal') {
            this.doResizeH(e);
        } else if (this.resizeState.type === 'vertical') {
            this.doResizeV(e);
        }
    }

    doResizeH(e) {
        const { leftPanel, rightPanel, containerWidth, startX, startLeftWidth, startRightWidth, leftPanelId, rightPanelId, isRightLast } = this.resizeState;
        const delta = e.clientX - startX;
        const minWidth = 200;

        let newLeftWidth = startLeftWidth + delta;
        let newRightWidth = startRightWidth - delta;

        // Enforce minimum widths
        if (newLeftWidth < minWidth) {
            newLeftWidth = minWidth;
            newRightWidth = startLeftWidth + startRightWidth - minWidth;
        }
        if (newRightWidth < minWidth) {
            newRightWidth = minWidth;
            newLeftWidth = startLeftWidth + startRightWidth - minWidth;
        }

        // Convert to percentages
        const leftPercent = (newLeftWidth / containerWidth) * 100;
        const rightPercent = (newRightWidth / containerWidth) * 100;

        // Left panel always uses percentage width
        leftPanel.style.width = `${leftPercent}%`;

        // Right panel: if it's the last panel, use min-width (flex-grow fills the rest)
        // Otherwise use percentage
        if (isRightLast) {
            rightPanel.style.minWidth = `${Math.max(minWidth, newRightWidth)}px`;
        } else {
            rightPanel.style.width = `${rightPercent}%`;
        }

        // Update saved widths
        this.panelWidths.set(leftPanelId, leftPercent);
        if (!isRightLast) {
            this.panelWidths.set(rightPanelId, rightPercent);
        }
    }

    doResizeV(e) {
        const { topPanel, bottomPanel, containerHeight, startY, startTopHeight, startBottomHeight, topPanelId } = this.resizeState;
        const delta = e.clientY - startY;
        const minHeight = 100;
        const handleHeight = 8;
        const availableHeight = containerHeight - handleHeight;

        let newTopHeight = startTopHeight + delta;
        let newBottomHeight = startBottomHeight - delta;

        // Enforce minimum heights for both panels
        if (newTopHeight < minHeight) {
            newTopHeight = minHeight;
            newBottomHeight = availableHeight - minHeight;
        }
        if (newBottomHeight < minHeight) {
            newBottomHeight = minHeight;
            newTopHeight = availableHeight - minHeight;
        }

        // Ensure heights sum to available height
        const totalHeight = newTopHeight + newBottomHeight;
        if (totalHeight !== availableHeight) {
            const ratio = availableHeight / totalHeight;
            newTopHeight *= ratio;
            newBottomHeight *= ratio;
        }

        // Convert to percentage of available height
        const topPercent = (newTopHeight / availableHeight) * 100;

        // Set top panel flex-basis, bottom auto-fills
        topPanel.style.flex = `0 0 ${topPercent}%`;

        // Save only the top panel percentage (bottom auto-fills)
        this.panelHeights.set(topPanelId, topPercent);
    }

    stopResize() {
        if (!this.resizeState) return;

        this.resizeState.handle.classList.remove('dragging');
        document.body.style.cursor = '';
        document.body.style.userSelect = '';

        if (this.resizeState.type === 'horizontal') {
            this.savePanelWidths();
        } else if (this.resizeState.type === 'vertical') {
            this.savePanelHeights();
        }
        this.resizeState = null;
    }

    loadPanelWidths() {
        try {
            const saved = localStorage.getItem('mtron_panel_widths');
            if (saved) {
                const widths = JSON.parse(saved);
                this.panelWidths = new Map(Object.entries(widths));
            }
        } catch (e) {
            console.error('failed to load panel widths:', e);
        }
    }

    savePanelWidths() {
        try {
            const widths = Object.fromEntries(this.panelWidths);
            localStorage.setItem('mtron_panel_widths', JSON.stringify(widths));
        } catch (e) {
            console.error('failed to save panel widths:', e);
        }
    }

    loadPanelHeights() {
        try {
            const saved = localStorage.getItem('mtron_panel_heights');
            if (saved) {
                const heights = JSON.parse(saved);
                this.panelHeights = new Map(Object.entries(heights));
            }
        } catch (e) {
            console.error('failed to load panel heights:', e);
        }
    }

    savePanelHeights() {
        try {
            const heights = Object.fromEntries(this.panelHeights);
            localStorage.setItem('mtron_panel_heights', JSON.stringify(heights));
        } catch (e) {
            console.error('failed to save panel heights:', e);
        }
    }

    // ==================== Panel Renderers ====================

    renderPanelHeader(panel, extraControls = '') {
        const closeBtn = this.panelsLocked
            ? `<span class="btn btn-sm btn-link text-secondary p-0 ms-2 opacity-25" title="panels locked"><i class="bi bi-lock"></i></span>`
            : `<button class="btn btn-sm btn-link text-muted p-0 ms-2" onclick="dashboard.closePanel('${panel.id}')" title="close panel"><i class="bi bi-x-lg"></i></button>`;
        return `
            <div class="card-header d-flex justify-content-between align-items-center">
                <span class="d-flex align-items-center">
                    <span class="panel-drag-handle me-2" title="drag to reorder"><i class="bi bi-grip-vertical"></i></span>
                    <i class="bi ${panel.icon} me-2"></i>${panel.title}
                </span>
                <div class="d-flex align-items-center">${extraControls}${closeBtn}</div>
            </div>`;
    }

    renderSpacesPanel() {
        const panel = this.panelRegistry.spaces;
        return `
            <div class="card h-100">
                ${this.renderPanelHeader(panel, `
                    <button id="refreshSpacesBtn" class="btn btn-sm btn-outline-primary" title="refresh"><i class="bi bi-arrow-clockwise"></i></button>
                `)}
                <div class="card-body p-0 overflow-auto" style="max-height: calc(100vh - 180px);">
                    <div id="spacesContainer" class="list-group list-group-flush">
                        <div class="text-center text-muted py-4">
                            <i class="bi bi-wifi-off fs-1"></i>
                            <p class="mt-2">connect to view spaces</p>
                        </div>
                    </div>
                </div>
            </div>`;
    }

    renderTreePanel() {
        const panel = this.panelRegistry.tree;
        return `
            <div class="card h-100">
                ${this.renderPanelHeader(panel, `
                    <div class="input-group input-group-sm" style="width: 180px;">
                        <input type="text" id="treePathInput" class="form-control form-control-sm bg-dark border-secondary text-light" placeholder="uri path..." value="">
                        <button id="browsePathBtn" class="btn btn-sm btn-outline-primary" title="browse"><i class="bi bi-folder2-open"></i></button>
                    </div>
                `)}
                <div class="card-body p-2 overflow-auto" style="max-height: calc(100vh - 180px);">
                    <div id="treeContainer" class="tree-view">
                        <div class="text-center text-muted py-4">
                            <i class="bi bi-diagram-3 fs-1"></i>
                            <p class="mt-2">select a space to browse</p>
                        </div>
                    </div>
                </div>
            </div>`;
    }

    renderInspectorPanel() {
        const panel = this.panelRegistry.inspector;
        return `
            <div class="card" style="flex: 0 0 auto; max-height: 40vh;">
                ${this.renderPanelHeader(panel, `<span id="inspectorUri" class="text-muted small me-2" style="font-family: monospace;"></span>`)}
                <div class="card-body p-2 overflow-auto">
                    <div id="inspectorContainer" class="inspector-output">
                        <div class="text-muted small text-center py-3">
                            <i class="bi bi-crosshair fs-3 d-block mb-2"></i>
                            click a tree node or space to inspect
                        </div>
                    </div>
                </div>
            </div>`;
    }

    renderConsolePanel() {
        const panel = this.panelRegistry.console;
        return `
            <div class="card flex-grow-1 d-flex flex-column">
                ${this.renderPanelHeader(panel, `
                    <button id="clearOutputBtn" class="btn btn-sm btn-outline-secondary me-1" title="clear output"><i class="bi bi-trash"></i></button>
                    <button id="executeBtn" class="btn btn-sm btn-primary" title="execute (ctrl+enter)"><i class="bi bi-play-fill me-1" style="color:white;"></i>Run</button>
                `)}
                <div class="card-body p-0 d-flex flex-column flex-grow-1">
                    <div class="p-2 border-bottom border-secondary">
                        <textarea id="codeInput" class="form-control code-input" rows="3" placeholder="enter mtron code here... (ctrl+enter to execute)">1-&lt;[_,_]</textarea>
                    </div>
                    <div class="flex-grow-1 overflow-auto p-2" style="max-height: calc(100vh - 450px);">
                        <div id="outputContainer" class="output-container">
                            <div class="text-muted small"><i class="bi bi-info-circle me-1"></i>output will appear here...</div>
                        </div>
                    </div>
                </div>
            </div>`;
    }

    renderLlmAgentPanel() {
        const panel = this.panelRegistry.llmAgent;
        return `
            <div class="card h-100 d-flex flex-column">
                ${this.renderPanelHeader(panel, `<button id="loadModelsBtn" class="btn btn-sm btn-outline-primary" title="refresh providers"><i class="bi bi-arrow-clockwise"></i></button>`)}
                <div class="card-body overflow-auto" style="max-height: calc(100vh - 180px);">
                    <div class="mb-3">
                        <label class="form-label small text-muted">agent uri</label>
                        <div class="input-group input-group-sm">
                            <span class="input-group-text bg-dark border-secondary text-muted">/usr/ai/agent/</span>
                            <input type="text" id="agentUri" class="form-control bg-dark border-secondary text-light" placeholder="my-agent">
                        </div>
                    </div>
                    <div class="row mb-3">
                        <div class="col-5">
                            <label class="form-label small text-muted">provider</label>
                            <select id="agentProvider" class="form-select form-select-sm bg-dark border-secondary text-light">
                                <option value="">select provider...</option>
                            </select>
                        </div>
                        <div class="col-7">
                            <label class="form-label small text-muted">model</label>
                            <select id="agentModel" class="form-select form-select-sm bg-dark border-secondary text-light">
                                <option value="">select model...</option>
                            </select>
                        </div>
                    </div>
                    <div class="mb-3">
                        <label class="form-label small text-muted">description <span class="text-muted">(optional)</span></label>
                        <input type="text" id="agentDesc" class="form-control form-control-sm bg-dark border-secondary text-light" placeholder="A helpful coding assistant...">
                    </div>
                    <div class="mb-3">
                        <label class="form-label small text-muted">think <span class="text-muted">(thinking callback)</span></label>
                        <input type="text" id="agentThink" class="form-control form-control-sm bg-dark border-secondary text-light font-monospace" placeholder="print(_)" value="print(_)">
                    </div>
                    <div class="mb-3">
                        <label class="form-label small text-muted d-flex justify-content-between">
                            <span>skills <span class="text-muted">(skill.md files)</span></span>
                            <button class="btn btn-link btn-sm p-0 text-primary" onclick="dashboard.addAgentSkill()"><i class="bi bi-plus-circle"></i> add</button>
                        </label>
                        <div id="agentSkills" class="border border-secondary rounded p-2">
                            <div class="text-muted small text-center py-1">no skills added</div>
                        </div>
                    </div>
                    <div class="mb-3">
                        <label class="form-label small text-muted d-flex justify-content-between">
                            <span>tools <span class="text-muted">(inst wrappers)</span></span>
                            <button class="btn btn-link btn-sm p-0 text-primary" onclick="dashboard.addAgentTool()"><i class="bi bi-plus-circle"></i> add</button>
                        </label>
                        <div id="agentTools" class="border border-secondary rounded p-2">
                            <div class="agent-tool-item d-flex align-items-center mb-1">
                                <input type="text" class="form-control form-control-sm bg-dark border-secondary text-light font-monospace flex-grow-1" value="!*eval" readonly>
                                <span class="badge bg-secondary ms-2">default</span>
                            </div>
                        </div>
                    </div>
                    <div class="mb-3">
                        <label class="form-label small text-muted">response.to <span class="text-muted">(output callback)</span></label>
                        <input type="text" id="agentResponseTo" class="form-control form-control-sm bg-dark border-secondary text-light font-monospace" placeholder="print(_)" value="print(_)">
                    </div>
                    <div class="mb-3">
                        <label class="form-label small text-muted">memory <span class="text-muted">(optional uri)</span></label>
                        <input type="text" id="agentMemory" class="form-control form-control-sm bg-dark border-secondary text-light font-monospace" placeholder="/usr/ai/memory" value="/usr/ai/memory">
                    </div>
                    <div class="mb-3">
                        <div class="form-check">
                            <input class="form-check-input" type="checkbox" id="agentCompress" checked>
                            <label class="form-check-label small text-muted" for="agentCompress">
                                compress references <code class="text-warning">^*</code> <span class="text-muted">— store linked objects as uri refs</span>
                            </label>
                        </div>
                        <div class="form-check">
                            <input class="form-check-input" type="checkbox" id="agentInitMemory" checked>
                            <label class="form-check-label small text-muted" for="agentInitMemory">
                                initialize memory <code class="text-info">[,]@uri</code> <span class="text-muted">— create memory if not exists</span>
                            </label>
                        </div>
                    </div>
                    <div class="mb-3">
                        <label class="form-label small text-muted d-flex justify-content-between">
                            <span>generated mtron</span>
                            <button class="btn btn-link btn-sm p-0 text-muted" onclick="dashboard.copyAgentCode()"><i class="bi bi-clipboard"></i> copy</button>
                        </label>
                        <pre id="agentCodePreview" class="bg-black text-success p-2 rounded small font-monospace mb-0" style="max-height: 120px; overflow: auto; font-size: 0.75rem;"></pre>
                    </div>
                    <div class="d-flex gap-2">
                        <button id="previewAgentBtn" class="btn btn-sm btn-outline-secondary flex-grow-1" onclick="dashboard.previewAgentCode()"><i class="bi bi-code me-1"></i>preview</button>
                        <button id="createAgentBtn" class="btn btn-sm btn-primary flex-grow-1" onclick="dashboard.createAgent()"><i class="bi bi-robot me-1"></i>create agent</button>
                    </div>
                </div>
            </div>`;
    }

    renderConnectSpacesPanel() {
        const panel = this.panelRegistry.connectSpaces;
        return `
            <div class="card h-100">
                ${this.renderPanelHeader(panel)}
                <div class="card-body">
                    <div class="mb-3">
                        <label class="form-label small">remote endpoint</label>
                        <input type="text" id="remoteEndpoint" class="form-control form-control-sm bg-dark border-secondary text-light" placeholder="ws://remote-host:8999">
                    </div>
                    <div class="mb-3">
                        <label class="form-label small">space pattern</label>
                        <input type="text" id="spacePattern" class="form-control form-control-sm bg-dark border-secondary text-light" placeholder="/shared/*">
                    </div>
                    <button id="connectSpaceBtn" class="btn btn-sm btn-primary w-100"><i class="bi bi-link-45deg me-1"></i>connect space</button>
                    <hr class="border-secondary my-3">
                    <div id="connectedSpaces" class="small text-muted">no remote spaces connected</div>
                </div>
            </div>`;
    }

    renderMetricsPanel() {
        const panel = this.panelRegistry.metrics;
        return `
            <div class="card h-100">
                ${this.renderPanelHeader(panel, `<button id="refreshMetricsBtn" class="btn btn-sm btn-outline-primary" title="refresh"><i class="bi bi-arrow-clockwise"></i></button>`)}
                <div class="card-body p-2">
                    <div id="metricsContainer" class="small">
                        <div class="text-muted text-center py-3">
                            <i class="bi bi-graph-up fs-3 d-block mb-2"></i>
                            connect to view metrics
                        </div>
                    </div>
                </div>
            </div>`;
    }

    // ==================== LLM Agent Designer ====================

    addAgentSkill() {
        const path = prompt('Enter skill path (e.g., local:/path/to/skill.md):');
        if (path?.trim()) {
            this.agentSkills.push(path.trim());
            this.updateAgentSkillsDisplay();
            this.previewAgentCode();
        }
    }

    removeAgentSkill(index) {
        this.agentSkills.splice(index, 1);
        this.updateAgentSkillsDisplay();
        this.previewAgentCode();
    }

    updateAgentSkillsDisplay() {
        const container = document.getElementById('agentSkills');
        if (!container) return;

        if (this.agentSkills.length === 0) {
            container.innerHTML = '<div class="text-muted small text-center py-1">no skills added</div>';
            return;
        }

        container.innerHTML = this.agentSkills.map((skill, i) => `
            <div class="agent-skill-item d-flex align-items-center mb-1">
                <code class="small flex-grow-1 text-info">${this.escapeHtml(skill)}</code>
                <button class="btn btn-link btn-sm p-0 text-danger ms-2" onclick="dashboard.removeAgentSkill(${i})"><i class="bi bi-x-circle"></i></button>
            </div>
        `).join('');
    }

    addAgentTool() {
        const tool = prompt('Enter tool reference (e.g., !*my_inst or !*/path/to/inst):');
        if (tool?.trim()) {
            this.agentTools.push(tool.trim());
            this.updateAgentToolsDisplay();
            this.previewAgentCode();
        }
    }

    removeAgentTool(index) {
        if (index === 0) return;
        this.agentTools.splice(index, 1);
        this.updateAgentToolsDisplay();
        this.previewAgentCode();
    }

    updateAgentToolsDisplay() {
        const container = document.getElementById('agentTools');
        if (!container) return;

        container.innerHTML = this.agentTools.map((tool, i) => `
            <div class="agent-tool-item d-flex align-items-center mb-1">
                <code class="small flex-grow-1 text-warning">${this.escapeHtml(tool)}</code>
                ${i === 0
                    ? '<span class="badge bg-secondary ms-2">default</span>'
                    : `<button class="btn btn-link btn-sm p-0 text-danger ms-2" onclick="dashboard.removeAgentTool(${i})"><i class="bi bi-x-circle"></i></button>`
                }
            </div>
        `).join('');
    }

    previewAgentCode() {
        const preview = document.getElementById('agentCodePreview');
        if (preview) preview.textContent = this.generateAgentCode();
    }

    generateAgentCode() {
        const uri = document.getElementById('agentUri')?.value?.trim() || 'my-agent';
        const provider = document.getElementById('agentProvider')?.value || 'openai';
        const model = document.getElementById('agentModel')?.value || 'gpt-4o';
        const desc = document.getElementById('agentDesc')?.value?.trim();
        const think = document.getElementById('agentThink')?.value?.trim() || 'print(_)';
        const responseTo = document.getElementById('agentResponseTo')?.value?.trim() || 'print(_)';
        const memory = document.getElementById('agentMemory')?.value?.trim();
        const compress = document.getElementById('agentCompress')?.checked;
        const initMemory = document.getElementById('agentInitMemory')?.checked;

        const fullUri = `/usr/ai/agent/${uri}`;
        const modelRef = `${provider}:${model}`;

        let code = '';
        if (initMemory && memory) code += `[,]@${memory};\n`;

        let config = [`think    =>${think}`];
        if (this.agentSkills.length > 0) {
            config.push(`skill    =>[${this.agentSkills.map(s => `!*(<${s}>)`).join(',')}]`);
        }
        if (this.agentTools.length > 0) {
            config.push(`tool     =>[${this.agentTools.join(',')}]`);
        }
        config.push(`response =>[to=>${responseTo}]`);
        if (memory) config.push(`memory   =>!*${memory}`);
        if (desc) config.push(`desc     =>'${desc}'`);

        const configStr = config.join(',\n                                ');
        const compressOp = compress ? '>>=[_=>^*]' : '';

        code += `*<${modelRef}>.>>=[${configStr}]${compressOp}.to(${fullUri});`;

        if (this.agentSkills.length > 0) {
            code += `\n<${modelRef}/skill> ->(*${fullUri}>>skill>>0.as(skill::T).>-[,]).to(${fullUri});`;
        }
        if (this.agentTools.length > 0) {
            code += `\n<${modelRef}/tool> ->([${this.agentTools.join(',')}]);`;
        }
        code += `\n*<${modelRef}>.to(${fullUri});`;

        return code;
    }

    copyAgentCode() {
        navigator.clipboard.writeText(this.generateAgentCode()).then(() => {
            const btn = document.querySelector('[onclick="dashboard.copyAgentCode()"]');
            if (btn) {
                const orig = btn.innerHTML;
                btn.innerHTML = '<i class="bi bi-check"></i> copied!';
                setTimeout(() => btn.innerHTML = orig, 1500);
            }
        });
    }

    createAgent() {
        if (!this.connected) {
            alert('Not connected to metatron');
            return;
        }

        const lines = this.generateAgentCode().split('\n').filter(l => l.trim());
        const executeNext = (i) => {
            if (i >= lines.length) {
                const uri = document.getElementById('agentUri')?.value?.trim() || 'my-agent';
                alert(`Agent created at /usr/ai/agent/${uri}`);
                if (this.openPanels.has('tree')) this.loadDefaultTree();
                return;
            }
            this.sendQuery(lines[i], (response, error) => {
                if (error) {
                    alert('Error: ' + error);
                    return;
                }
                executeNext(i + 1);
            });
        };
        executeNext(0);
    }

    loadAgentProviders() {
        const select = document.getElementById('agentProvider');
        if (!select) return;

        if (!this.connected) {
            select.innerHTML = '<option value="">connect first</option>';
            return;
        }

        select.innerHTML = '<option value="">loading...</option>';

        this.sendQuery(`"*/sys/space/+.where(?catalog::T)"./m/web/inst/doc_json()`, (response, error) => {
            if (error) {
                select.innerHTML = '<option value="">error loading</option>';
                return;
            }
            try {
                const providers = this.parseNamedItems(response);
                select.innerHTML = providers.length > 0
                    ? '<option value="">select provider...</option>' + providers.map(p => `<option value="${this.escapeHtml(p)}">${this.escapeHtml(p)}</option>`).join('')
                    : '<option value="">no providers found</option>';
            } catch (e) {
                select.innerHTML = '<option value="">parse error</option>';
            }
        });
    }

    loadAgentModels() {
        const provider = document.getElementById('agentProvider')?.value;
        const select = document.getElementById('agentModel');
        if (!provider || !select) return;

        if (!this.connected) {
            select.innerHTML = '<option value="">connect first</option>';
            return;
        }

        select.innerHTML = '<option value="">loading...</option>';

        this.sendQuery(`"*${provider}:+/"./m/web/inst/doc_json()`, (response, error) => {
            if (error) {
                select.innerHTML = '<option value="">error loading</option>';
                return;
            }
            try {
                const models = this.parseNamedItems(response).filter(n => !n.includes('/') && !n.startsWith('_'));
                select.innerHTML = models.length > 0
                    ? models.map(m => `<option value="${this.escapeHtml(m)}">${this.escapeHtml(m)}</option>`).join('')
                    : '<option value="">no models found</option>';
            } catch (e) {
                select.innerHTML = '<option value="">parse error</option>';
            }
        });
    }

    parseNamedItems(response) {
        const stripped = this.stripMtronResponse(response);
        try {
            const parsed = JSON.parse(stripped);
            if (!Array.isArray(parsed)) return [];
            return parsed
                .map(item => {
                    if (Array.isArray(item) && item.length >= 2 && typeof item[1] === 'object' && item[1]?.name) {
                        return item[1].name;
                    }
                    if (typeof item === 'object' && item?.name) return item.name;
                    return null;
                })
                .filter(Boolean)
                .sort((a, b) => a.localeCompare(b));
        } catch (e) {
            return [];
        }
    }

    // ==================== Element Initialization ====================

    initNavElements() {
        this.wsEndpoint = document.getElementById('wsEndpoint');
        this.connectBtn = document.getElementById('connectBtn');
        this.connectionStatus = document.getElementById('connectionStatus');
        this.statsDisplay = document.getElementById('statsDisplay');
        this.panelLockBtn = document.getElementById('panelLockBtn');
    }

    initElements() {
        this.spacesContainer = document.getElementById('spacesContainer');
        this.refreshSpacesBtn = document.getElementById('refreshSpacesBtn');
        this.treeContainer = document.getElementById('treeContainer');
        this.treePathInput = document.getElementById('treePathInput');
        this.browsePathBtn = document.getElementById('browsePathBtn');
        this.inspectorContainer = document.getElementById('inspectorContainer');
        this.inspectorUri = document.getElementById('inspectorUri');
        this.codeInput = document.getElementById('codeInput');
        this.outputContainer = document.getElementById('outputContainer');
        this.executeBtn = document.getElementById('executeBtn');
        this.clearOutputBtn = document.getElementById('clearOutputBtn');
    }

    initEventListeners() {
        this.connectBtn.addEventListener('click', () => this.toggleConnection());
        this.wsEndpoint.addEventListener('keypress', e => { if (e.key === 'Enter') this.toggleConnection(); });
        this.panelLockBtn?.addEventListener('click', () => this.togglePanelLock());
        this.initPanelEventListeners();
    }

    initPanelEventListeners() {
        this.refreshSpacesBtn?.addEventListener('click', () => this.loadSpaces());
        this.browsePathBtn?.addEventListener('click', () => this.browsePath());
        this.treePathInput?.addEventListener('keypress', e => { if (e.key === 'Enter') this.browsePath(); });

        this.executeBtn?.addEventListener('click', () => this.executeCode());
        this.clearOutputBtn?.addEventListener('click', () => this.clearOutput());
        this.codeInput?.addEventListener('keydown', e => {
            if (e.ctrlKey && e.key === 'Enter') {
                e.preventDefault();
                this.executeCode();
            }
        });

        // LLM Agent Designer
        document.getElementById('loadModelsBtn')?.addEventListener('click', () => this.loadAgentProviders());
        document.getElementById('agentProvider')?.addEventListener('change', () => this.loadAgentModels());

        const agentInputs = ['agentUri', 'agentModel', 'agentDesc', 'agentThink', 'agentResponseTo', 'agentMemory'];
        const agentCheckboxes = ['agentCompress', 'agentInitMemory'];

        agentInputs.forEach(id => {
            const el = document.getElementById(id);
            el?.addEventListener('input', () => this.previewAgentCode());
            el?.addEventListener('change', () => this.previewAgentCode());
        });
        agentCheckboxes.forEach(id => {
            document.getElementById(id)?.addEventListener('change', () => this.previewAgentCode());
        });
    }

    // ==================== UI Helpers ====================

    showLoading(container, message = 'Loading...') {
        if (container) container.innerHTML = `<div class="text-center py-3"><div class="spinner-border spinner-sm text-primary" role="status"></div><span class="ms-2">${message}</span></div>`;
    }

    showContainerError(container, message) {
        if (container) container.innerHTML = `<div class="text-center text-danger py-3"><i class="bi bi-exclamation-triangle"></i> ${this.escapeHtml(message)}</div>`;
    }

    stripMtronResponse(response) {
        let str = response.replace(/^<[^>]+>::/, '').trim();
        if (str.startsWith("'") && str.endsWith("'")) str = str.slice(1, -1);
        return str.replaceAll("%%%", "\n");
    }

    // ==================== WebSocket ====================

    toggleConnection() {
        this.connected ? this.disconnect() : this.connect();
    }

    connect() {
        const url = this.wsEndpoint.value.trim();
        if (!url) return;

        try {
            this.updateStatus('connecting');
            this.socket = new WebSocket(url);
            this.socket.binaryType = 'arraybuffer';

            this.socket.onopen = () => {
                this.connected = true;
                this.updateStatus('connected');
                this.loadSpaces();
                this.loadDefaultTree();
                if (this.openPanels.has('llmAgent')) this.loadAgentProviders();
            };

            this.socket.onmessage = (event) => this.handleMessage(event);

            this.socket.onclose = () => {
                this.connected = false;
                this.updateStatus('disconnected');
                this.socket = null;
            };

            this.socket.onerror = (error) => {
                console.error('WebSocket error:', error);
            };
        } catch (error) {
            console.error('Connection failed:', error);
        }
    }

    disconnect() {
        if (this.socket) {
            this.socket.close();
            this.socket = null;
        }
        this.connected = false;
        this.callbackQueue = [];
        this.updateStatus('disconnected');
    }

    updateStatus(status) {
        const el = this.connectionStatus;
        el.className = 'badge me-3';

        switch (status) {
            case 'connected':
                el.classList.add('bg-success');
                el.innerHTML = '<i class="bi bi-wifi me-1"></i>connected';
                this.connectBtn.innerHTML = '<i class="bi bi-x-lg"></i>';
                break;
            case 'connecting':
                el.classList.add('bg-warning');
                el.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>connecting...';
                break;
            default:
                el.classList.add('bg-danger');
                el.innerHTML = '<i class="bi bi-wifi-off me-1"></i>disconnected';
                this.connectBtn.innerHTML = '<i class="bi bi-plug"></i>';
        }
    }

    sendQuery(code, callback) {
        if (!this.connected || !this.socket) {
            if (callback) callback(null, 'not connected');
            return;
        }
        // User actions cancel pending background queries
        this.cancelBackgroundQueries();
        this.callbackQueue.push({callback, code, timestamp: Date.now()});
        this.socket.send(new TextEncoder().encode(code));
    }

    // Background queries run with lower priority and can be cancelled
    sendBackgroundQuery(code, callback) {
        if (!this.connected || !this.socket) return;
        this.backgroundQueryQueue.push({code, callback});
        this.scheduleBackgroundQuery();
    }

    scheduleBackgroundQuery() {
        if (this.backgroundQueryTimer) return; // Already scheduled
        if (this.backgroundQueryQueue.length === 0) return;

        this.backgroundQueryTimer = setTimeout(() => {
            this.backgroundQueryTimer = null;
            if (!this.connected || this.backgroundQueryQueue.length === 0) return;

            const {code, callback} = this.backgroundQueryQueue.shift();
            this.callbackQueue.push({callback, code, timestamp: Date.now(), background: true});
            this.socket.send(new TextEncoder().encode(code));

            // Schedule next background query
            if (this.backgroundQueryQueue.length > 0) {
                this.scheduleBackgroundQuery();
            }
        }, this.backgroundQueryDelay);
    }

    cancelBackgroundQueries() {
        // Clear pending background queries
        this.backgroundQueryQueue = [];
        if (this.backgroundQueryTimer) {
            clearTimeout(this.backgroundQueryTimer);
            this.backgroundQueryTimer = null;
        }
    }

    handleMessage(event) {
        const data = event.data instanceof ArrayBuffer
            ? new TextDecoder('utf-8').decode(event.data)
            : event.data;

        if (this.callbackQueue.length > 0) {
            const {callback} = this.callbackQueue.shift();
            if (callback) callback(data, null);
        }
    }

    // ==================== Spaces Panel ====================

    loadSpaces() {
        if (!this.connected || !this.spacesContainer) return;
        this.showLoading(this.spacesContainer, 'loading spaces...');

        this.sendQuery(`"*/sys/space/+/.as(rec::T)"./m/web/inst/doc_json()`, (response, error) => {
            if (error) {
                this.showContainerError(this.spacesContainer, error);
                return;
            }
            this.renderSpaces(response);
        });
    }

    renderSpaces(response) {
        const spaces = this.parseSpacesResponse(response);
        if (spaces.length === 0) {
            this.spacesContainer.innerHTML = '<div class="text-center text-muted py-3"><i class="bi bi-inbox"></i> No spaces found</div>';
            return;
        }

        this.spacesContainer.innerHTML = spaces.map(space => {
            const name = space.uri?.split('/').pop() || space.pattern || 'Unknown';
            const pattern = space.pattern || '';
            const icon = this.getSpaceIcon(pattern);
            const dataAttr = JSON.stringify(space).replace(/'/g, "&#39;");

            return `
                <div class="list-group-item" data-space='${dataAttr}' onclick="dashboard.selectSpace(this)">
                    <div class="d-flex align-items-center">
                        <i class="bi ${icon} space-icon"></i>
                        <div class="flex-grow-1">
                            <div class="space-name">
                                ${this.escapeHtml(name)}
                                <span class="doc-indicator" data-doc-uri="${this.escapeHtml(space.uri)}" style="display:none; cursor:pointer;" title="click to view documentation" onclick="event.stopPropagation(); dashboard.loadDocumentation('${space.uri.replace(/\\/g, '\\\\').replace(/'/g, "\\'")}')">
                                    <i class="bi bi-circle-fill text-info" style="font-size: 6px; vertical-align: middle; margin-left: 4px;"></i>
                                </span>
                            </div>
                            <div class="space-pattern">${this.escapeHtml(pattern)}</div>
                        </div>
                    </div>
                </div>`;
        }).join('');

        // Load doc indicators for spaces after a delay
        setTimeout(() => {
            this.loadDocIndicators(spaces.map(s => s.uri));
        }, 200);

        this.updateStats(`Loaded ${spaces.length} spaces`);
    }

    parseSpacesResponse(response) {
        try {
            const items = JSON.parse(this.stripMtronResponse(response));
            if (!Array.isArray(items)) return [];
            return items.map(item => {
                const [uri, config] = Object.entries(item)[0] || [];
                if (!uri) return null;
                return {uri, path: uri, ...(typeof config === 'object' && config !== null ? config : {value: config})};
            }).filter(Boolean);
        } catch (e) {
            return [];
        }
    }

    getSpaceIcon(pattern) {
        if (pattern.includes('http') || pattern.includes('web')) return 'bi-globe';
        if (pattern.includes('mqtt') || pattern.includes('z2m') || pattern.includes('ha:')) return 'bi-broadcast';
        if (pattern.includes('mariadb') || pattern.includes('db:') || pattern.includes('acme') || pattern.includes('netflix')) return 'bi-database';
        if (pattern.includes('mongo')) return 'bi-file-earmark-code';
        if (pattern.includes('ollama') || pattern.includes('openai')) return 'bi-robot';
        if (pattern.includes('local:') || pattern.includes('fs')) return 'bi-folder';
        if (pattern.includes('/h/') || pattern.includes('g:')) return 'bi-diagram-3';
        if (pattern.includes('/usr/') || pattern.includes('/shared/')) return 'bi-archive';
        return 'bi-box';
    }

    selectSpace(element) {
        document.querySelectorAll('#spacesContainer .list-group-item').forEach(el => el.classList.remove('active'));
        element.classList.add('active');

        try {
            this.selectedSpace = JSON.parse(element.dataset.space);
            this.browseSpaceRoot();
            if (this.selectedSpace.uri) this.focusObject(this.selectedSpace.uri);
        } catch (e) {
            console.error('Failed to parse space data:', e);
        }
    }

    // ==================== Tree Browser ====================

    browseSpaceRoot() {
        if (!this.selectedSpace) return;
        const pattern = this.selectedSpace.pattern || '';
        let rootPath = pattern.replace(/#.*$/, '');
        if (rootPath.includes(':') && !rootPath.startsWith('/')) {
            rootPath = rootPath.replace(/:$/, '') + ':';
        }
        this.treePathInput.value = rootPath;
        this.treeState.clear();
        this.loadTreeNode(rootPath, this.treeContainer, 0);
    }

    browsePath() {
        const path = this.treePathInput.value.trim();
        if (!path) return;
        this.treeState.clear();
        this.loadTreeNode(path, this.treeContainer, 0);
    }

    loadDefaultTree() {
        if (!this.treePathInput || !this.treeContainer) return;
        this.treePathInput.value = '/';
        this.treeState.clear();

        const roots = [
            {uri: '/m', label: 'm'},
            {uri: '/sys', label: 'sys'},
            {uri: '/usr', label: 'usr'},
            {uri: '/shared', label: 'shared'}
        ];

        this.treeContainer.innerHTML = roots.map(({uri, label}) => `
            <div class="tree-node" data-uri="${uri}" data-depth="0">
                <span class="tree-node-icon folder" onclick="dashboard.toggleTreeNode('${uri}', this.parentElement)">
                    <i class="bi bi-folder2"></i>
                </span>
                <span class="tree-node-label" onclick="dashboard.queryUri('${uri}')" title="${uri}">
                    ${label} <span class="tree-desc text-muted small" data-uri="${uri}"></span>
                </span>
                <span class="doc-indicator" data-doc-uri="${uri}" style="display:none; cursor:pointer;" title="click to view documentation" onclick="event.stopPropagation(); dashboard.loadDocumentation('${uri}')">
                    <i class="bi bi-circle-fill text-info" style="font-size: 6px; vertical-align: middle; margin-left: 4px;"></i>
                </span>
            </div>
            <div class="tree-children" id="tree-${this.hashCode(uri)}" style="display: none;"></div>
        `).join('');

        // Load descriptions first, then doc indicators after a delay
        this.loadRootDescriptions(roots.map(r => r.uri));
    }

    loadRootDescriptions(uris) {
        uris.forEach(uri => {
            this.sendBackgroundQuery(`"*${uri}?docq.>>desc"./m/web/inst/doc_json()`, (response, error) => {
                if (error) return;
                const desc = this.stripMtronResponse(response).replace(/^"|"$/g, '');
                const descEl = document.querySelector(`.tree-desc[data-uri="${uri}"]`);
                const docIndicator = document.querySelector(`.doc-indicator[data-doc-uri="${uri}"]`);

                if (desc && desc !== 'no documentation available' && desc !== 'null') {
                    if (descEl) descEl.textContent = `(${desc})`;
                    if (docIndicator) docIndicator.style.display = 'inline';
                }
            });
        });
    }

    loadDocIndicators(uris) {
        uris.forEach(uri => {
            this.sendBackgroundQuery(`"*${uri}?docq.>>desc"./m/web/inst/doc_json()`, (response, error) => {
                if (error) return;
                const desc = this.stripMtronResponse(response).replace(/^"|"$/g, '');
                if (desc && desc !== 'no documentation available' && desc !== 'null') {
                    const indicator = document.querySelector(`.doc-indicator[data-doc-uri="${CSS.escape(uri)}"]`);
                    if (indicator) indicator.style.display = 'inline';
                }
            });
        });
    }

    loadTreeNode(path, container, depth) {
        if (!this.connected) return;
        this.showLoading(container, 'loading...');

        const basePath = path.replace(/\/+$/, '').replace(/#$/, '');
        const innerQuery = basePath.endsWith(':') ? `*${basePath}+/` : `*${basePath}/+/`;

        this.sendQuery(`'${innerQuery}'./m/web/inst/doc_json()`, (response, error) => {
            if (error) {
                container.innerHTML = `<div class="text-danger small">${this.escapeHtml(error)}</div>`;
                return;
            }
            this.renderTreeNodes(basePath, response, container, depth);
        });
    }

    renderTreeNodes(parentPath, response, container, depth) {
        const nodes = this.parseTreeResponse(response);
        if (nodes.length === 0) {
            container.innerHTML = `<div class="text-muted small px-2">No children found</div>`;
            return;
        }

        const children = nodes
            .map(node => ({...node, name: this.extractUriName(node.uri, parentPath)}))
            .filter(c => c.name?.length > 0)
            .sort((a, b) => a.name.localeCompare(b.name));

        container.innerHTML = children.map((child, i) => {
            const nodeId = this.hashCode(child.uri);
            const isExpanded = this.treeState.get(child.uri);
            const escapedUri = child.uri.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
            const connector = i === children.length - 1 ? '└─' : '├─';
            const valueHtml = child.value != null ? `<span class="tree-value">${this.escapeHtml(this.formatTreeValue(child.value))}</span>` : '';

            return `
                <div class="tree-node" data-uri="${this.escapeHtml(child.uri)}" data-depth="${depth}" style="margin-left: ${depth * 20}px;">
                    <span class="tree-connector" style="color: #6C7293; font-family: monospace; margin-right: 4px;">${connector}</span>
                    <span class="tree-node-icon" data-node-id="${nodeId}" onclick="dashboard.toggleTreeNode('${escapedUri}', this.parentElement)">
                        <i class="bi bi-folder2"></i>
                    </span>
                    <span class="tree-node-label" onclick="dashboard.queryUri('${escapedUri}')" title="${this.escapeHtml(child.uri)}">
                        ${this.escapeHtml(child.name)}
                    </span>
                    <span class="doc-indicator" data-doc-uri="${this.escapeHtml(child.uri)}" style="display:none; cursor:pointer;" title="click to view documentation" onclick="event.stopPropagation(); dashboard.loadDocumentation('${escapedUri}')">
                        <i class="bi bi-circle-fill text-info" style="font-size: 6px; vertical-align: middle; margin-left: 4px;"></i>
                    </span>
                    ${valueHtml}
                </div>
                <div class="tree-children" id="tree-${nodeId}" style="display: ${isExpanded ? 'block' : 'none'};"></div>`;
        }).join('');

        // Load type icons first (higher priority)
        this.loadNodeTypeIcons(children.map(c => c.uri));

        // Load doc indicators after a short delay (lower priority)
        setTimeout(() => {
            this.loadDocIndicators(children.map(c => c.uri));
        }, 200);

        // Load expanded nodes
        children.filter(c => this.treeState.get(c.uri)).forEach(child => {
            const childContainer = document.getElementById(`tree-${this.hashCode(child.uri)}`);
            if (childContainer) this.loadTreeNode(child.uri, childContainer, depth + 1);
        });
    }

    loadNodeTypeIcons(uris) {
        if (!this.connected || uris.length === 0) return;

        // Query types for all URIs using background queries (interruptible)
        uris.forEach(uri => {
            const nodeId = this.hashCode(uri);
            const iconSpan = document.querySelector(`[data-node-id="${nodeId}"] i`);
            if (!iconSpan) return;

            this.sendBackgroundQuery(`"*${uri}.type().vid()"./m/web/inst/doc_json()`, (response, error) => {
                if (error) return;
                const typeVid = this.stripMtronResponse(response).replace(/^"|"$/g, '');
                const icon = this.getTypeIcon(typeVid);
                if (iconSpan) iconSpan.className = `bi ${icon}`;
            });
        });
    }

    getTypeIcon(typeVid) {
        if (!typeVid || typeVid === 'null' || typeVid === 'noobj') return 'bi-folder2';

        // LLM/AI types
        if (typeVid.includes('/llm/') || typeVid.includes('/ai/')) return 'bi-robot';
        if (typeVid.includes('model')) return 'bi-cpu';
        if (typeVid.includes('agent')) return 'bi-person-gear';
        if (typeVid.includes('skill')) return 'bi-lightbulb';

        // Space types
        if (typeVid.endsWith('/space') || typeVid.includes('/space/')) return 'bi-box-seam';
        if (typeVid.includes('catalog')) return 'bi-journal-bookmark';

        // Collection types
        if (typeVid.endsWith('/lst') || typeVid.endsWith('/poly')) return 'bi-list-ul';
        if (typeVid.endsWith('/rec')) return 'bi-braces';
        if (typeVid.endsWith('/set')) return 'bi-collection';

        // Primitive types
        if (typeVid.endsWith('/str')) return 'bi-fonts';
        if (typeVid.endsWith('/int') || typeVid.endsWith('/real') || typeVid.endsWith('/num')) return 'bi-123';
        if (typeVid.endsWith('/bool')) return 'bi-toggle-on';
        if (typeVid.endsWith('/bytes')) return 'bi-file-binary';

        // Code/function types
        if (typeVid.endsWith('/instset')) return 'bi-journal-code';
        if (typeVid.endsWith('/code')) return 'bi-gear';
        if (typeVid.endsWith('/inst') || typeVid.includes('/inst/')) return 'bi-code-slash';
        if (typeVid.endsWith('/lambda') || typeVid.includes('/fn')) return 'bi-code-slash';

        // Relation types
        if (typeVid.endsWith('/rel')) return 'bi-arrow-left-right';

        // Error types
        if (typeVid.endsWith('/fail')) return 'bi-x-octagon';

        // Meta types
        if (typeVid.endsWith('/type')) return 'bi-diagram-2';
        if (typeVid.endsWith('/uri')) return 'bi-link-45deg';

        // Web/IO types
        if (typeVid.includes('/http') || typeVid.includes('/web')) return 'bi-globe';
        if (typeVid.includes('/mqtt') || typeVid.includes('/sub')) return 'bi-broadcast';
        if (typeVid.includes('/db') || typeVid.includes('/sql')) return 'bi-database';
        if (typeVid.includes('/file') || typeVid.includes('/fs')) return 'bi-file-earmark';

        // Default - has a type but not specifically mapped
        return 'bi-diamond';
    }

    formatTreeValue(value) {
        if (value == null) return '';
        if (Array.isArray(value)) {
            if (value.length === 0) return '[]';
            const items = value.map(v => typeof v === 'string' ? v : JSON.stringify(v));
            const joined = items.join(', ');
            return joined.length > 50 ? `[${items.length} items]` : `[${joined}]`;
        }
        if (typeof value === 'object') {
            const keys = Object.keys(value);
            return keys.length === 0 ? '{}' : `{${keys.length} keys}`;
        }
        if (typeof value === 'string') return value.length > 40 ? value.substring(0, 40) + '...' : value;
        if (typeof value === 'number') return Number.isInteger(value) ? value.toString() : value.toFixed(2);
        if (typeof value === 'boolean') return value ? 'true' : 'false';
        return String(value);
    }

    parseTreeResponse(response) {
        try {
            const items = JSON.parse(this.stripMtronResponse(response));
            if (!Array.isArray(items)) return [];
            return items
                .filter(item => Array.isArray(item) && item.length >= 2)
                .map(([uri, value]) => ({uri: String(uri).replace(/\/+$/, ''), value}))
                .filter(node => !node.uri.includes('::') && !node.uri.endsWith('#'));
        } catch (e) {
            return [];
        }
    }

    extractUriName(uri, parentPath) {
        let name = uri;
        if (parentPath && uri.startsWith(parentPath)) name = uri.substring(parentPath.length);
        name = name.replace(/^[/:]+/, '').replace(/\/+$/, '');
        const parts = name.split('/');
        name = parts[0] || name;
        return name.replace(/^:+/, '');
    }

    toggleTreeNode(uri, nodeElement) {
        const nodeId = this.hashCode(uri);
        const childContainer = document.getElementById(`tree-${nodeId}`);
        const iconElement = nodeElement.querySelector('.tree-node-icon i');
        if (!childContainer) return;

        const isExpanded = this.treeState.get(uri);

        if (isExpanded) {
            this.treeState.set(uri, false);
            childContainer.style.display = 'none';
            // Only change folder icons, preserve type-based icons
            if (iconElement.classList.contains('bi-folder2-open')) {
                iconElement.className = 'bi bi-folder2';
            }
        } else {
            this.treeState.set(uri, true);
            childContainer.style.display = 'block';
            // Only change folder icons, preserve type-based icons
            if (iconElement.classList.contains('bi-folder2')) {
                iconElement.className = 'bi bi-folder2-open';
            }

            if (childContainer.innerHTML.trim() === '' || childContainer.querySelector('.spinner-border')) {
                const depth = parseInt(nodeElement.dataset.depth) || 0;
                this.loadTreeNode(uri, childContainer, depth + 1);
            }
        }
    }

    queryUri(uri) {
        if (this.codeInput) this.codeInput.value = uri;
        this.executeCode();
        this.focusObject(uri);
    }

    // ==================== Object Inspector ====================

    focusObject(uri) {
        if (!this.connected || !this.inspectorContainer) return;
        if (this.inspectorUri) this.inspectorUri.textContent = uri;
        this.showLoading(this.inspectorContainer, 'Loading...');

        this.sendQuery(`"*${uri}"./m/web/inst/doc()`, (response, error) => {
            if (error) {
                this.showContainerError(this.inspectorContainer, error);
                return;
            }
            this.inspectorContainer.innerHTML = this.highlightMtron(this.stripMtronResponse(response));
        });
    }

    loadDocumentation(uri) {
        if (!this.connected || !this.inspectorContainer) return;
        if (this.inspectorUri) this.inspectorUri.innerHTML = `<i class="bi bi-book me-1"></i>${this.escapeHtml(uri)}?docq`;
        this.showLoading(this.inspectorContainer, 'Loading documentation...');

        this.sendQuery(`"*${uri}?docq"./m/web/inst/doc()`, (response, error) => {
            if (error) {
                this.showContainerError(this.inspectorContainer, error);
                return;
            }
            this.inspectorContainer.innerHTML = this.highlightMtron(this.stripMtronResponse(response));
        });
    }

    // ==================== Console ====================

    executeCode() {
        const code = this.codeInput?.value?.trim();
        if (!code) return;

        if (!this.connected) {
            this.appendOutput(code, null, 'not connected to metatron');
            return;
        }

        this.sendQuery(`'${code}'./m/web/inst/doc()`, (response, error) => {
            this.appendOutput(code, this.stripMtronResponse(response), error);
        });
    }

    appendOutput(code, result, error) {
        if (!this.outputContainer) return;
        const timestamp = new Date().toLocaleTimeString();
        const resultHtml = error
            ? `<div class="output-error">${this.escapeHtml(error)}</div>`
            : `<div class="output-result">${result ? this.highlightMtron(result) : '<span class="text-muted">noobj</span>'}</div>`;

        const placeholder = this.outputContainer.querySelector('.text-muted');
        if (placeholder) this.outputContainer.innerHTML = '';

        this.outputContainer.insertAdjacentHTML('afterbegin', `
            <div class="output-entry">
                <div class="output-timestamp">${timestamp}</div>
                <div class="output-input">${this.escapeHtml(code)}</div>
                ${resultHtml}
            </div>`);
    }

    highlightMtron(code) {
        if (typeof hljs !== 'undefined' && hljs.getLanguage('mtron')) {
            try {
                return `<pre class="hljs m-0">${hljs.highlight(code, {language: 'mtron'}).value}</pre>`;
            } catch (e) { /* fallback */ }
        }
        return `<pre class="m-0">${this.escapeHtml(code)}</pre>`;
    }

    clearOutput() {
        if (this.outputContainer) {
            this.outputContainer.innerHTML = '<div class="text-muted small"><i class="bi bi-info-circle me-1"></i>Output cleared</div>';
        }
    }

    // ==================== Utilities ====================

    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    hashCode(str) {
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            hash = ((hash << 5) - hash) + str.charCodeAt(i);
            hash = hash & hash;
        }
        return Math.abs(hash).toString(36);
    }

    updateStats(message) {
        if (this.statsDisplay) {
            this.statsDisplay.innerHTML = `<i class="bi bi-info-circle me-1"></i>${message}`;
        }
    }
}

// Initialize
let dashboard;
document.addEventListener('DOMContentLoaded', () => {
    dashboard = new MetatronDashboard();
});
