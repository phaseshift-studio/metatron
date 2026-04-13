/**
 * Metatron Dashboard JavaScript
 * Handles WebSocket communication and UI interactions
 */

class MetatronDashboard {
    constructor() {
        this.socket = null;
        this.connected = false;
        this.callbackQueue = []; // FIFO queue for callbacks
        this.selectedSpace = null;
        this.treeState = new Map(); // Track expanded nodes

        // Cache element references
        this.elements = {
            wsEndpoint: 'wsEndpoint',
            connectBtn: 'connectBtn',
            connectionStatus: 'connectionStatus',
            spacesContainer: 'spacesContainer',
            refreshSpacesBtn: 'refreshSpacesBtn',
            treeContainer: 'treeContainer',
            treePathInput: 'treePathInput',
            browsePathBtn: 'browsePathBtn',
            inspectorContainer: 'inspectorContainer',
            inspectorUri: 'inspectorUri',
            codeInput: 'codeInput',
            outputContainer: 'outputContainer',
            executeBtn: 'executeBtn',
            clearOutputBtn: 'clearOutputBtn',
            statsDisplay: 'statsDisplay'
        };

        this.initElements();
        this.initEventListeners();
    }

    initElements() {
        // Auto-bind all elements
        for (const [key, id] of Object.entries(this.elements)) {
            this[key] = document.getElementById(id);
        }
    }

    initEventListeners() {
        const onEnter = (el, fn) => el.addEventListener('keypress', e => e.key === 'Enter' && fn());

        this.connectBtn.addEventListener('click', () => this.toggleConnection());
        onEnter(this.wsEndpoint, () => this.toggleConnection());

        this.refreshSpacesBtn.addEventListener('click', () => this.loadSpaces());
        this.browsePathBtn.addEventListener('click', () => this.browsePath());
        onEnter(this.treePathInput, () => this.browsePath());

        this.executeBtn.addEventListener('click', () => this.executeCode());
        this.clearOutputBtn.addEventListener('click', () => this.clearOutput());
        this.codeInput.addEventListener('keydown', e => {
            if (e.ctrlKey && e.key === 'Enter') {
                e.preventDefault();
                this.executeCode();
            }
        });
    }

    // ==================== UI Helpers ====================

    showLoading(container, message = 'Loading...') {
        container.innerHTML = `
            <div class="text-center py-3">
                <div class="spinner-border spinner-sm text-primary" role="status"></div>
                <span class="ms-2">${message}</span>
            </div>`;
    }

    showError(container, message, icon = 'bi-exclamation-triangle') {
        container.innerHTML = `
            <div class="text-center text-danger py-3">
                <i class="bi ${icon}"></i> ${this.escapeHtml(message)}
            </div>`;
    }

    showEmpty(container, message, icon = 'bi-inbox') {
        container.innerHTML = `
            <div class="text-center text-muted py-3">
                <i class="bi ${icon}"></i> ${message}
            </div>`;
    }

    /**
     * Strip mtron type prefix and outer quotes from response
     * e.g., "</m/str>::'[...]'" → "[...]"
     */
    stripMtronResponse(response) {
        let str = response.replace(/^<[^>]+>::/, '').trim();
        if (str.startsWith("'") && str.endsWith("'")) {
            str = str.slice(1, -1);
        }
        str = str.replaceAll("%%%", "\n");
        return str;
    }

    // ==================== WebSocket Connection ====================

    toggleConnection() {
        if (this.connected) {
            this.disconnect();
        } else {
            this.connect();
        }
    }

    connect() {
        const url = this.wsEndpoint.value.trim();
        if (!url) {
            this.showError('Please enter a WebSocket URL');
            return;
        }

        try {
            this.updateStatus('connecting');
            this.socket = new WebSocket(url);
            this.socket.binaryType = 'arraybuffer';

            this.socket.onopen = () => {
                this.connected = true;
                this.updateStatus('connected');
                this.loadSpaces();
                this.loadDefaultTree();
            };

            this.socket.onmessage = (event) => {
                this.handleMessage(event);
            };

            this.socket.onclose = (event) => {
                this.connected = false;
                this.updateStatus('disconnected');
                this.socket = null;
            };

            this.socket.onerror = (error) => {
                console.error('WebSocket error:', error);
                this.showError('Connection error');
            };

        } catch (error) {
            this.showError('Failed to connect: ' + error.message);
        }
    }

    disconnect() {
        if (this.socket) {
            this.socket.close();
            this.socket = null;
        }
        this.connected = false;
        this.callbackQueue = []; // Clear pending callbacks
        this.updateStatus('disconnected');
    }

    updateStatus(status) {
        const statusEl = this.connectionStatus;
        statusEl.className = 'badge me-3';

        switch (status) {
            case 'connected':
                statusEl.classList.add('bg-success', 'connected');
                statusEl.innerHTML = '<i class="bi bi-wifi me-1"></i>connected';
                this.connectBtn.innerHTML = '<i class="bi bi-x-lg"></i>';
                break;
            case 'connecting':
                statusEl.classList.add('bg-warning');
                statusEl.innerHTML = '<i class="bi bi-hourglass-split me-1"></i>connecting...';
                break;
            default:
                statusEl.classList.add('bg-danger');
                statusEl.innerHTML = '<i class="bi bi-wifi-off me-1"></i>disconnected';
                this.connectBtn.innerHTML = '<i class="bi bi-plug"></i>';
        }
    }

    // ==================== Message Handling ====================

    sendQuery(code, callback) {
        if (!this.connected || !this.socket) {
            if (callback) callback(null, 'not connected');
            return;
        }

        const encoder = new TextEncoder();
        const data = encoder.encode(code);

        // Push callback to FIFO queue - responses come back in order
        this.callbackQueue.push({callback, code, timestamp: Date.now()});

        this.socket.send(data);
    }

    handleMessage(event) {
        let data;
        if (event.data instanceof ArrayBuffer) {
            const decoder = new TextDecoder('utf-8');
            data = decoder.decode(event.data);
        } else {
            data = event.data;
        }

        // Pop the first callback from the queue (FIFO)
        if (this.callbackQueue.length > 0) {
            const {callback, code} = this.callbackQueue.shift();
            if (callback) {
                callback(data, null);
            }
        }
    }

    // ==================== Spaces Panel ====================

    loadSpaces() {
        if (!this.connected) return;

        this.showLoading(this.spacesContainer, 'loading spaces...');

        const query = `"*/sys/space/+/.as(rec::T)"./m/web/inst/doc_json()`;

        this.sendQuery(query, (response, error) => {
            if (error) {
                this.showError(this.spacesContainer, error);
                return;
            }

            this.renderSpaces(response);
        });
    }

    renderSpaces(response) {
        const spaces = this.parseSpacesResponse(response);

        if (spaces.length === 0) {
            this.showEmpty(this.spacesContainer, 'No spaces found');
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
                return {
                    uri,
                    path: uri,
                    ...(typeof config === 'object' && config !== null ? config : {value: config})
                };
            }).filter(Boolean);
        } catch (e) {
            console.error('[Spaces] Parse error:', e);
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
        // Remove selection from all items
        document.querySelectorAll('#spacesContainer .list-group-item').forEach(el => {
            el.classList.remove('active');
        });

        // Select clicked item
        element.classList.add('active');

        try {
            this.selectedSpace = JSON.parse(element.dataset.space);
            this.browseSpaceRoot();
            // Focus the space object in the inspector
            if (this.selectedSpace.uri) {
                this.focusObject(this.selectedSpace.uri);
            }
        } catch (e) {
            console.error('Failed to parse space data:', e);
        }
    }

    // ==================== Tree Browser Panel ====================

    browseSpaceRoot() {
        if (!this.selectedSpace) return;

        // Get the root pattern from the space
        const pattern = this.selectedSpace.pattern || '';
        // Extract the scheme/prefix from pattern (e.g., "netflix:#" -> "netflix:")
        // For path patterns like "/h/#", keep the path part
        let rootPath = pattern.replace(/#.*$/, '');

        // If it's a scheme-based pattern (contains :), ensure it ends with :
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

        // Clean path and build query
        const basePath = path.replace(/\/+$/, '').replace(/#$/, '');
        const innerQuery = basePath.endsWith(':') ? `*${basePath}+/` : `*${basePath}/+/`;
        const query = `'${innerQuery}'./m/web/inst/doc_json()`;

        this.sendQuery(query, (response, error) => {
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

        // Sort by name
        const children = nodes
            .map(node => ({...node, name: this.extractUriName(node.uri, parentPath)}))
            .filter(c => c.name?.length > 0)
            .sort((a, b) => a.name.localeCompare(b.name));

        container.innerHTML = children.map((child, i) => {
            const nodeId = this.hashCode(child.uri);
            const isExpanded = this.treeState.get(child.uri);
            const escapedUri = child.uri.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
            const connector = i === children.length - 1 ? '└─' : '├─';
            const valueHtml = child.value != null
                ? `<span class="tree-value">${this.escapeHtml(this.formatTreeValue(child.value))}</span>`
                : '';

            return `
                <div class="tree-node" data-uri="${this.escapeHtml(child.uri)}" data-depth="${depth}" style="margin-left: ${depth * 20}px;">
                    <span class="tree-connector" style="color: #6C7293; font-family: monospace; margin-right: 4px;">${connector}</span>
                    <span class="tree-node-icon folder" onclick="dashboard.toggleTreeNode('${escapedUri}', this.parentElement)">
                        <i class="bi ${isExpanded ? 'bi-folder2-open' : 'bi-folder2'}"></i>
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
    }

    formatTreeValue(value) {
        // Format a value for compact display in the tree
        if (value === null || value === undefined) {
            return '';
        }
        if (typeof value === 'object') {
            if (Array.isArray(value)) {
                if (value.length === 0) return '[]';
                // Show array contents compactly
                const items = value.map(v => typeof v === 'string' ? v : JSON.stringify(v));
                const joined = items.join(', ');
                return joined.length > 50 ? `[${items.length} items]` : `[${joined}]`;
            } else {
                // Object - show keys or empty
                const keys = Object.keys(value);
                if (keys.length === 0) return '{}';
                return `{${keys.length} keys}`;
            }
        }
        if (typeof value === 'string') {
            // Truncate long strings
            return value.length > 40 ? value.substring(0, 40) + '...' : value;
        }
        if (typeof value === 'number') {
            // Format numbers nicely
            return Number.isInteger(value) ? value.toString() : value.toFixed(2);
        }
        if (typeof value === 'boolean') {
            return value ? 'true' : 'false';
        }
        return String(value);
    }

    parseTreeResponse(response) {
        // Response format: </m/str>::'[["uri1", value1], ["uri2", value2], ...]'
        // Returns array of {uri, value} objects
        try {
            const items = JSON.parse(this.stripMtronResponse(response));
            if (!Array.isArray(items)) return [];

            return items
                .filter(item => Array.isArray(item) && item.length >= 2)
                .map(([uri, value]) => ({
                    uri: String(uri).replace(/\/+$/, ''),
                    value
                }))
                .filter(node => !node.uri.includes('::') && !node.uri.endsWith('#'));
        } catch (e) {
            console.error('[Tree] Parse error:', e);
            return [];
        }
    }

    extractUriName(uri, parentPath) {
        // Get the last segment of the URI as the display name
        let name = uri;

        // Remove parent path prefix if present
        if (parentPath && uri.startsWith(parentPath)) {
            name = uri.substring(parentPath.length);
        }

        // Clean up leading/trailing slashes and colons
        name = name.replace(/^[/:]+/, '').replace(/\/+$/, '');

        // Get first segment only for display
        const parts = name.split('/');
        name = parts[0] || name;

        // Also clean any remaining special chars
        name = name.replace(/^:+/, '');

        return name;
    }

    toggleTreeNode(uri, nodeElement) {
        const nodeId = this.hashCode(uri);
        const childContainer = document.getElementById(`tree-${nodeId}`);
        const iconElement = nodeElement.querySelector('.tree-node-icon i');

        if (!childContainer) return;

        const isExpanded = this.treeState.get(uri);

        if (isExpanded) {
            // Collapse
            this.treeState.set(uri, false);
            childContainer.style.display = 'none';
            iconElement.className = 'bi bi-folder2';
        } else {
            // Expand
            this.treeState.set(uri, true);
            childContainer.style.display = 'block';
            iconElement.className = 'bi bi-folder2-open';

            // Load children if not already loaded
            if (childContainer.querySelector('.tree-loading') || childContainer.innerHTML.trim() === '') {
                const depth = parseInt(nodeElement.dataset.depth) || 0;
                this.loadTreeNode(uri, childContainer, depth + 1);
            }
        }
    }

    queryUri(uri) {
        // When clicking on a tree node, query it and show result in console
        this.codeInput.value = uri;
        this.executeCode();
        // Also focus the object in the inspector
        this.focusObject(uri);
    }

    // ==================== Object Inspector Panel ====================

    focusObject(uri) {
        if (!this.connected) {
            this.showError(this.inspectorContainer, 'Not connected', 'bi-wifi-off');
            return;
        }

        this.inspectorUri.textContent = uri;
        this.showLoading(this.inspectorContainer, 'Loading...');

        this.sendQuery(`"*${uri}"./m/web/inst/doc()`, (response, error) => {
            if (error) {
                this.showError(this.inspectorContainer, error);
                return;
            }
            // Strip prefix/quotes and highlight
            this.inspectorContainer.innerHTML = this.highlightMtron(this.stripMtronResponse(response));
        });
    }

    // ==================== Console Panel ====================

    executeCode() {
        const code = this.codeInput.value.trim();
        if (!code) return;

        if (!this.connected) {
            this.appendOutput(code, null, 'not connected to metatron');
            return;
        }
// f"'${code}'./m/web/inst/doc()",
        this.sendQuery("'" + code + "'" + "./m/web/inst/doc()", (response, error) => {
            this.appendOutput(code, this.stripMtronResponse(response), error);
        });
    }

    appendOutput(code, result, error) {
        const timestamp = new Date().toLocaleTimeString();

        let resultHtml;
        if (error) {
            resultHtml = `<div class="output-error">${this.escapeHtml(error)}</div>`;
        } else {
            // Try to format JSON nicely
            const formatted = this.formatOutput(result);
            resultHtml = `<div class="output-result">${formatted}</div>`;
        }

        const entryHtml = `
            <div class="output-entry">
                <div class="output-timestamp">${timestamp}</div>
                <div class="output-input">${this.escapeHtml(code)}</div>
                ${resultHtml}
            </div>`;

        // Prepend new entry (most recent at top)
        const placeholder = this.outputContainer.querySelector('.text-muted');
        if (placeholder) {
            this.outputContainer.innerHTML = '';
        }

        this.outputContainer.insertAdjacentHTML('afterbegin', entryHtml);
    }

    formatOutput(output) {
        if (!output) return '<span class="text-muted">noobj</span>';

        // Try to detect and format JSON
        const trimmed = output.trim();
        if ((trimmed.startsWith('{') || trimmed.startsWith('[')) &&
            (trimmed.endsWith('}') || trimmed.endsWith(']'))) {
            try {
                const parsed = JSON.parse(trimmed);
               return this.highlightMtron(this.stripMtronResponse(output));
                // return `<pre class="json-display m-0">${this.syntaxHighlightJson(JSON.stringify(parsed, null, 2))}</pre>`;
            } catch (e) {
                // Not valid JSON, continue to mtron highlighting
            }
        }

        // Apply mtron syntax highlighting
        return this.highlightMtron(output);
    }

    highlightMtron(code) {
        // Use highlight.js with mtron language if available
        if (typeof hljs !== 'undefined' && hljs.getLanguage('mtron')) {
            try {
                const highlighted = hljs.highlight(code, {language: 'mtron'});
                return `<pre class="hljs m-0">${highlighted.value}</pre>`;
            } catch (e) {
                console.warn('highlight.js failed:', e);
            }
        }
        // Fallback: escape and return
        return `<pre class="m-0">${this.escapeHtml(code)}</pre>`;
    }

    syntaxHighlightJson(json) {
        // Simple JSON syntax highlighting
        return json
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?)/g, (match) => {
                let cls = 'json-string';
                if (match.endsWith(':')) {
                    cls = 'json-key';
                }
                return `<span class="${cls}">${match}</span>`;
            })
            .replace(/\b(true|false)\b/g, '<span class="json-boolean">$1</span>')
            .replace(/\b(-?\d+\.?\d*)\b/g, '<span class="json-number">$1</span>')
            .replace(/\bnull\b/g, '<span class="json-boolean">null</span>');
    }

    clearOutput() {
        this.outputContainer.innerHTML = `
            <div class="text-muted small">
                <i class="bi bi-info-circle me-1"></i>
                Output cleared
            </div>`;
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
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash;
        }
        return Math.abs(hash).toString(36);
    }

    showError(message) {
        console.error(message);
        this.updateStats(`Error: ${message}`);
    }

    updateStats(message) {
        this.statsDisplay.innerHTML = `<i class="bi bi-info-circle me-1"></i>${message}`;
    }
}

// Initialize dashboard when DOM is ready
let dashboard;
document.addEventListener('DOMContentLoaded', () => {
    dashboard = new MetatronDashboard();
});
