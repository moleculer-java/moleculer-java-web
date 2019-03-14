var webSocket = null;

var pingPongTimer = null;
var pingSubmitted = 0;
var pongReceived = 0;
var pingPeriod = 6 * 1000;
var pongTimeout = 1 * 1000;

var savedChannel = null;
var savedHandler = null;

function connect(channel, handler) {
	if (webSocket) {
		return;
	}
	if (!channel) {
		channel = "/ws/common";
	}
	savedChannel = channel;
	savedHandler = handler;
	var url = new URL(channel, window.location.href).href;
	url = url.replace(/http+/, "ws");
	console.log("Connecting to " + url + "...");
	webSocket = new WebSocket(url);
	webSocket.onopen = function (evt) {
		console.log("WebSocket channel opened.");
		pongReceived = pingSubmitted = new Date().valueOf();	
		startPingPongTimer();
	}
	webSocket.onmessage = function (evt) {
	    var msg = evt.data;
	    if (msg == "!") {
			pongReceived = new Date().valueOf();
			return;
		}	    
		if (handler) { 
			handler(msg);
		} else {
			console.log("WebSocket message received: " + msg);
		}
	}
	webSocket.onerror = function (evt) { 
		webSocketConnectionLost();
	}	
	webSocket.onclose = function (evt) {
		console.log("WebSocket channel closed.");
	}
}

function disconnect() {
	if (webSocket) {
		webSocket.close();
		webSocket = null;
		console.warn("WebSocket channel disconnected.");		
	}
}

function startPingPongTimer() {
	stopPingPongTimer();
	pingPongTimer = setInterval(function() {		
		var now = new Date().valueOf();
    	if (now - pingSubmitted > pingPeriod) {
    		pingSubmitted = now;
    		webSocket.send("!");
    		return;
    	}
        if ((pingSubmitted - pongReceived) >= pongTimeout
        		&& (now - pingSubmitted) >= pongTimeout) {       	
        	console.debug("Pong message timeouted.");
        	webSocketConnectionLost();		        	
        }
        
	}, 5000);
	console.log("Ping-pong timer started.");
}

function stopPingPongTimer() {
	if (pingPongTimer != null) {
		clearInterval(pingPongTimer);
		pingPongTimer = null;
		console.log("Ping-pong timer stopped.");
	}	
}

function webSocketConnectionLost() {
	if (!webSocket) {
		return;
	}
	console.log("WebSocket connection lost.");
	disconnect();
	stopPingPongTimer();
	connect(savedChannel, savedHandler);	
}