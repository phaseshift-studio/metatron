
    //var host = document.currentScript.dataset.host;
var host = document.currentScript.host;
   // Create a WebSocket connection to the server
    var socket = new WebSocket(host);
    socket.binaryType = "arraybuffer"; // This ensures binary data is received as ArrayBuffer

    // Handle the connection opening
    socket.addEventListener("open", (event) => {
      console.log("connected to metatron node " + host);
      document.getElementById("connectBtn").disabled = true;
      document.getElementById("sendBtn").disabled = false;
      appendMessage("connected to metatron server " + host);
    });

    // Handle incoming messages
    socket.addEventListener("message", (event) => {
     if (event.data instanceof ArrayBuffer) {
        const decoder = new TextDecoder('utf-8');
        const text = decoder.decode(event.data);
        appendMessage(`received: ${text}`);
      }
      
    });

    // Handle connection closure
    socket.addEventListener("close", (event) => {
      console.log("connection closed", event);
      document.getElementById("connectBtn").disabled = false;
      document.getElementById("sendBtn").disabled = true;
      appendMessage("connection closed to metatron server " + host);
    });

    // Handle errors
    socket.addEventListener("error", (error) => {
      console.error("webSocket error:", error);
      appendMessage("⚠️ error occurred: " + error);
    });

    // Function to append messages to the UI
    function appendMessage(text) {
      const messageDiv = document.createElement("li");
      messageDiv.textContent = text;
      document.getElementById("messages").appendChild(messageDiv);
    }

    // Send a message when the send button is clicked
    document.getElementById("sendBtn").addEventListener("click", () => {
      const messageInput = document.getElementById("messageInput");
      const message = messageInput.value;
      if (message) {
        socket.send(message);
        appendMessage(`sent: ${message}`);
        //messageInput.value = "";
      }
    });

    // Connect button functionality
    document.getElementById("connectBtn").addEventListener("click", () => {
      socket = new WebSocket(host);
      // Reattach event listeners to the new socket
      socket.addEventListener("open", (event) => {
        console.log("connected to server");
        document.getElementById("connectBtn").disabled = true;
        document.getElementById("sendBtn").disabled = false;
        appendMessage("connected to metatron server " + host);
      });
      socket.addEventListener("message", (event) => {
        appendMessage(`received: ${event.data}`);
      });
      socket.addEventListener("close", (event) => {
        console.log("connection closed", event);
        document.getElementById("connectBtn").disabled = false;
        document.getElementById("sendBtn").disabled = true;
        appendMessage("❌ connection closed to metatron server " + host);
      });
      socket.addEventListener("error", (error) => {
        console.error("WebSocket error:", error);
        appendMessage("⚠️ error occurred");
      });
    });