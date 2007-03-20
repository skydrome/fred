package freenet.node.fcp;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;

import freenet.support.Logger;
import freenet.support.api.BucketFactory;

public class FCPConnectionHandler {

	final FCPServer server;
	final Socket sock;
	final FCPConnectionInputHandler inputHandler;
	final FCPConnectionOutputHandler outputHandler;
	private boolean isClosed;
	private boolean inputClosed;
	private boolean outputClosed;
	private String clientName;
	private FCPClient client;
	final BucketFactory bf;
	final HashMap requestsByIdentifier;
	
	public FCPConnectionHandler(Socket s, FCPServer server) {
		this.sock = s;
		this.server = server;
		isClosed = false;
		this.bf = server.core.tempBucketFactory;
		requestsByIdentifier = new HashMap();
		this.inputHandler = new FCPConnectionInputHandler(this);
		this.outputHandler = new FCPConnectionOutputHandler(this);
	}
	
	void start() {
		inputHandler.start();
		outputHandler.start();
	}

	public void close() {
		ClientRequest[] requests;
		if(client != null)
			client.onLostConnection(this);
		synchronized(this) {
			isClosed = true;
			requests = new ClientRequest[requestsByIdentifier.size()];
			requests = (ClientRequest[]) requestsByIdentifier.values().toArray(requests);
		}
		for(int i=0;i<requests.length;i++)
			requests[i].onLostConnection();
		if((client != null) && !client.hasPersistentRequests())
			server.unregisterClient(client);
	}
	
	public synchronized boolean isClosed() {
		return isClosed;
	}
	
	public void closedInput() {
		try {
			sock.shutdownInput();
		} catch (IOException e) {
			// Ignore
		}
		synchronized(this) {
			inputClosed = true;
			if(!outputClosed) return;
		}
		try {
			sock.close();
		} catch (IOException e) {
			// Ignore
		}
	}
	
	public void closedOutput() {
		try {
			sock.shutdownOutput();
		} catch (IOException e) {
			// Ignore
		}
		synchronized(this) {
			outputClosed = true;
			if(!inputClosed) return;
		}
		try {
			sock.close();
		} catch (IOException e) {
			// Ignore
		}
	}

	public void setClientName(String name) {
		this.clientName = name;
		client = server.registerClient(name, server.core, this);
		if(Logger.shouldLog(Logger.MINOR, this))
			Logger.minor(this, "Set client name: "+name);
	}
	
	public String getClientName() {
		return clientName;
	}

	public void startClientGet(ClientGetMessage message) {
		String id = message.identifier;
		ClientGet cg = null;
		boolean success;
		boolean persistent = message.persistenceType != ClientRequest.PERSIST_CONNECTION;
		synchronized(this) {
			if(isClosed) return;
			// We need to track non-persistent requests anyway, so we may as well check
			if(persistent)
				success = true;
			else
				success = !requestsByIdentifier.containsKey(id);
			if(success) {
				try {
					cg = new ClientGet(this, message);
					if(!persistent)
						requestsByIdentifier.put(id, cg);
				} catch (IdentifierCollisionException e) {
					success = false;
				}
			}
		}
		if(!success) {
			Logger.normal(this, "Identifier collision on "+this);
			FCPMessage msg = new IdentifierCollisionMessage(id, message.global);
			outputHandler.queue(msg);
			return;
		} else {
			// Register before starting, because it may complete immediately, and if it does,
			// we may end up with it not being removable because it wasn't registered!
			if(cg.isPersistent()) {
				if(cg.isPersistentForever())
					server.forceStorePersistentRequests();
			}
			cg.start();
		}
	}

	public void startClientPut(ClientPutMessage message) {
		if(Logger.shouldLog(Logger.MINOR, this))
			Logger.minor(this, "Starting insert ID=\""+message.identifier+ '"');
		String id = message.identifier;
		ClientPut cp = null;
		boolean success;
		boolean persistent = message.persistenceType != ClientRequest.PERSIST_CONNECTION;
		synchronized(this) {
			if(isClosed) return;
			// We need to track non-persistent requests anyway, so we may as well check
			if(persistent)
				success = true;
			else
				success = !requestsByIdentifier.containsKey(id);
			if(success) {
				try {
					cp = new ClientPut(this, message);
				} catch (IdentifierCollisionException e) {
					success = false;
				}
				if(!persistent)
					requestsByIdentifier.put(id, cp);
			}
		}
		if(!success) {
			Logger.normal(this, "Identifier collision on "+this);
			FCPMessage msg = new IdentifierCollisionMessage(id, message.global);
			outputHandler.queue(msg);
			return;
		} else {
			Logger.minor(this, "Starting "+cp);
			// Register before starting, because it may complete immediately, and if it does,
			// we may end up with it not being removable because it wasn't registered!
			if(cp.isPersistent()) {
				if(cp.isPersistentForever())
					server.forceStorePersistentRequests();
			}
			cp.start();
		}
	}

	public void startClientPutDir(ClientPutDirMessage message, HashMap buckets) {
		if(Logger.shouldLog(Logger.MINOR, this))
			Logger.minor(this, "Start ClientPutDir");
		String id = message.identifier;
		ClientPutDir cp = null;
		boolean success;
		boolean persistent = message.persistenceType != ClientRequest.PERSIST_CONNECTION;
		synchronized(this) {
			if(isClosed) return;
			// We need to track non-persistent requests anyway, so we may as well check
			if(persistent)
				success = true;
			else
				success = !requestsByIdentifier.containsKey(id);
			if(success) {
				try {
					cp = new ClientPutDir(this, message, buckets);
				} catch (IdentifierCollisionException e) {
					success = false;
				}
				if(!persistent)
					requestsByIdentifier.put(id, cp);
			}
		}
		if(!success) {
			Logger.normal(this, "Identifier collision on "+this);
			FCPMessage msg = new IdentifierCollisionMessage(id, message.global);
			outputHandler.queue(msg);
			return;
		} else {
			// Register before starting, because it may complete immediately, and if it does,
			// we may end up with it not being removable because it wasn't registered!
			if(cp.isPersistent()) {
				if(cp.isPersistentForever())
					server.forceStorePersistentRequests();
			}
			if(Logger.shouldLog(Logger.MINOR, this))
				Logger.minor(this, "Starting "+cp);
			cp.start();
		}
	}
	
	public FCPClient getClient() {
		return client;
	}

	public void finishedClientRequest(ClientRequest get) {
		synchronized(this) {
			requestsByIdentifier.remove(get.getIdentifier());
		}
	}

	public boolean isGlobalSubscribed() {
		return client.watchGlobal;
	}

	public boolean hasFullAccess() {
		return server.allowedHostsFullAccess.allowed(sock.getInetAddress());
	}

}
