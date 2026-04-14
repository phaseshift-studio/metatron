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
        this.renderPanels();
        this.initElements();
        this.initPanelEventListeners();
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

        let html = '';

        for (const panelId of openList) {
            if (stackedPanels && (panelId === 'inspector' || panelId === 'console')) continue;
            const panel = this.panelRegistry[panelId];
            if (panel) {
                html += `<div class="${panel.colClass}" data-panel="${panelId}">${panel.render()}</div>`;
            }
        }

        if (stackedPanels) {
            html += `
                <div class="col-lg-5 d-flex flex-column" style="gap: 8px;" data-panel="stacked">
                    ${this.panelRegistry.inspector.render()}
                    ${this.panelRegistry.console.render()}
                </div>`;
        } else {
            if (hasInspector && !hasConsole) {
                html += `<div class="${this.panelRegistry.inspector.colClass}" data-panel="inspector">${this.panelRegistry.inspector.render()}</div>`;
            }
            if (hasConsole && !hasInspector) {
                html += `<div class="${this.panelRegistry.console.colClass}" data-panel="console">${this.panelRegistry.console.render()}</div>`;
            }
        }

        container.innerHTML = html;
    }

    // ==================== Panel Renderers ====================

    renderPanelHeader(panel, extraControls = '') {
        const closeBtn = this.panelsLocked
            ? `<span class="btn btn-sm btn-link text-secondary p-0 ms-2 opacity-25" title="Panels locked"><i class="bi bi-lock"></i></span>`
            : `<button class="btn btn-sm btn-link text-muted p-0 ms-2" onclick="dashboard.closePanel('${panel.id}')" title="Close panel"><i class="bi bi-x-lg"></i></button>`;
        return `
            <div class="card-header d-flex justify-content-between align-items-center">
                <span><i class="bi ${panel.icon} me-2"></i>${panel.title}</span>
                <div class="d-flex align-items-center">${extraControls}${closeBtn}</div>
            </div>`;
    }

    renderSpacesPanel() {
        const panel = this.panelRegistry.spaces;
        return `
            <div class="card h-100">
                ${this.renderPanelHeader(panel, `
                    <button id="refreshSpacesBtn" class="btn btn-sm btn-outline-primary" title="Refresh"><i class="bi bi-arrow-clockwise"></i></button>
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
                        <button id="browsePathBtn" class="btn btn-sm btn-outline-primary" title="Browse"><i class="bi bi-folder2-open"></i></button>
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
                    <button id="clearOutputBtn" class="btn btn-sm btn-outline-secondary me-1" title="Clear Output"><i class="bi bi-trash"></i></button>
                    <button id="executeBtn" class="btn btn-sm btn-primary" title="Execute (Ctrl+Enter)"><i class="bi bi-play-fill me-1" style="color:white;"></i>Run</button>
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
                ${this.renderPanelHeader(panel, `<button id="loadModelsBtn" class="btn btn-sm btn-outline-primary" title="Refresh providers"><i class="bi bi-arrow-clockwise"></i></button>`)}
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
                ${this.renderPanelHeader(panel, `<button id="refreshMetricsBtn" class="btn btn-sm btn-outline-primary" title="Refresh"><i class="bi bi-arrow-clockwise"></i></button>`)}
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
        this.callbackQueue.push({callback, code, timestamp: Date.now()});
        this.socket.send(new TextEncoder().encode(code));
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
                            <div class="space-name">${this.escapeHtml(name)}</div>
                            <div class="space-pattern">${this.escapeHtml(pattern)}</div>
                        </div>
                    </div>
                </div>`;
        }).join('');

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
            {uri: '/m', label: 'm', desc: 'types & instructions'},
            {uri: '/sys', label: 'sys', desc: 'system'},
            {uri: '/usr', label: 'usr', desc: 'user space'},
            {uri: '/shared', label: 'shared', desc: 'shared space'}
        ];

        this.treeContainer.innerHTML = roots.map(({uri, label, desc}) => `
            <div class="tree-node" data-uri="${uri}" data-depth="0">
                <span class="tree-node-icon folder" onclick="dashboard.toggleTreeNode('${uri}', this.parentElement)">
                    <i class="bi bi-folder2"></i>
                </span>
                <span class="tree-node-label" onclick="dashboard.queryUri('${uri}')" title="${uri}">
                    ${label} <span class="text-muted small">(${desc})</span>
                </span>
            </div>
            <div class="tree-children" id="tree-${this.hashCode(uri)}" style="display: none;"></div>
        `).join('');
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
                    ${valueHtml}
                </div>
                <div class="tree-children" id="tree-${nodeId}" style="display: ${isExpanded ? 'block' : 'none'};"></div>`;
        }).join('');

        // Load expanded nodes
        children.filter(c => this.treeState.get(c.uri)).forEach(child => {
            const childContainer = document.getElementById(`tree-${this.hashCode(child.uri)}`);
            if (childContainer) this.loadTreeNode(child.uri, childContainer, depth + 1);
        });

        // Load type icons asynchronously
        this.loadNodeTypeIcons(children.map(c => c.uri));
    }

    loadNodeTypeIcons(uris) {
        if (!this.connected || uris.length === 0) return;

        // Query types for all URIs (batch queries to avoid flooding)
        uris.forEach(uri => {
            const nodeId = this.hashCode(uri);
            const iconSpan = document.querySelector(`[data-node-id="${nodeId}"] i`);
            if (!iconSpan) return;

            this.sendQuery(`"*${uri}.type().vid()"./m/web/inst/doc_json()`, (response, error) => {
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
