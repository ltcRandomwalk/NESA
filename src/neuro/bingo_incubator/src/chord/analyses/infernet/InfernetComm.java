package chord.analyses.infernet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.util.Execution;
import chord.util.Utils;

@Chord(name = "infernetComm")
public class InfernetComm extends JavaAnalysis{
	
	Execution X = Execution.v();
	int port; // Port that Master listens on
	
	// Max number of milliseconds allowed from the time that a worker connection is accepted to the time
	// when data is available on the connection.
	int dataTransferTimeOut;
	
	// Number of milliseconds from last contact after which a worker is declared dead.
	int deadTimeOut;
	
	InfernetWrapper wrapper;
	
	String wrapperInstanceName;
	
	public void init(){
		port = Integer.getInteger("chord.infernet.port", 8988);
		dataTransferTimeOut = 1000 * 60 * 60;
		deadTimeOut = 2*1000 * 60 * 60;
		wrapperInstanceName = System.getProperty("chord.infernet.wrapper");
		wrapper = (InfernetWrapper) ClassicProject.g().getTask(wrapperInstanceName);
	}
	
	@Override
	public void run() {
		init();
		X.logs("MASTER: listening at port %s", port);
		try {
			ServerSocket socket = new ServerSocket(port);
			commController(socket);
			socket.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Chord-Infernet communication protocol enabler
	 */
	void commController(ServerSocket socket) {
		boolean exitFlag = false;
		while (true) {
			try {
				if (exitFlag) break;
				X.flushOutput();
				X.logs("============================================================");
				
				socket.setSoTimeout(deadTimeOut);
				Socket worker = socket.accept();
				String hostname = worker.getInetAddress().getHostAddress() /*+ ":" + worker.getPort()*/;
				BufferedReader in = new BufferedReader(new InputStreamReader(worker.getInputStream()));
				//PrintWriter out = new PrintWriter(worker.getOutputStream(), true);
				PrintStream out = new PrintStream(worker.getOutputStream(), true, "UTF-8");
				
				worker.setSoTimeout(dataTransferTimeOut);

				X.logs("MASTER: Got connection from InferNet instance %s [hostname=%s]", worker, hostname);
				String cmd = in.readLine();
				
				if (cmd.equals("INIT")) { //Request for a initial graph generation data
					X.logs("Initial data request");
					String initData = wrapper.getInitializationData();
					out.println(initData);
					
				} else if (cmd.startsWith("UPDATE")) { //Request to run the client analysis for factor graph update
					X.logs("Update request");
					String[] tokens = Utils.split(cmd, " ", false, false, 2);
					String updateData = wrapper.getUpdateData(tokens[1]);
					out.println(updateData);
					
				} else if (cmd.startsWith("EXIT")) {
					
					exitFlag = true;
					X.logs("Going to exit...");
					
				}
				in.close();
				out.close();
				
			} catch(SocketTimeoutException e) {
				X.logs("Socket read from InferNet instance timed out. Exiting..");
				exitFlag = true;
			} catch(IOException e) {
				X.logs("Some error in socket comm with Infernet instance. Exiting..");
				exitFlag = true;
			}
		}
		return;
	}

}
