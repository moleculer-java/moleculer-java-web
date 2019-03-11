function connect() {
	
	console.log("Connect method invoked.");
	
	var url = new URL("/ws/test", window.location.href).href;
	url = url.replace(/http+/, "ws");

	var ws = new WebSocket(url);
	
	ws.onopen = function (evt) {
		console.log("WebSocket opened.");
	}
	
	ws.onmessage = function (evt) { 
		console.log("Message received: " + evt.data);
	}
	
	ws.onerror = function (evt) { 
		console.log("Error received: " + evt);
	}
	
	ws.onclose = function (evt) {
		console.log("WebSocket closed.");
	}
		
}