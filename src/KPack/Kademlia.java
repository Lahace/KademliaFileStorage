package KPack;

import KPack.Files.KadFile;
import KPack.Files.KadFileList;
import KPack.Packets.*;
import KPack.Tree.Bucket;
import KPack.Tree.RoutingTree;

import java.io.*;

import java.math.BigInteger;
import java.net.*;
import java.util.*;

public class Kademlia implements KademliaInterf {

    private static boolean instance = false;
    public final static int BITID = 8;
    public final static int K = 4;
    public final static int ALPHA = 2;
    public final static String FILESPATH = "./storedFiles/";
    public KadFileList fileList;
    private BigInteger nodeID;
    private RoutingTree routingTree;
    private KadNode thisNode;
    public short UDPPort = 1337;

    private final int pingTimeout = 15000;

    public Kademlia()
    {
        if (instance) return; //Aggiungere un'eccezione tipo AlreadyInstanced
        instance = true;

        //Gestione File

        File temp = new File(FILESPATH);
        if(!(temp.exists())) temp.mkdir();

        File localFiles = new File(FILESPATH + "index");
        fileList = new KadFileList();

        if(localFiles.exists())
        {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(FILESPATH + "index");
                ObjectInputStream ois = new ObjectInputStream(fis);
                while(true)
                {
                    fileList = ((KadFileList)ois.readObject());
                }
            }
            catch (EOFException | FileNotFoundException | ClassNotFoundException e)
            {
                //Aspettate o impossibili
            }
            catch(IOException ioe)
            {
                ioe.printStackTrace();
            }
            finally
            {
                try { if (fis != null) fis.close(); }
                catch (IOException ioe) {} //Ignorata
            }
        }
        else
        {
            try
            {
                localFiles.createNewFile();
            }
            catch (IOException ioe)
            {
                ioe.printStackTrace();
            }
        }

        String myIP = getIP().getHostAddress().toString();

        boolean exists = true;
        do
        {
            nodeID = new BigInteger(BITID, new Random());
            //Controllare se esiste
            //TODO
            exists = false;
        }
        while (exists);

        thisNode = new KadNode(myIP, UDPPort, nodeID);
        routingTree = new RoutingTree(this);
        routingTree.add(thisNode); //Mi aggiungo


        new Thread(new ListenerThread()).start();
    }
    /*
    public InetAddress getIP()   //per il momento restituisce l'ip locale.
    {
        try
        {
            return InetAddress.getByName(InetAddress.getLocalHost().getHostAddress());
        }
        catch (UnknownHostException ex)
        {
            ex.printStackTrace();
            /////////////// DA GESTIRE
        }
        return null;
    }
    */

    public InetAddress getIP()
    {
        String publicIP = null;
        try {
            URL urlForIP = new URL("https://api.ipify.org/");
            BufferedReader in = new BufferedReader(new InputStreamReader(urlForIP.openStream()));

            publicIP = in.readLine(); //IP as a String
        }
        catch (MalformedURLException mue)
        {
            mue.printStackTrace();
            /////////////// DA GESTIRE
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
        try
        {
            return InetAddress.getByName(publicIP);
        }
        catch(UnknownHostException e)
        {
            e.printStackTrace();
            //DA GESTIRE
            return null;
        }
    }

    public BigInteger getNodeID()
    {
        return nodeID;
    }

    public boolean ping(KadNode node)
    {
        PingRequest pr = new PingRequest(thisNode,node);
        try
        {
            Socket s = new Socket(node.getIp(), node.getUDPPort());
            s.setSoTimeout(pingTimeout);

            OutputStream os = s.getOutputStream();
            ObjectOutputStream outputStream = new ObjectOutputStream(os);
            outputStream.writeObject(pr);
            outputStream.flush();

            InputStream is = s.getInputStream();
            ObjectInputStream inputStream = new ObjectInputStream(is);

            long timeInit = System.currentTimeMillis();
            boolean state = true;
            while(true)
            {
                try
                {
                    Object preply = inputStream.readObject();
                    if(preply instanceof PingReply)
                    {
                        if(((PingReply)preply).getSourceKadNode().equals(pr.getDestKadNode()))
                        {
                            is.close();
                            s.close();
                            return true;
                        }
                    }
                    s.setSoTimeout(((int)(pingTimeout-(System.currentTimeMillis()-timeInit))));
                }
                catch(ClassNotFoundException e)
                {
                    e.printStackTrace();
                }
            }
        }
        catch(SocketTimeoutException soe)
        {
            System.out.println("Timeout");
            return false;
        }
        catch(ConnectException soe)
        {
            System.out.println("Non c'è risposta");
            return false;
        }
        catch (EOFException e)
        {
            return false;
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return false;
        }
    }

    public Object findValue(BigInteger fileID)
    {
        return null;
    }

    public List<KadNode> findNode(BigInteger targetID)  //Working in progress
    {
        Bucket bucket=routingTree.findNodesBucket(thisNode);
        List<KadNode> lkn=new ArrayList<>();
        Iterator<KadNode> ikn=bucket.getList();
        while(ikn.hasNext())
        {
            lkn.add(ikn.next());
        }
        lkn.sort((o1, o2) ->
                distanza(o1, new KadNode(null, (short) 0,targetID)).compareTo(distanza(o2,new KadNode(null, (short) 0,targetID))));
        List<KadNode> alphaNode=lkn.subList(0,ALPHA-1);
        List<KadNode> list=new ArrayList<>();
        alphaNode.forEach((o1)->list.addAll(findNode(o1.getNodeID())));
        if(list.subList(0,ALPHA-1).containsAll(alphaNode))
            return list.subList(0,K-1);
        return null;
    }

    public KadFileList getFileList()
    {
        return fileList;
    }

    public KadNode getMyNode()
    {
        return thisNode;
    }

    public void store(KadNode node, KadFile file) //gestire eccezioni
    {

    }

    private class ListenerThread implements Runnable {

        private ServerSocket listener;

        @Override
        public void run()
        {
            try
            {
                listener = new ServerSocket(UDPPort);
                System.out.println("Thread Server avviato\n" + "IP: " + getIP() + "\nPorta: " + UDPPort);
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
                ////////// DA GESTIRE
            }

            Socket connection;
            while (true)
            {

                try
                {
                    System.out.println("Waiting for connection");

                    connection = listener.accept();
                    System.out.println("Connection received from " + connection.getInetAddress().getHostAddress());

                    //Analizzo la richiesta ricevuta
                    InputStream is = connection.getInputStream();
                    ObjectInputStream inStream = new ObjectInputStream(is);

                    Object received = inStream.readObject();

                    //Elaboro la risposta
                    /*
                    if (received instanceof PingReply)
                    {
                        PingReply pr = (PingReply) received;
                        System.out.println("Received PingReply from: " + pr.toString());
                        KadNode kn = pr.getKadNode();

                        synchronized (pendentPing)
                        {
                            if (pendentPing.contains(kn));
                            {
                                pendentPing.remove(kn);
                                notifyAll();
                            }
                        }
                    }
                    */
                    if(received instanceof FindNodeRequest)
                    {

                    }
                    else if(received instanceof FindValueRequest)
                    {

                    }
                    else if (received instanceof StoreRequest)
                    {
                        StoreRequest rq = (StoreRequest) received;

                    }
                    else if(received instanceof DeleteRequest)
                    {

                    }
                    else if (received instanceof PingRequest)
                    {
                        PingRequest pr = (PingRequest) received;
                        if(!(pr.getDestKadNode().equals(thisNode)))
                        {
                            connection.close();
                            continue;
                        }
                        KadNode sourceKadNode = pr.getSourceKadNode();

                        System.out.println("Received PingRequest from: " + pr.getSourceKadNode().toString());

                        PingReply reply = new PingReply(thisNode, sourceKadNode);

                        OutputStream os = connection.getOutputStream();
                        ObjectOutputStream outputStream = new ObjectOutputStream(os);
                        outputStream.writeObject(reply);
                        outputStream.flush();

                        os.close();
                    }

                    connection.close();
                }
                catch (IOException ex)
                {
                    ex.printStackTrace();
                    ////// GESTIREE
                }
                catch (ClassNotFoundException ex)
                {
                    ex.printStackTrace();
                    //// GESTIREEEEE
                }
            }
        }
    }

    public static BigInteger distanza(KadNode o1,KadNode o2)
    {
        return o1.getNodeID().xor(o2.getNodeID());
    }
}
