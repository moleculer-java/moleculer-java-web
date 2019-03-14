package services.moleculer.web.common;

public interface Endpoint {

	public void send(String message);
	
	public boolean isOpen();
	
	public Object getInternal();
	
}
