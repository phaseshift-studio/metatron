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

            $('.tutorial-grid button').each(function () {
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
            });
            
            // Hide parent rows if all buttons inside are hidden
            $('.tutorial-grid').each(function() {
                var buttons = $(this).find('button');
                var hasVisible = buttons.filter(function() {
                    return $(this).css('display') !== 'none';
                }).length > 0;
                $(this).toggle(hasVisible);
            });
        });

        // Clear search button functionality
        $('#clear-search').on('click', function() {
            $(this).hide();
            $('#lecture-search').val('').keyup().focus();
        });

        // REORDER PANELS BY CLICK ORDER
        $('.tutorial-grid button').on('click', function () {
            var targetId = $(this).attr('data-bs-target');
            // If it's about to be shown (currently not shown or being toggled)
            // Bootstrap's collapse plugin will handle the visibility, 
            // but we want to move the element to the end of the container
            // to ensure it appears in the order it was clicked.
            $(targetId).appendTo('#custom-docs');
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


