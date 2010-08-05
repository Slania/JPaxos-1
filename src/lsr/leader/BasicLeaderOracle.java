package lsr.leader;

import java.util.BitSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import lsr.common.Handler;
import lsr.common.ProcessDescriptor;
import lsr.common.SingleThreadDispatcher;
import lsr.leader.messages.SimpleAlive;
import lsr.leader.messages.Start;
import lsr.paxos.messages.Message;
import lsr.paxos.messages.MessageType;
import lsr.paxos.network.MessageHandler;
import lsr.paxos.network.Network;

/**
 * The basic implementation of a leader oracle. This implementation ignores
 * latencies.
 * 
 * @author Donz? Benjamin
 */
public class BasicLeaderOracle implements LeaderOracle {
	/** Upper bound of the transmission of a message */
	public final String DELTA = "delta";
	private final int delta;
	private final static int DEFAULT_DELTA = 1000;

	private final Network network;
	private final CopyOnWriteArrayList<LeaderOracleListener> listeners;

	private final ProcessDescriptor p;
	private final int N;

	/** Receives notifications of messages from the network class */
	private InnerMessageHandler innerHandler;

	/** The current view. The leader is (view % N) */
	int view = -1;

	/** Executed when the timeout on the leader expires. 3 delta time */
	private ScheduledFuture<SuspectLeaderTask> suspectTask = null;
	private SingleThreadDispatcher executor;

	/**
	 * Initializes new instance of <code>LeaderElector</code>.
	 * 
	 * @param network
	 *            - used to send and receive messages
	 * @param localID
	 *            - the id of this process
	 * @param N
	 *            - the total number of process
	 * @param loConfPath
	 *            - the path of the configuration file
	 */
	public BasicLeaderOracle(ProcessDescriptor p, Network network,
			SingleThreadDispatcher executor) {
		this.p = p;
		this.network = network;
		this.executor = executor;
		this.delta = p.config.getIntProperty("delta", DEFAULT_DELTA);
		this.N = p.config.getN();
		this.innerHandler = new InnerMessageHandler();
		this.listeners = new CopyOnWriteArrayList<LeaderOracleListener>();

		_logger.info("[p" + p.localID + "] Configuration: DELTA=" + delta);
	}

	public void start() throws Exception {
		executor.executeAndWait(new Runnable() {
			public void run() {
				onStart();
			}
		});
	}

	public void stop() throws Exception {
		executor.executeAndWait(new Runnable() {
			public void run() {
				onStop();
			}
		});
	}

	private void onStart() {
		executor.checkInDispatcher();
		// Register interest in receiving network messages
		network.addMessageListener(MessageType.SimpleAlive, innerHandler);
		network.addMessageListener(MessageType.Start, innerHandler);

		if (view != -1) {
			throw new RuntimeException("Already started");
		}

		view = 1;

		// Initiate the first view
		startRound(view);
		_logger.info("Leader oracle started");
	}

	private void onStop() {
		executor.checkInDispatcher();
		// remove the process from the message listener.
		network.removeMessageListener(MessageType.SimpleAlive, innerHandler);
		network.removeMessageListener(MessageType.Start, innerHandler);

		if (suspectTask != null) {
			suspectTask.cancel(true);
			suspectTask = null;
		}

		// schedule the stop of the latencyDetector in order to avoid
		// concurrency problem
		view = -1;
		_logger.info("Leader oracle stopped");
	}

	private void startRound(int round) {
		startRound(round, delta);
	}

	// Start round with a defined value for reseting the timer
	private void startRound(int round, int rstTimerVal) {
		view = round;
		int leader = view % N;

		// inform every listener of the change of leader
		for (LeaderOracleListener loListener : listeners) {
			loListener.onNewLeaderElected(leader);
		}
		_logger.info("New view: " + round + " leader: " + leader);

		resetTimer(rstTimerVal);

		Start startMsg = new Start(view);
		network.sendMessage(startMsg, leader);

		if (leader == p.localID) {
			_logger.fine("I'm leader now.");
			sendAlives();
		}
	}

	private void sendAlives() {
		resetTimer(0);

		SimpleAlive aliveMsg = new SimpleAlive(view);
		// Destination all except me
		BitSet destination = new BitSet(N);
		destination.set(0, N);
		destination.clear(p.localID);
		network.sendMessage(aliveMsg, destination);
	}

	private void onAliveMessage(SimpleAlive msg, int sender) {
		int msgRound = msg.getView();
		if (msgRound < view) {
			network.sendMessage(new Start(view), sender);
		} else if (msgRound == view) {
			resetTimer(0);
		} else {
			_logger.finer("Alive with higher view");
			startRound(msgRound, 0);
		}
	}

	private void onStartMessage(Start msg, int sender) {
		// _logger.finer("Received start view: " + msg.getView() + " from: " +
		// sender);
		if (msg.getView() > view) {
			startRound(msg.getView());
			assert isLeader() : "I'm not leader for round of START message: "
					+ msg;
		}
	}

	final class SuspectLeaderTask implements Runnable {
		public void run() {
			if (!isLeader()) {
				_logger.info("Suspecting leader: " + getLeader());
				startRound(view + 1);
			}
		}
	}

	final class InnerMessageHandler implements MessageHandler {
		public void onMessageReceived(final Message msg, final int sender) {
			// Execute on the dispatcher thread.
			executor.execute(new Handler() {
				@Override
				public void handle() {
					switch (msg.getType()) {
					case SimpleAlive:
						onAliveMessage((SimpleAlive) msg, sender);
						break;
					case Start:
						onStartMessage((Start) msg, sender);
						break;
					default:
						_logger.severe("Wrong message type received!!!");
						System.exit(1);
						break;
					}

				}
			});
		}

		public void onMessageSent(Message message, BitSet destinations) {
			// Empty
		}
	}

	public int getLeader() {
		return view % N;
	}

	public boolean isLeader() {
		return getLeader() == p.localID;
	}

	public void registerLeaderOracleListener(LeaderOracleListener listener) {
		listeners.addIfAbsent(listener);
	}

	public void removeLeaderOracleListener(LeaderOracleListener listener) {
		listeners.remove(listener);
	}

	// Reset the timer, cancel and reschedule task later
	// argument startTime correspond to the starting time of the timer
	@SuppressWarnings("unchecked")
	private void resetTimer(int startTime) {
		if (suspectTask != null) {
			suspectTask.cancel(false);
		}

		suspectTask = (ScheduledFuture<SuspectLeaderTask>) executor.schedule(
				new SuspectLeaderTask(), 3 * delta - startTime,
				TimeUnit.MILLISECONDS);
	}

	// /** Sanity checks */
	// private final void checkIsInExecutorThread() {
	// assert Thread.currentThread().getName().equals(LO_THREAD_NAME);
	// }

	private final static Logger _logger = Logger
			.getLogger(BasicLeaderOracle.class.getCanonicalName());

	public int getDelta() {
		return delta;
	}
}
