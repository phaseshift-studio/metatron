(function () {
    "use strict";
    // LOADING WIDGET
    var spinner = function () {
        setTimeout(function () {
            var el = document.getElementById('spinner');
            if (el) {
                el.classList.remove('show');
            }
        }, 1);
    };
    spinner();
    // STICK NAVBAR
    window.addEventListener('scroll', function () {
        var scrolled = window.scrollY > 300;
        document.querySelectorAll('.sticky-top').forEach(function (el) {
            if (scrolled) {
                el.classList.add('shadow-sm');
                el.style.top = '0px';
            } else {
                el.classList.remove('shadow-sm');
                el.style.top = '-100px';
            }
        });
        document.querySelectorAll('.back-to-top').forEach(function (el) {
            el.classList.toggle('show', scrolled);
        });
    });
    // SYNTAX HIGHLIGHTING IN <code> SNIPPETS
    // (pages that do not ship highlight.js — e.g. 404.html — must still run the rest of this file)
    if (typeof hljs !== 'undefined') hljs.highlightAll();

    document.addEventListener('DOMContentLoaded', function () {
        // BACK TO TOP
        document.querySelectorAll('.back-to-top').forEach(function (el) {
            el.addEventListener('click', function (e) {
                e.preventDefault();
                window.scrollTo({ top: 0, behavior: 'smooth' });
            });
        });

        // Initialize tooltips
        document.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(function (el) {
            new bootstrap.Tooltip(el);
        });

        // LECTURE SEARCH FILTERING
        var search = document.getElementById('lecture-search');
        var clearSearch = document.getElementById('clear-search');

        if (search) {
            search.addEventListener('keyup', function () {
                var rawValue = search.value.toLowerCase();

                // Show/hide clear button
                if (clearSearch) {
                    clearSearch.style.display = rawValue.length > 0 ? '' : 'none';
                }

                // Tokenize search input (respecting quotes for multi-word tokens)
                var tokens = [];
                var regex = /[^\s"']+|"([^"]*)"|'([^']*)'/g;
                var match;
                while ((match = regex.exec(rawValue)) !== null) {
                    // Get the captured group if it was quoted, otherwise the full match
                    tokens.push(match[1] || match[2] || match[0]);
                }

                document.querySelectorAll('.tutorial-grid button').forEach(function (btn) {
                    var btnText = btn.textContent.toLowerCase();
                    var selector = btn.getAttribute('data-bs-target');
                    var target = selector ? document.querySelector(selector) : null;
                    var contentText = target ? target.textContent.toLowerCase() : '';
                    var fullSearchableText = btnText + " " + contentText;

                    var toggle = true;
                    if (tokens.length > 0) {
                        for (var i = 0; i < tokens.length; i++) {
                            if (fullSearchableText.indexOf(tokens[i]) === -1) {
                                toggle = false;
                                break;
                            }
                        }
                    }

                    btn.style.display = toggle ? '' : 'none';
                });
            });
        }

        // Clear search button functionality
        if (clearSearch) {
            clearSearch.addEventListener('click', function () {
                clearSearch.style.display = 'none';
                if (search) {
                    search.value = '';
                    search.dispatchEvent(new Event('keyup'));
                    search.focus();
                }
            });
        }

        var customDocs = document.getElementById('custom-docs');
        var pdfExport = document.getElementById('pdf-export-container');

        // REORDER PANELS BY CLICK ORDER
        document.querySelectorAll('.tutorial-grid button').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var selector = btn.getAttribute('data-bs-target');
                var target = selector ? document.querySelector(selector) : null;

                // Move the element to the end of the container
                // to ensure it appears in the order it was clicked.
                if (target && customDocs) {
                    customDocs.appendChild(target);
                }

                // Hide tooltip when button is clicked (to avoid it staying on screen)
                var tooltip = bootstrap.Tooltip.getInstance(btn);
                if (tooltip) tooltip.hide();

                // Show PDF export button and the custom-docs panel when a tutorial is active
                // We use setTimeout to allow Bootstrap to start the collapse animation/state change
                var checkVisibility = function () {
                    var anyVisible = document.querySelector('#custom-docs .collapse.show, #custom-docs .collapse.collapsing');
                    if (anyVisible) {
                        if (pdfExport) pdfExport.style.display = '';
                        if (customDocs) customDocs.style.display = '';
                    } else {
                        if (pdfExport) pdfExport.style.display = 'none';
                        if (customDocs) customDocs.style.display = 'none';
                    }
                };

                // Check multiple times to catch state transitions
                setTimeout(checkVisibility, 50);
                setTimeout(checkVisibility, 250);
                setTimeout(checkVisibility, 500);
            });
        });

        // HANDLE URL FRAGMENTS (HASH) FOR DIRECT TUTORIAL ACCESS
        var handleHash = function () {
            var hash = window.location.hash;
            if (!hash || hash.length <= 1) return;

            var fragment = decodeURIComponent(hash.substring(1));

            // ── Case 1: Fragment matches an element ID on the page ──
            var targetEl = document.getElementById(fragment);
            if (targetEl) {
                // Find the enclosing .collapse panel (if any)
                var panel = targetEl.closest('.collapse');
                if (panel) {
                    var panelId = panel.id; // e.g. "card-0"
                    // Open the panel by clicking its grid button
                    var btn = document.querySelector('.tutorial-grid button[data-bs-target="#' + panelId + '"]');
                    if (btn && btn.getAttribute('aria-expanded') === 'false') {
                        btn.click();
                    }
                }
                // Scroll to the target after the collapse animation completes
                // (or immediately if no panel was opened)
                var delay = panel ? 650 : 100;
                setTimeout(function () {
                    if (customDocs) customDocs.style.display = '';
                    if (pdfExport) pdfExport.style.display = '';
                    window.scrollTo({
                        top: targetEl.getBoundingClientRect().top + window.pageYOffset - 120,
                        behavior: 'smooth'
                    });
                }, delay);
                return;
            }

            // ── Case 2: Fragment matches a card panel ID directly ──
            if (/^card-\d+$/.test(fragment)) {
                var cardBtn = document.querySelector('.tutorial-grid button[data-bs-target="#' + fragment + '"]');
                if (cardBtn && cardBtn.getAttribute('aria-expanded') === 'false') {
                    cardBtn.click();
                    setTimeout(function () {
                        if (customDocs) customDocs.style.display = '';
                        if (pdfExport) pdfExport.style.display = '';
                        if (customDocs) {
                            window.scrollTo({
                                top: customDocs.getBoundingClientRect().top + window.pageYOffset - 100,
                                behavior: 'smooth'
                            });
                        }
                    }, 600);
                }
                return;
            }

            // ── Case 3: Fragment matches a tutorial name (legacy behavior) ──
            var tutorialName = fragment.toLowerCase();
            var found = false;
            var buttons = document.querySelectorAll('.tutorial-grid button');
            for (var i = 0; i < buttons.length; i++) {
                var btnText = buttons[i].textContent.trim().toLowerCase();
                // Match by exact name or slugified name (replace spaces with dashes)
                if (btnText === tutorialName || btnText.replace(/\s+/g, '-') === tutorialName || btnText.replace(/\s+/g, '') === tutorialName) {
                    // Only click if it's not already open (aria-expanded="false")
                    if (buttons[i].getAttribute('aria-expanded') === 'false') {
                        buttons[i].click();
                    }

                    found = true;
                    break;
                }
            }

            if (found) {
                // Smooth scroll to the custom-docs area after a short delay
                setTimeout(function () {
                    if (customDocs && (customDocs.offsetWidth > 0 || customDocs.offsetHeight > 0)) {
                        window.scrollTo({
                            top: customDocs.getBoundingClientRect().top + window.pageYOffset - 100,
                            behavior: 'smooth'
                        });
                    }
                }, 600);
            }
        };

        // Run on load
        handleHash();

        // Also run when hash changes without page reload
        window.addEventListener('hashchange', handleHash);

        if (customDocs) {
            // Handle close button click to update container visibility
            customDocs.addEventListener('click', function (e) {
                var close = e.target.closest('.btn-close');
                if (!close) return;

                e.preventDefault();
                e.stopPropagation();

                // Find the ID of the collapse panel this close button belongs to
                var targetId = close.getAttribute('data-bs-target');

                // Find the corresponding grid button that toggles this panel
                // and trigger a click on it to ensure both the panel closes
                // and the button's 'active' state (if any) is updated.
                // We search in .tutorial-grid to find the button with the same data-bs-target
                var gridBtn = document.querySelector('.tutorial-grid button[data-bs-target="' + targetId + '"]');
                if (gridBtn) gridBtn.click();

                // Hide tooltip when close button is clicked
                var tooltip = bootstrap.Tooltip.getInstance(close);
                if (tooltip) tooltip.hide();
            });

            // Handle copy URL button click
            customDocs.addEventListener('click', function (e) {
                var copy = e.target.closest('.btn-copy-url');
                if (!copy) return;

                e.preventDefault();
                e.stopPropagation();

                var panel = copy.closest('.collapse');
                var panelId = panel ? panel.id : '';
                var gridBtn = document.querySelector('.tutorial-grid button[data-bs-target="#' + panelId + '"]');
                var tutorialName = gridBtn ? gridBtn.textContent.trim() : '';

                // Construct the full URL with the fragment
                var hash = tutorialName.replace(/\s+/g, '-').toLowerCase();
                var url = window.location.origin + window.location.pathname + window.location.search + '#' + hash;

                // Copy to clipboard
                var temp = document.createElement('input');
                document.body.appendChild(temp);
                temp.value = url;
                temp.select();
                document.execCommand('copy');
                temp.remove();

                // Show feedback via tooltip
                var tooltip = bootstrap.Tooltip.getOrCreateInstance(copy);
                var originalTitle = copy.getAttribute('data-bs-original-title') || copy.getAttribute('title') || '';
                tooltip.setContent({ '.tooltip-inner': 'copied' });
                tooltip.show();
                setTimeout(function () {
                    tooltip.hide();
                    tooltip.setContent({ '.tooltip-inner': originalTitle });
                }, 2000);
            });
        }

        // Initial sweep-out on page load
        var sweep = document.createElement('div');
        sweep.className = 'page-sweep active';
        document.body.appendChild(sweep);
        setTimeout(function () {
            sweep.classList.add('exit');
            setTimeout(function () {
                sweep.remove();
            }, 400);
        }, 100);

        // PAGE SWEEP ON INTERNAL NAVIGATION
        document.addEventListener('click', function (e) {
            var anchor = e.target.closest('a');
            if (!anchor) return;
            var href = anchor.getAttribute('href');
            var currentPath = window.location.pathname.split('/').pop() || 'index.html';
            if (href && (href === 'index.html' || href === 'tractatus.html') && href !== currentPath) {
                e.preventDefault();
                triggerSweep(href);
            }
        });

        // Legacy redirect from the hash-only home page
        if (window.location.hash.substring(1) === "1") {
            window.location.href = "tractatus.html";
        }

        // FEATURE GRID INTERACTIONS
        document.addEventListener('click', function (e) {
            var zoomable = e.target.closest('.zoomable');
            if (zoomable) {
                zoomable.classList.toggle('zoomed');
            }
        });

        document.addEventListener('click', function (e) {
            var trigger = e.target.closest('.feature-modal-trigger');
            if (!trigger) return;
            var flipBox = trigger.closest('.flip-box');
            if (!flipBox) return;
            var title = flipBox.querySelector('.feature-title-src');
            var icon = flipBox.querySelector('.feature-icon-src');
            var content = flipBox.querySelector('.feature-modal-content');
            modalPanel(
                title ? title.textContent : '',
                icon ? icon.textContent : '',
                content ? content.innerHTML : ''
            );
        });
    });
})();

function slideShowPage(id) {
    if (id === 0) {
        triggerSweep("index.html");
    } else {
        triggerSweep("tractatus.html");
    }
}

function triggerSweep(url) {
    const sweep = document.createElement('div');
    sweep.className = 'page-sweep';
    document.body.appendChild(sweep);
    setTimeout(function () {
        sweep.classList.add('active');
    }, 5);
    setTimeout(function () {
        window.location.href = url;
    }, 400);
}

function modalPanel(title, icon, htmlBody) {
    // Move modal to body to escape stacking context traps
    // (e.g., #content { position: relative; z-index: 1 } would otherwise
    // trap the modal's z-index below the backdrop's, rendering it unclickable)
    const panel = document.getElementById('modalPanel');
    if (panel && panel.parentElement !== document.body) {
        document.body.appendChild(panel);
    }

    // Destroy any existing modal instance to avoid state issues
    const existingModal = panel ? bootstrap.Modal.getInstance(panel) : null;
    if (existingModal) {
        existingModal.dispose();
    }
    // Defense in depth: ensure no leftover backdrop or body state
    document.querySelectorAll('.modal-backdrop').forEach(function (el) {
        el.remove();
    });
    document.body.classList.remove('modal-open');

    if (panel) {
        panel.innerHTML = `
    <div class="modal-dialog modal-dialog-centered modal-lg">
        <div class="modal-content bg-secondary text-light">
            <div class="modal-header border-bottom border-primary">
                <div class="bg-dark d-flex flex-shrink-0 align-items-center justify-content-center me-3" style="width: 50px; height: 50px;">
                    <img src="${icon}" alt="${title}" width="32" height="32" class="icon-color">
                </div>
                <h4 class="modal-title text-uppercase">${title}</h4>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                ${htmlBody}
            </div>
        </div>
    </div>
`;

        // Create a new modal instance and show it
        const modal = new bootstrap.Modal(panel, {
            backdrop: true,
            keyboard: true
        });
        modal.show();
    }
}

function featurePanel(id) {
    const el = document.getElementById(id);
    if (!el) return;

    const title = el.getAttribute('data-title');
    const icon = el.getAttribute('data-icon');
    const backImage = el.getAttribute('data-back-image');
    const front = el.querySelector('.feature-front');
    const modal = el.querySelector('.feature-modal');
    const frontHTML = front ? front.innerHTML : '';
    const modalHTML = modal ? modal.innerHTML : '';

    const wrapper = document.createElement('div');
    wrapper.innerHTML = `
<div id="${id}" class="col-lg-4 col-md-6 wow fadeInUp flip-box" data-wow-delay="0.1s">
   <div class="flip-box-inner">
      <div class="flip-box-front">
         <div class="service-item position-relative overflow-hidden bg-secondary d-flex h-100 p-1 ps-1">
            <div class="bg-dark d-flex flex-shrink-0 align-items-center justify-content-center"
               style="width: 60px; height: 60px;">
               <img src="${icon}" alt="${title}" width="32" height="32"
                  class="icon-color">
            </div>
            <div class="ps-2 p-3">
               <h3 class="text-uppercase mb-4">${title}</h3>
               ${frontHTML}
            </div>
         </div>
      </div>
      <div class="flip-box-back">
        <div class="d-flex justify-content-center align-items-center overflow-hidden bg-secondary h-100">
            <div class="row">
                <a class="feature-modal-trigger" href="javascript:void(0);">
                    <img src="${backImage}" alt="${title}" class="icon-color" width="100%" height="100%"/>
                </a>
            </div>
            <div class="row">
                <div class="col position-absolute bottom-0 end-0 d-flex justify-content-center">
                    <a class="feature-modal-trigger" href="javascript:void(0);">learn more</a>
                </div>
            </div>
        </div>
        <div class="feature-modal-content" style="display:none;">
            ${modalHTML}
        </div>
        <div class="feature-icon-src" style="display:none;">${icon}</div>
        <div class="feature-title-src" style="display:none;">${title}</div>
      </div>
   </div>
</div>
`;
    el.replaceWith(wrapper.firstElementChild);
}
