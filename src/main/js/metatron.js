var webSocket = new WebSocket("ws://localhost:8080/");

webSocket.onmessage = function(event) {
    document.getElementById("rate").innerHTML = event.data;
};

window.addEventListener("load", function() {
    webSocket.onopen = function() {
        console.log("Connected to WebSocket server");
    };
}, false);