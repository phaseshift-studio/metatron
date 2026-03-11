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
})(jQuery);

function slideShowPage(id) {
    $('#slideshow_pages').carousel(id);
    Object.keys(termynals).forEach(key => refreshTermynal(key));
}

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
<div id="modalPanel" class="modal fade" tabindex="-1">
    <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content bg-secondary text-light neon-box">
            <div class="modal-header">
                <div class="bg-dark d-flex flex-shrink-0 align-items-center justify-content-center" style="width: 50px; height: 50px;">
                    <img src="${icon}" alt="${title}" width="32" height="32" class="icon-color">
                </div>
                <div class="d-flex justify-content-left align-items-left">
                    <h4 class="modal-title">${title}: ${subtitle}</h4>
                </div>
                <!--<button type="button" class="btn-close" data-bs-dismiss="modal"></button>-->
            </div>
             <div id="modalContent"></div>
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
        <div class="justify-content-center overflow-hidden bg-secondary h-100 p-1 ps-1">
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

