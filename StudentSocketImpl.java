import java.net.*;
import java.io.*;
import java.util.Timer;

class StudentSocketImpl extends BaseSocketImpl {
	// SocketImpl data members:
	   protected InetAddress address;
	   protected int port;
	   protected int localport;

  private Demultiplexer D;
  private Timer tcpTimer;
  private String state;


  StudentSocketImpl(Demultiplexer D) {  // default constructor
    this.D = D;
  }

  /**
   * Connects this socket to the specified port number on the specified host.
   *
   * @param      address   the IP address of the remote host.
   * @param      port      the port number.
   * @exception  IOException  if an I/O error occurs when attempting a
   *               connection.
   */
  public synchronized void connect(InetAddress address, int port) throws IOException{
    //Called for an active open (client) to connect to address:port
	//Initialize state, register this socket with demultiplexer, 
	//  and send syn packet to waiting server
	  
	state = "SYS_SENT";
	System.out.println(state);
	localport = D.getNextAvailablePort();
	this.address = address;
	this.port = port;
	
	D.registerConnection(address, localport, port, this);
	
	TCPPacket synpack = new TCPPacket(localport, port, 0, 0, false, true, false, 20, null);
	TCPWrapper.send(synpack, address);
  }
  
  /**
   * Called by Demultiplexer when a packet comes in for this connection
   * @param p The packet that arrived 
   */
  public synchronized void receivePacket(TCPPacket p) throws InterruptedException{
	  //called by Demultiplexer when a packet arrives for this connection. You must have registered with the
	  //  Demultiplexer first.
	  TCPPacket returnpack = null;
	  address = p.sourceAddr;
	  localport = p.destPort;
	  port = p.sourcePort;
	  
	  this.notifyAll();
	  
	  try {
		  if (p.synFlag && p.ackFlag){ 
			  state = "ESTABLISHED";
			  returnpack = new TCPPacket(localport, port, p.ackNum, (p.seqNum + 1), true, false, false, 30, null);
			  TCPWrapper.send(returnpack, address);
			  System.out.println(state);
			  
		  }
		  else if (p.synFlag){
			  state = "SYN_RECIEVED";
			  D.unregisterListeningSocket(p.destPort, this);
			  D.registerConnection(p.sourceAddr, localport, port, this);
			  
			  returnpack = new TCPPacket(localport, port, p.ackNum, (p.seqNum + 1), true, true, false, 30, null);
			  TCPWrapper.send(returnpack, address);
			  System.out.println(state);
			  
		  }
		  else if (p.ackFlag){
			  if (state == "LAST_ACK" || state == "FIN_WAIT_2"){
				  state = "TIME_WAIT";
				  
			  }
			  else {
				  state = "ESTABLISHED";
			  }
			  System.out.println(state);
		  }
		  else if (p.finFlag){ 
			  if (state == "ESTABLISHED"){
				  state = "CLOSE_WAIT";
			  }
			  else if (state == "FIN_WAIT_1"){
				  state = "FIN_WAIT_2";
			  }
			  returnpack = new TCPPacket(localport, port, p.ackNum, (p.seqNum + 1), true, false, false, 30, null);
			  
			  TCPWrapper.send(returnpack, address);
			  System.out.println(state);
		  }
		  

	  } catch (IOException e) {
		  System.out.println("EXCEPTION RECEIVED: \n"+e);
          System.exit(1);
		}
  }
  
  /** 
   * Waits for an incoming connection to arrive to connect this socket to
   * Ultimately this is called by the application calling 
   * ServerSocket.accept(), but this method belongs to the Socket object 
   * that will be returned, not the listening ServerSocket.
   * Note that localport is already set prior to this being called.
   */
  public synchronized void acceptConnection() throws IOException {
	  //Waits for an incoming connection to arrive to connect this socket to. Ultimately this is called by the
	  //  application calling ServerSocket.accept(), but this method belongs to the Socket object that will be
	  //  returned, not the listening ServerSocket.
	  
	  D.registerListeningSocket(this.getLocalPort(), this);
	  state = "LISTEN";
	  System.out.println(state);
  }

  
  /**
   * Returns an input stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an 
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     a stream for reading from this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               input stream.
   */
  public InputStream getInputStream() throws IOException {
    // project 4 return appIS;
    return null;
    
  }

  /**
   * Returns an output stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an 
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     an output stream for writing to this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               output stream.
   */
  public OutputStream getOutputStream() throws IOException {
    // project 4 return appOS;
    return null;
  }


  /**
   * Closes this socket. 
   *
   * @exception  IOException  if an I/O error occurs when closing this socket.
   */
  public synchronized void close() throws IOException {
	  //close the connection. called by the application

	  TCPPacket p = new TCPPacket(localport, port, -1, -1, false, false, true, 30, null); 
	  if (state == "ESTABLISHED"){
		  state = "FIN_WAIT_1";
	  }
	  else if (state == "CLOSE_WAIT"){
		  state = "LAST_ACK";
	  }

	  TCPWrapper.send(p, address);
  }

  /** 
   * create TCPTimerTask instance, handling tcpTimer creation
   * @param delay time in milliseconds before call
   * @param ref generic reference to be returned to handleTimer
   */
  private TCPTimerTask createTimerTask(long delay, Object ref){
	  //handle timer event, called by TCPTimerTask. ref is a generic pointer that you can use to pass data back
	  //  to this routine when the timer expires. (hint, could be a TCPPacket)
    if(tcpTimer == null)
      tcpTimer = new Timer(false);
    return new TCPTimerTask(tcpTimer, delay, this, ref);
  }


  /**
   * handle timer expiration (called by TCPTimerTask)
   * @param ref Generic reference that can be used by the timer to return 
   * information.
   */
  public synchronized void handleTimer(Object ref){

    // this must run only once the last timer (30 second timer) has expired
    tcpTimer.cancel();
    tcpTimer = null;
  }
}
