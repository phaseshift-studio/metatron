$(document).ready(function () {
    // wait for document to load
});

jQuery(function () {
    var markers = $(".circle"); // marker selector
    var widthGain = 1; // 1 default
    var heightGain = 1; // 1 default

    // repeat for all markers
    markers.each(function () {
        // Define variables
        var marker = $(this),
            width = marker.width(),
            height = 2 * marker.height(),
            ns = "http://www.w3.org/2000/svg";

        // if the svg element doesn't exist, create it
        var svg = document.createElementNS(ns, "svg");

        // Also providing attrs for width and height of the svg element
        $(svg)
            .css({
                width: width,
                height: height,
                transform:
                    "scale(" + (2 * widthGain * width) / height + "," + heightGain + ")"
            })
            .attr({
                width: width,
                height: height,
                viewBox: "-1 -1 2 2"
            });

        // attach it to the marker element
        marker[0].appendChild(svg);

        // create the path element
        var path = document.createElementNS(ns, "path");

        // Set path attributes and styles
        $(path)
            .attr({
                pathLength: 100,
                "vector-effect": "non-scaling-stroke"
            })
        svg.appendChild(path);

        // when creating the element the offset is initialized, however, because of the transition we have to hide it untill it disappears
        setCircle(false);
        setCircle(false);

        // generate a new circle and show path on mouse hover
        marker.mouseover(function () {
            setCircle(true);
        });

        // this function handles path drawing, it uses the circlePath() function that has tunable inputs - see the codePen https://codepen.io/spencerthayer/pen/nhjwu on how to tune them
        function setCircle(show_element) {
            if (show_element) {
                $(path).css("visibility", "visible");
            } else {
                $(path).css("visibility", "hidden");
            }

            var pathLength = 1000 * path.getTotalLength();

            // Set path attributes and styles
            $(path)
                .attr({
                    d: circlePath(-0.15, 0.05, 150, 190, 0.05, 0.3)
                })
                .attr({
                    "stroke-dasharray": pathLength,
                    "stroke-dashoffset": pathLength
                });
        }

        // generates a circle path - see https://codepen.io/spencerthayer/pen/nhjwu
        function circlePath(dr_min, dr_max, θ0_min, θ0_max, dθ_min, dθ_max) {
            var c = 0.551915024494,
                β = Math.atan(c),
                d = Math.sqrt(c * c + 1 * 1),
                r = 0.9,
                θ = ((θ0_min + Math.random() * (θ0_max - θ0_min)) * Math.PI) / 180,
                path = "M";

            path += [r * Math.sin(θ), r * Math.cos(θ)];
            path += " C" + [d * r * Math.sin(θ + β), d * r * Math.cos(θ + β)];

            for (var i = 0; i < 4; i++) {
                θ += (Math.PI / 2) * (1 + dθ_min + Math.random() * (dθ_max - dθ_min));
                r *= 1 + dr_min + Math.random() * (dr_max - dr_min);
                path +=
                    " " +
                    (i ? "S" : "") +
                    [d * r * Math.sin(θ - β), d * r * Math.cos(θ - β)];
                path += " " + [r * Math.sin(θ), r * Math.cos(θ)];
            }
            return path;
        }
    });
});
