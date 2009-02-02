package de.fu_berlin.inf.dpp.net.jingle;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jivesoftware.smackx.jingle.JingleSession;
import org.jivesoftware.smackx.jingle.media.JingleMediaSession;
import org.jivesoftware.smackx.jingle.media.PayloadType;
import org.jivesoftware.smackx.jingle.nat.TransportCandidate;
import org.limewire.nio.NIODispatcher;
import org.limewire.rudp.DefaultRUDPContext;
import org.limewire.rudp.DefaultRUDPSettings;
import org.limewire.rudp.DefaultUDPService;
import org.limewire.rudp.RudpMessageDispatcher;
import org.limewire.rudp.UDPMultiplexor;
import org.limewire.rudp.UDPSelectorProvider;
import org.limewire.rudp.messages.RUDPMessageFactory;
import org.limewire.rudp.messages.impl.DefaultMessageFactory;

import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.net.jingle.JingleFileTransferData.FileTransferType;

/**
 * This class implements a file transfer session with jingle.
 * 
 * Jingle is a XMPP-extension with id XEP-0166. Documentation can be found at
 * http://xmpp.org/extensions/xep-0166.html .
 * 
 * This implementation uses TCP as transport protocol which fall back to UDP
 * when a TCP connection failed. To ensure no data loss when transmitting with
 * UDP the RUDP implementation from the Limewire project are used.
 * 
 * Documentation for the RUDP component from limewire can be found at:
 * http://wiki.limewire.org/index.php?title=Javadocs .
 * 
 * @author chjacob
 * 
 */
public class JingleFileTransferSession extends JingleMediaSession {

    private class Receive extends Thread {

        private ObjectInputStream input;

        public Receive(ObjectInputStream ii) {
            this.input = ii;
        }

        public void run() {
            try {

                while (true) {
                    logger.debug("waiting on port " + localPort);

                    /* get number of file to be transfer. */
                    int fileNumber;

                    fileNumber = input.readInt();
                    logger.debug("incoming file number: " + fileNumber);

                    for (int i = 0; i < fileNumber; i++) {

                        /* receive file data */
                        JingleFileTransferData data = (JingleFileTransferData) input
                                .readObject();

                        if (data.type == FileTransferType.FILELIST_TRANSFER) {
                            logger.debug("received file List");
                            logger.debug(data.file_list_content);
                            /* inform listener. */
                            for (IJingleFileTransferListener listener : listeners) {
                                listener.incomingFileList(
                                        data.file_list_content, data.sender);
                            }

                        } else if (data.type == FileTransferType.RESOURCE_TRANSFER) {
                            logger.debug("received resource "
                                    + data.file_project_path);
                            for (IJingleFileTransferListener listener : listeners) {
                                listener.incomingResourceFile(data,
                                        new ByteArrayInputStream(data.content));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                logger.info("receive-thread interrupted");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

        }
    }

    private static Logger logger = Logger
            .getLogger(JingleFileTransferSession.class);

    private Receive tcpReceiveThread;
    private Receive udpReceiveThread;
    private JingleFileTransferData[] transferList;
    private Set<IJingleFileTransferListener> listeners;
    private UDPSelectorProvider udpSelectorProvider;
    private Socket udpSocket;
    private Socket tcpSocket;
    private ObjectOutputStream tcpObjectOutputStream;
    private ObjectOutputStream udpObjectOutputStream;
    private ObjectInputStream tcpObjectInputStream;
    private ObjectInputStream udpObjectInputStream;
    private JID remoteJid;
    private String ip;
    private String localIp;
    private int localPort;
    private int remotePort;

    /**
     * TODO CJ: write javadoc
     * 
     * @param payloadType
     * @param remote
     * @param local
     * @param mediaLocator
     * @param jingleSession
     * @param transferData
     * @param listeners
     */
    public JingleFileTransferSession(PayloadType payloadType,
            TransportCandidate remote, TransportCandidate local,
            String mediaLocator, JingleSession jingleSession,
            JingleFileTransferData[] transferData, JID remoteJid,
            Set<IJingleFileTransferListener> listeners) {
        super(payloadType, remote, local, mediaLocator, jingleSession);

        this.remoteJid = remoteJid;
        this.transferList = transferData;
        this.listeners = listeners;
        logger.debug("JingleFileTransferSesseion created " + local.getIp()
                + ":" + local.getPort() + " <-> " + remote.getIp() + ":"
                + remote.getPort());
        initialize();
    }

    /**
     * Initialization of the session. It tries to create sockets for both, TCP
     * and UDP. The UDP Socket is a reliable implementation from the Limewire
     * project. Documentation can be found at http://wiki.limewire.org.
     */
    @Override
    public void initialize() {

        if (this.getLocal().getSymmetric() != null) {
            ip = this.getLocal().getIp();
            localIp = this.getLocal().getLocalIp();
            localPort = getFreePort();
            remotePort = this.getLocal().getSymmetric().getPort();

            logger.debug(this.getLocal().getConnection() + " " + ip + ": "
                    + localPort + "->" + remotePort);

        } else {
            ip = this.getRemote().getIp();
            localIp = this.getLocal().getLocalIp();
            localPort = this.getLocal().getPort();
            remotePort = this.getRemote().getPort();
        }

        // create RUDP service
        RudpMessageDispatcher dispatcher = new RudpMessageDispatcher();
        DefaultUDPService service = new DefaultUDPService(dispatcher);
        RUDPMessageFactory factory = new DefaultMessageFactory();
        udpSelectorProvider = new UDPSelectorProvider(new DefaultRUDPContext(
                factory, NIODispatcher.instance().getTransportListener(),
                service, new DefaultRUDPSettings()));
        UDPMultiplexor udpMultiplexor = udpSelectorProvider.openSelector();
        dispatcher.setUDPMultiplexor(udpMultiplexor);
        NIODispatcher.instance().registerSelector(udpMultiplexor,
                udpSelectorProvider.getUDPSocketChannelClass());
        try {
            service.start(localPort);
        } catch (IOException e) {
            logger.debug("Failed to create RUDP service");
        }

        // server side
        if (getJingleSession().getInitiator().equals(
                getJingleSession().getConnection().getUser())) {

            // create TCP Socket and listen
            Thread createTcpSocket = new Thread(new Runnable() {
                public void run() {
                    try {
                        ServerSocket serverSocket = new ServerSocket(localPort);
                        serverSocket.setSoTimeout(0);
                        JingleFileTransferSession.this.tcpSocket = serverSocket
                                .accept();
                        JingleFileTransferSession.this.tcpObjectOutputStream = new ObjectOutputStream(
                                tcpSocket.getOutputStream());
                        JingleFileTransferSession.this.tcpObjectInputStream = new ObjectInputStream(
                                tcpSocket.getInputStream());
                        informListenersAboutConnection("TCP");
                    } catch (IOException e) {
                        logger.debug("Failed to listen with TCP");
                    }
                }
            });
            createTcpSocket.start();

            Thread createUdpSocket = new Thread(new Runnable() {
                public void run() {
                    try {
                        Socket usock = udpSelectorProvider
                                .openAcceptorSocketChannel().socket();
                        usock.setSoTimeout(0);
                        usock.connect(new InetSocketAddress(InetAddress
                                .getByName(ip), remotePort));
                        usock.setKeepAlive(true);
                        JingleFileTransferSession.this.udpSocket = usock;
                        JingleFileTransferSession.this.udpObjectOutputStream = new ObjectOutputStream(
                                udpSocket.getOutputStream());
                        JingleFileTransferSession.this.udpObjectInputStream = new ObjectInputStream(
                                udpSocket.getInputStream());
                        informListenersAboutConnection("UDP");
                    } catch (IOException e) {
                        logger.debug("Failed to listen with UDP");
                    }
                }
            });
            createUdpSocket.start();

            try { // give client a little time to connect
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                // do nothing
            }

        } else { // client side
            try { // give server a little time to come up
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // do nothing
            }
            try { // to create a tcp socket
                this.tcpSocket = new Socket(ip, remotePort);
                this.tcpObjectOutputStream = new ObjectOutputStream(tcpSocket
                        .getOutputStream());
                this.tcpObjectInputStream = new ObjectInputStream(tcpSocket
                        .getInputStream());
                logger.debug("successfully connected with TCP");
                informListenersAboutConnection("TCP");
                logger.debug("JingleFileTransferSesseion initialized");
                return;

            } catch (UnknownHostException e) {
                logger.debug("Invalid IP-address of jingle remote (TCP)");
            } catch (IOException e) {
                logger.debug("Failed to connect with TCP");
            }

            try { // to create a udp socket

                Socket usock = udpSelectorProvider.openSocketChannel().socket();
                usock.setSoTimeout(0);
                usock.setKeepAlive(true);
                usock.connect(new InetSocketAddress(InetAddress.getByName(ip),
                        remotePort));
                this.udpSocket = usock;
                this.udpObjectOutputStream = new ObjectOutputStream(udpSocket
                        .getOutputStream());
                this.udpObjectInputStream = new ObjectInputStream(udpSocket
                        .getInputStream());
                logger.debug("successfully connected with UDP");
                informListenersAboutConnection("UDP");
                logger.debug("JingleFileTransferSesseion initialized");
            } catch (UnknownHostException e1) {
                logger.debug("Invalid IP-address of jingle remote (UDP)");
            } catch (IOException e1) {
                logger.debug("Failed to connect with UDP");
            }
        }
    }

    private void informListenersAboutConnection(String protocol) {
        for (IJingleFileTransferListener listener : listeners) {
            listener.connected(protocol, ip);
        }
    }

    /**
     * This method is called from the JingleFileTransferManager to send files
     * with this session. This method tries to transmit the files with TCP. When
     * this fails it tries to send the files with UDP/RUDP.
     * 
     * @throws JingleSessionException
     */
    public void sendFiles(JingleFileTransferData[] transferData)
            throws JingleSessionException {

        this.transferList = transferData;

        if (tcpSocket != null) {
            logger.debug("sending with TCP to " + ip + ":" + remotePort);
            try {
                logger.debug("sending with TCP..");
                transmit(tcpObjectOutputStream);
                return;
            } catch (IOException e) {
                logger.debug("sending with TCP failed, use UDP instead..", e);
            }
        }
        if (udpSocket != null) {
            logger.debug("sending with UDP to " + ip + ":" + remotePort);
            try {
                logger.debug("sending with UDP..");
                transmit(udpObjectOutputStream);
                return;
            } catch (IOException e) {
                logger.debug("sending with UDP failed, use IBB instead..", e);
            }
        }
        throw new JingleSessionException("Failed to send files with Jingle");
    }

    /**
     * This method is called from Jingle when a jingle session is established.
     * Two threads are started, one for receiving with TCP, the other for
     * receiving with UDP/RUDP.
     */
    @Override
    public void startReceive() {

        logger.debug("start receiving");

        if (tcpSocket != null && tcpObjectInputStream != null) {
            this.tcpReceiveThread = new Receive(tcpObjectInputStream);
            this.tcpReceiveThread.start();
        }

        if (udpSocket != null && udpObjectInputStream != null) {
            this.udpReceiveThread = new Receive(udpObjectInputStream);
            this.udpReceiveThread.start();
        }
    }

    /**
     * This method is called from Jingle when a jingle session is established.
     * This method tries to transmit the files with TCP. When this fails it
     * tries to send the files with UDP/RUDP.
     */
    @Override
    public void startTrasmit() {
        logger.debug("JingleFileTransferSesseion: start transmitting");

        if (transferList == null)
            return;

        if (tcpSocket != null) {
            try {
                logger.debug("sending with TCP..");
                transmit(tcpObjectOutputStream);
                return;
            } catch (IOException e) {
                logger.debug("sending with TCP failed, use UDP instead..", e);
            }
        }
        if (udpSocket != null) {
            try {
                logger.debug("sending with UDP..");
                transmit(udpObjectOutputStream);
                return;
            } catch (IOException e) {
                logger.warn("sending with UDP failed, use UDP instead..", e);
            }
        }
        if (transferList.length > 0) {
            for (IJingleFileTransferListener listener : listeners) {
                listener.failedToSendFileListWithJingle(remoteJid,
                        transferList[0]);
            }
        }
    }

    private synchronized void transmit(ObjectOutputStream oo)
            throws IOException {
        assert (oo != null);

        oo.writeInt(transferList.length);
        oo.flush();
        logger.debug("sent transfer number : " + transferList.length);

        for (JingleFileTransferData data : transferList) {

            /* send data */
            oo.writeObject(data);
            oo.flush();
            logger.debug("sent data for : " + data.file_project_path);

        }
        transferList = null;
    }

    @Override
    public void stopReceive() {
        logger.debug("JingleFileTransferSesseion: stop receiving");
        if (tcpReceiveThread != null)
            tcpReceiveThread.interrupt();
        if (udpReceiveThread != null)
            udpReceiveThread.interrupt();
    }

    @Override
    public void stopTrasmit() {
        logger.debug("JingleFileTransferSesseion: stop transmitting");
        try {
            if (tcpSocket != null) {
                tcpObjectOutputStream.close();
                tcpObjectInputStream.close();
                tcpSocket.close();
            }
            if (udpSocket != null) {
                udpObjectOutputStream.close();
                udpObjectInputStream.close();
                udpSocket.close();
            }
        } catch (IOException e) {
            logger.debug("Failed to close all sockets");
        }
    }

    @Override
    public void setTrasmit(boolean active) {
        logger.debug("JingleFileTransferSesseion: set transmit to " + active);
        // TODO CJ: What have to do here?
    }

    /**
     * Obtain a free port we can use.
     * 
     * @return A free port number.
     */
    protected int getFreePort() {
        ServerSocket ss;
        int freePort = 0;

        for (int i = 0; i < 10; i++) {
            freePort = (int) (10000 + Math.round(Math.random() * 10000));
            freePort = freePort % 2 == 0 ? freePort : freePort + 1;
            try {
                ss = new ServerSocket(freePort);
                freePort = ss.getLocalPort();
                ss.close();
                return freePort;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            ss = new ServerSocket(0);
            freePort = ss.getLocalPort();
            ss.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return freePort;
    }
}
