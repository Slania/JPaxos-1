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

        ProcessBuilder builder = new ProcessBuilder("/usr/local/bin/bash", "-c", "echo y | sudo ipfw flush");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        process.waitFor();
        builder = new ProcessBuilder("/usr/local/bin/bash", "-c", "echo y | sudo ipfw pipe flush");
        builder.redirectErrorStream(true);
        process = builder.start();
        process.waitFor();

        Integer ruleNumber = 100;
        Integer pipeNumber = 1;
        for (String otherReplica : othersAre) {
            String delay = network.delayBetweenNodes(me, otherReplica);
            String myPorts = configuration.getProperty(me + ".ports");
            String otherReplicaPorts = configuration.getProperty(otherReplica + ".ports");

            List<String> allMyPorts = new ArrayList<String>();
            List<String> allOtherReplicaPorts = new ArrayList<String>();

            StringTokenizer portTokenizer = new StringTokenizer(myPorts, ":");
            while (portTokenizer.hasMoreElements()) {
                allMyPorts.add((String) portTokenizer.nextElement());
            }

            portTokenizer = new StringTokenizer(otherReplicaPorts, ":");
            while (portTokenizer.hasMoreElements()) {
                allOtherReplicaPorts.add((String) portTokenizer.nextElement());
            }

            for (String destPort : allOtherReplicaPorts) {
                for (String srcPort : allMyPorts) {
                    String addPipeCommand = "sudo ipfw add " + ruleNumber + " pipe " + pipeNumber + " ip from " + nsLookUp(getFullName(me)) + " to " + nsLookUp(getFullName(otherReplica)) + " src-port " + srcPort + " dst-port " + destPort;
                    String addPipeCommandForFrags = "sudo ipfw add " + ruleNumber + " pipe " + pipeNumber + " ip from " + nsLookUp(getFullName(me)) + " to " + nsLookUp(getFullName(otherReplica)) + " frag";

                    if(Integer.valueOf(delay) >= 0) {
                        String modifyPipeDelayCommand = "sudo ipfw pipe " + pipeNumber + " config delay " + (Integer.valueOf(delay)/2) + "ms";

                        System.out.println(addPipeCommand);
                        System.out.println(modifyPipeDelayCommand);

                        builder = new ProcessBuilder("/usr/local/bin/bash", "-c", addPipeCommand);
                        builder.redirectErrorStream(true);
                        process = builder.start();
                        process.waitFor();

                        ruleNumber++;

                        builder = new ProcessBuilder("/usr/local/bin/bash", "-c", addPipeCommandForFrags);
                        builder.redirectErrorStream(true);
                        process = builder.start();
                        process.waitFor();

                        builder = new ProcessBuilder("/usr/local/bin/bash", "-c", modifyPipeDelayCommand);
                        builder.redirectErrorStream(true);
                        process = builder.start();
                        process.waitFor();

                        ruleNumber++;
                        pipeNumber++;

                    } else {
                        System.out.println("Delay numbers must be non-negative!");
                    }
                }
            }
        }
    }

    private String getFullName(String node) {
        return node + "." + configuration.getProperty("experiment.name") + "." + configuration.getProperty("project.name") + ".kodiak.nx";
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

        private List<Cluster> clusters = new ArrayList<Cluster>();
        private final Properties configuration = new Properties();

        public Network() {
            loadNetworkConfiguration();
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
                    clusterReplicas.add(clusterMember);
                }
                clusters.add(new Cluster(clusterName, clusterReplicas));
            }

            for (Cluster cluster : clusters) {
                System.out.println(cluster.getName() + ": " + cluster.getReplicas());
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
                delay = configuration.getProperty(nodeBCluster.getName() + "." + nodeACluster.getName() + ".interDelay");
            }
            return delay;
        }
    }

}
