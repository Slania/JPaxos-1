package lsr.paxos.test.directory;

import java.io.*;
import java.net.*;
import java.util.*;

public class SetupNetworkDelays {

    private final Properties configuration = new Properties();

    public static void main(String args[]) throws IOException, InterruptedException {
        new SetupNetworkDelays().setup();
    }

    private void setup() throws IOException, InterruptedException {
        loadPropertiesFile();
        String localHostname = getLocalHostname();
        StringTokenizer hostNameTokenizer = new StringTokenizer(localHostname, ".");
        String me = "";
        while (hostNameTokenizer.hasMoreElements()) {
            me = (String) hostNameTokenizer.nextElement();
            break;
        }
        ArrayList<String> othersAre = new ArrayList<String>();
        StringTokenizer replicas = new StringTokenizer(configuration.getProperty("replicas"), ",");
        while (replicas.hasMoreElements()) {
            String machineName = (String) replicas.nextElement();
            if (!me.equals(machineName)) {
                othersAre.add(machineName);
            }
        }

        Network network = new Network();

        ProcessBuilder builder = new ProcessBuilder("ipfw flush");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        process.waitFor();
        builder = new ProcessBuilder("ipfw pipe flush");
        builder.redirectErrorStream(true);
        process = builder.start();
        process.waitFor();

        Integer ruleNumber = 100;
        Integer pipeNumber = 1;
        for (String otherReplica : othersAre) {
            String delay = network.delayBetweenNodes(me, otherReplica);
            System.out.println("ipfw add " + ruleNumber + " pipe " + pipeNumber + " ip from " + me + " to " + otherReplica);
            System.out.println("ipfw pipe " + pipeNumber + " config delay " + (Integer.valueOf(delay)/2) + "ms");
            ruleNumber++;
            pipeNumber++;
        }

    }

    private void loadPropertiesFile() {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream("network.properties");
            configuration.load(fis);
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private String getLocalHostname() throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("hostname");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        process.waitFor();
        InputStream inputStream = process.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.readLine();
    }

    private String nsLookUp(String hostname) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(hostname);
        return address.getHostAddress();
    }

    private String getLocalAddress() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface current = interfaces.nextElement();
            System.out.println(current);
            if (!current.isUp() || current.isLoopback() || current.isVirtual()) continue;
            Enumeration<InetAddress> addresses = current.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress current_addr = addresses.nextElement();
                if (current_addr.isLoopbackAddress()) continue;
                if (current_addr instanceof Inet4Address) {
                    return current_addr.getHostAddress();
                }
            }
        }
        return "";
    }

    private class Cluster implements Comparable {

        String name;
        List<String> replicas = new ArrayList<String>();

        private Cluster(String name, List<String> replicas) {
            this.name = name;
            this.replicas = replicas;
        }

        public String getName() {
            return name;
        }

        public List<String> getReplicas() {
            return replicas;
        }

        public boolean nodeBelongsToCluster(String node) {
            return replicas.contains(node);
        }

        @Override
        public int compareTo(Object o) {
            return name.compareTo(((Cluster) o).getName());
        }
    }

    private class Network {

        private List<Cluster> clusters;
        private final Properties configuration = new Properties();

        private void loadPropertiesFile() {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream("network.properties");
                configuration.load(fis);
                fis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void loadNetworkConfiguration() {
            loadPropertiesFile();
            String clusterNames = configuration.getProperty("clusters");
            StringTokenizer clusterNameTokenizer = new StringTokenizer(clusterNames, ",");
            while (clusterNameTokenizer.hasMoreElements()) {
                String clusterName = (String) clusterNameTokenizer.nextElement();
                String clusterMembers = configuration.getProperty(clusterName);
                StringTokenizer clusterMemberTokenizer = new StringTokenizer(clusterMembers, ",");
                ArrayList<String> clusterReplicas = new ArrayList<String>();
                while (clusterMemberTokenizer.hasMoreElements()) {
                    String clusterMember = (String) clusterMemberTokenizer.nextElement();
                    clusterReplicas.add(clusterMember + "." + configuration.getProperty("experiment.name") + "." + configuration.getProperty("project.name") + ".kodiak.nx");
                }
                clusters.add(new Cluster(clusterName, clusterReplicas));
            }
        }

        private String delayBetweenNodes(String nodeA, String nodeB) {
            Cluster nodeACluster = null;
            Cluster nodeBCluster = null;
            for (Cluster cluster : clusters) {
                nodeACluster = cluster.nodeBelongsToCluster(nodeA) ? cluster : nodeACluster;
                nodeBCluster = cluster.nodeBelongsToCluster(nodeB) ? cluster : nodeBCluster;
                if (nodeACluster != null && nodeBCluster != null) {
                    break;
                }
            }
            String delay = null;
            if (nodeACluster.compareTo(nodeBCluster) < 0) {
                delay = configuration.getProperty(nodeACluster.getName() + "." + nodeBCluster.getName() + ".interDelay");
            } else if (nodeACluster.compareTo(nodeBCluster) == 0) {
                delay = configuration.getProperty(nodeACluster.getName() + ".intraDelay");
            } else {
                delay = configuration.getProperty(nodeBCluster.getName() + "." + nodeACluster.getName());
            }
            return delay;
        }
    }

}
