(function ($) {
    "use strict";
    // LOADING WIDGET
    var spinner = function () {
        setTimeout(function () {
            if ($('#spinner').length > 0) {
                $('#spinner').removeClass('show');
            }
        }, 1);
    };
    spinner();
    // FADE-IN-OUT EFFECT INITIALIZE
    new WOW().init();
    // STICK NAVBAR
    $(window).scroll(function () {
        if ($(this).scrollTop() > 300) {
            $('.sticky-top').addClass('shadow-sm').css('top', '0px');
        } else {
            $('.sticky-top').removeClass('shadow-sm').css('top', '-100px');
        }
        if ($(this).scrollTop() > 300) {
            $('.back-to-top').addClass('show');
        } else {
            $('.back-to-top').removeClass('show');
        }
    });
    $('.back-to-top').click(function () {
        $('html, body').animate({scrollTop: 0}, 1500, 'easeInOutExpo');
        return false;
    });
    // SYNTAX HIGHLIGHTING IN <code> SNIPPETS
    hljs.highlightAll();
    // TERMYNAL TABS
    $(function () {
        $("li").click(function (e) {
            e.preventDefault();
            $("li").removeClass("selected");
            $(this).addClass("selected");
        });
    });

    // CUSTOM DOCS SCROLL HANDLING
    $(document).ready(function () {
        // Toggle dark theme for the tutorial section based on system/user preference if needed
        // but for now, we just ensure it's readable.

        // Initialize tooltips
        var tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'))
        var tooltipList = tooltipTriggerList.map(function (tooltipTriggerEl) {
            return new bootstrap.Tooltip(tooltipTriggerEl)
        })
    });

    // LECTURE SEARCH FILTERING
    $(document).ready(function () {
        $('#lecture-search').on('keyup', function () {
            var rawValue = $(this).val().toLowerCase();

            // Show/hide clear button
            if (rawValue.length > 0) {
                $('#clear-search').show();
            } else {
                $('#clear-search').hide();
            }

            // Tokenize search input (respecting quotes for multi-word tokens)
            var tokens = [];
            var regex = /[^\s"']+|"([^"]*)"|'([^']*)'/g;
            var match;
            while ((match = regex.exec(rawValue)) !== null) {
                // Get the captured group if it was quoted, otherwise the full match
                tokens.push(match[1] || match[2] || match[0]);
            }

            var firstMatchingSlide = -1;
            $('.carousel-item').each(function (slideIndex) {
                var slideHasVisible = false;
                $(this).find('.tutorial-grid button').each(function () {
                    var btnText = $(this).text().toLowerCase();
                    var targetId = $(this).attr('data-bs-target');
                    var contentText = $(targetId).text().toLowerCase();
                    var fullSearchableText = btnText + " " + contentText;
                    
                    var toggle = true;
                    if (tokens.length > 0) {
                        // All tokens must be present (AND logic)
                        for (var i = 0; i < tokens.length; i++) {
                            if (fullSearchableText.indexOf(tokens[i]) === -1) {
                                toggle = false;
                                break;
                            }
                        }
                    }

                    $(this).toggle(toggle);
                    if (toggle) slideHasVisible = true;
                });

                if (slideHasVisible && firstMatchingSlide === -1) {
                    firstMatchingSlide = slideIndex;
                }
            });

            if (tokens.length > 0 && firstMatchingSlide !== -1) {
                // If there's a match, prefer jumping to a topic slide (index > 0)
                // if it's the first match, but if we only match in 'All', that's fine too.
                // Actually, the current logic finds the FIRST matching slide.
                // Since 'All' is index 0, it will always match 'All' first if it matches anything.
                // We might want to jump to the specific topic instead if it's not 'All'.
                
                var bestMatch = firstMatchingSlide;
                if (bestMatch === 0) {
                    // Look if there's a match in subsequent slides (the specific topics)
                    $('.carousel-item').each(function(idx) {
                        if (idx > 0) {
                            var matchInTopic = false;
                            $(this).find('.tutorial-grid button').each(function() {
                                if ($(this).css('display') !== 'none') {
                                    matchInTopic = true;
                                    return false;
                                }
                            });
                            if (matchInTopic) {
                                bestMatch = idx;
                                return false;
                            }
                        }
                    });
                }
                $('#tutorial-carousel').carousel(bestMatch);
            }
        });

        // Clear search button functionality
        $('#clear-search').on('click', function() {
            $(this).hide();
            $('#lecture-search').val('').keyup().focus();
        });

        // CAROUSEL JUMP BUTTONS
        $('.carousel-jump').on('click', function() {
            var slideTo = $(this).attr('data-bs-slide-to');
            $('#tutorial-carousel').carousel(parseInt(slideTo));
        });

        // UPDATE ACTIVE BUTTON ON CAROUSEL SLIDE
        $('#tutorial-carousel').on('slid.bs.carousel', function (e) {
            var index = e.to;
            $('.carousel-jump').removeClass('active');
            $('.carousel-jump[data-bs-slide-to="' + index + '"]').addClass('active');
        });

        // REORDER PANELS BY CLICK ORDER
        $('.tutorial-grid button').on('click', function () {
            var targetId = $(this).attr('data-bs-target');
            
            // Move the element to the end of the container
            // to ensure it appears in the order it was clicked.
            $(targetId).appendTo('#custom-docs');

            // Hide tooltip when button is clicked (to avoid it staying on screen)
            var tooltip = bootstrap.Tooltip.getInstance(this);
            if (tooltip) tooltip.hide();

            // Show PDF export button and the custom-docs panel when a tutorial is active
            // We use setTimeout to allow Bootstrap to start the collapse animation/state change
            var checkVisibility = function() {
                var anyVisible = $('#custom-docs .collapse.show, #custom-docs .collapse.collapsing').length > 0;
                if (anyVisible) {
                    $('#pdf-export-container').show();
                    $('#custom-docs').show();
                } else {
                    $('#pdf-export-container').hide();
                    $('#custom-docs').hide();
                }
            };
            
            // Check multiple times to catch state transitions
            setTimeout(checkVisibility, 50);
            setTimeout(checkVisibility, 250);
            setTimeout(checkVisibility, 500);
        });

        // HANDLE URL FRAGMENTS (HASH) FOR DIRECT TUTORIAL ACCESS
        function handleHash() {
            var hash = window.location.hash;
            if (hash && hash.length > 1) {
                var tutorialName = decodeURIComponent(hash.substring(1)).toLowerCase();
                var found = false;
                
                $('.tutorial-grid button').each(function() {
                    var btnText = $(this).text().trim().toLowerCase();
                    // Match by exact name or slugified name (replace spaces with dashes)
                    if (btnText === tutorialName || btnText.replace(/\s+/g, '-') === tutorialName || btnText.replace(/\s+/g, '') === tutorialName) {
                        // Only click if it's not already open (aria-expanded="false")
                        if ($(this).attr('aria-expanded') === 'false') {
                            $(this).click();
                        }
                        
                        // Also jump to the slide containing this button if it's not the active slide
                        var $carouselItem = $(this).closest('.carousel-item');
                        if ($carouselItem.length > 0 && !$carouselItem.hasClass('active')) {
                            var slideIndex = $carouselItem.index();
                            $('#tutorial-carousel').carousel(slideIndex);
                        }
                        
                        found = true;
                        return false; // Break loop
                    }
                });
                
                if (found) {
                    // Smooth scroll to the custom-docs area after a short delay
                    setTimeout(function() {
                        var $docs = $('#custom-docs');
                        if ($docs.length > 0 && $docs.is(':visible')) {
                            $('html, body').animate({
                                scrollTop: $docs.offset().top - 100
                            }, 500);
                        }
                    }, 600);
                }
            }
        }

        // Run on load
        handleHash();
        
        // Also run when hash changes without page reload
        $(window).on('hashchange', handleHash);

        // Handle close button click to update container visibility
        $('#custom-docs').on('click', '.btn-close', function(e) {
            e.preventDefault();
            e.stopPropagation();

            // Find the ID of the collapse panel this close button belongs to
            var targetId = $(this).attr('data-bs-target');
            
            // Find the corresponding grid button that toggles this panel
            // and trigger a click on it to ensure both the panel closes 
            // and the button's 'active' state (if any) is updated.
            // We search in .tutorial-grid to find the button with the same data-bs-target
            $('.tutorial-grid button[data-bs-target="' + targetId + '"]').first().click();

            // Hide tooltip when close button is clicked
            var tooltip = bootstrap.Tooltip.getInstance(this);
            if (tooltip) tooltip.hide();
        });

        // Handle copy URL button click
        $('#custom-docs').on('click', '.btn-copy-url', function(e) {
            e.preventDefault();
            e.stopPropagation();

            var $panel = $(this).closest('.collapse');
            var panelId = $panel.attr('id');
            var $gridBtn = $('.tutorial-grid button[data-bs-target="#' + panelId + '"]').first();
            var tutorialName = $gridBtn.text().trim();
            
            // Construct the full URL with the fragment
            var hash = tutorialName.replace(/\s+/g, '-').toLowerCase();
            var url = window.location.origin + window.location.pathname + window.location.search + '#' + hash;

            // Copy to clipboard
            var $temp = $("<input>");
            $("body").append($temp);
            $temp.val(url).select();
            document.execCommand("copy");
            $temp.remove();

            // Show feedback via tooltip
            var tooltip = bootstrap.Tooltip.getInstance(this);
            if (tooltip) {
                // In Bootstrap 5, we can use setContent to update title or manually trigger
                // but simpler to hide, update attribute, show, then revert.
                var originalTitle = $(this).attr('data-bs-original-title') || $(this).attr('title');
                
                $(this).attr('data-bs-original-title', 'Copied!').tooltip('show');
                
                var btn = this;
                setTimeout(function() {
                    $(btn).attr('data-bs-original-title', originalTitle).tooltip('hide');
                }, 2000);
            }
        });
    });
})(jQuery);

function slideShowPage(id) {
    if (id === 0) {
        triggerSweep("index.html");
    } else {
        triggerSweep("tractatus.html");
    }
}

function triggerSweep(url) {
    const sweep = $('<div class="page-sweep"></div>').appendTo('body');
    setTimeout(() => sweep.addClass('active'), 5);
    setTimeout(() => window.location.href = url, 250);
}

$(document).ready(function () {
    // Initial sweep-out on page load
    const sweep = $('<div class="page-sweep active"></div>').appendTo('body');
    setTimeout(() => {
        sweep.addClass('exit');
        setTimeout(() => sweep.remove(), 250);
    }, 100);

    $('a').on('click', function(e) {
        const href = $(this).attr('href');
        const currentPath = window.location.pathname.split('/').pop() || 'index.html';
        if (href && (href === 'index.html' || href === 'tractatus.html') && href !== currentPath) {
            e.preventDefault();
            triggerSweep(href);
        }
    });

    const hash = window.location.hash.substring(1);
    if (hash === "1") {
        window.location.href = "tractatus.html";
    }
});

/************************
 *  TERMYNAL FUNCTIONS  *
 ************************/

function parseConsoleOutput(consoleOutput) {
    var lines = consoleOutput.split("\n");
    var outputs = new Array();
    lines.forEach((line, i) => {
        if (line.startsWith("==>"))
            outputs.push({type: "input", prompt: line.trim()});
        else if (line.startsWith("mtron>"))
            outputs.push({type: "input", value: line.replace("mtron>", "").trim()})
        else if (line.startsWith("$"))
            outputs.push({type: "input", prompt: "$", value: line.replace("$", "").trim()})
        else if (line.startsWith("%")) {
            outputs.push({type: "input", prompt: "", value: line.replace("%", "").trim()})
            outputs.push({type: "progress"})
        } else if (line.startsWith("........."))
            outputs.push({type: "input", prompt: ".........", value: "    " + line.replace(".........", "").trim()})
        else
            outputs.push({type: "input", prompt: "", value: line.trim()})
    });
    return outputs;
}

var termynals = {};

function refreshTermynal(id) {
    if (null != termynals[id]) {
        var x = termynals[id]
        x.lines.splice(0, x.lines.length)
        x.lineData.splice(0, x.lineData.length)
        x.lines = []
        x.container = null
        delete termynals[id]
    }
    var t = new Termynal('#' + id,
        {
            lines: [],
            lineData: parseConsoleOutput(cluster),
            typeDelay: 5,
            lineDelay: 50,
            noInit: true
        });

    t.init();
    t.lines.splice(0, t.lines.length)
    t.lines = t.lineData
    termynals[id] = t;
}

function modalPanel(title, subtitle, icon, htmlBody) {
    $(document).ready(function () {
        $("#modalPanel").replaceWith(`
            <div class="modal-header">
                <div class="bg-dark d-flex flex-shrink-0 align-items-center justify-content-center" style="width: 50px; height: 50px;">
                    <img src="${icon}" alt="${title}" width="32" height="32" class="icon-color">
                </div>
                <div class="d-flex justify-content-left align-items-left">
                    <h4 class="modal-title">${title}: ${subtitle}</h4>
                </div>
        
            </div>
            <div class="spinner-border text-primary" role="status">
  <span class="visually-hidden">generating documentation about ${title}</span>
</div>
            <!-- <div id="modalContent"></div> -->
        </div>
    </div>
</div>
`);
      
        $("#modalPanel").modal("show");
    });
    return $('#modalContent').load(htmlBody + " #content");
}

function modalText(title) {
    switch (title) {
        case 'mtron': {
            modalPanel('mtron', 'monads controlled by monoids', 'images/icons/metatron-icon.svg', "website-back.index.html");
            break;
        }
    }
}

function featurePanel(id, title, icon, frontHTML, backImage) {
    $('#' + id).replaceWith(`
<div class="col-lg-4 col-md-6 wow fadeInUp flip-box" data-wow-delay="0.1s">
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
                <a onclick="modalText('${title}')" href="javascript:void(0);">
                    <img src="${backImage}" alt="${title}" class="icon-color" width="100%" height="100%"/>
                </a>
            </div>
            <div class="row">
                <div class="col position-absolute bottom-0 end-0 d-flex justify-content-center">
                    <a onclick="modalText('${title}')" href="javascript:void(0);">learn more</a>
                </div>
            </div>
        </div>
      </div>
   </div>
</div>
`);
}


