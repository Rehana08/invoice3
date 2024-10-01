package org.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;

public class BpmnPathFinder {

    private static final String API_URL = "https://n35ro2ic4d.execute-api.eu-central-1.amazonaws.com/prod/engine-rest/process-definition/key/invoice/xml";

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Please provide start and end node IDs as arguments.");
            System.exit(-1);
        }

        String startId = args[0];
        String endId = args[1];

        try {
            // Fetch BPMN XML from remote server
            String bpmnXml = fetchBpmnXml();

            if (bpmnXml == null || bpmnXml.isEmpty()) {
                System.err.println("Failed to fetch BPMN XML.");
                System.exit(-1);
            }

            // Parse the XML into a traversable data structure
            BpmnModelInstance modelInstance = Bpmn.readModelFromStream(new ByteArrayInputStream(bpmnXml.getBytes("UTF-8")));

            // Get all flow nodes
            Collection<FlowNode> flowNodes = modelInstance.getModelElementsByType(FlowNode.class);

            // Create a map of node IDs to FlowNode objects
            Map<String, FlowNode> nodeMap = new HashMap<>();
            for (FlowNode node : flowNodes) {
                nodeMap.put(node.getId(), node);
            }

            // Check if start and end nodes exist
            if (!nodeMap.containsKey(startId) || !nodeMap.containsKey(endId)) {
                System.err.println("Start or end node ID not found in the BPMN diagram.");
                System.exit(-1);
            }

            FlowNode startNode = nodeMap.get(startId);
            FlowNode endNode = nodeMap.get(endId);

            // Find one possible path using BFS to avoid deep recursion
            List<String> path = findPathBFS(startNode, endNode);

            if (path == null || path.isEmpty()) {
                System.err.println("No path found from " + startId + " to " + endId + ".");
                System.exit(-1);
            } else {
                System.out.println("The path from " + startId + " to " + endId + " is:");
                System.out.println(path);
            }

        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
            System.exit(-1);
        }
    }

    /**
     * Fetches the BPMN XML from the remote API.
     *
     * @return BPMN XML as a String, or null if failed.
     */
    private static String fetchBpmnXml() {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                System.err.println("Failed to fetch BPMN XML. HTTP status: " + status);
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder jsonOutput = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonOutput.append(line);
            }

            // Parse JSON to extract bpmn20Xml using Jackson
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonOutput.toString());
            JsonNode bpmnXmlNode = rootNode.get("bpmn20Xml");

            if (bpmnXmlNode == null || bpmnXmlNode.isNull()) {
                System.err.println("bpmn20Xml field is missing in the JSON response.");
                return null;
            }

            String bpmnXml = bpmnXmlNode.asText();

            return bpmnXml;

        } catch (Exception e) {
            System.err.println("Exception while fetching BPMN XML: " + e.getMessage());
            return null;
        } finally {
            try {
                if (reader != null)
                    reader.close();
            } catch (Exception ex) {
                // Ignore
            }
            if (connection != null)
                connection.disconnect();
        }
    }

    /**
     * Finds one possible path from start to end using Breadth-First Search (BFS).
     *
     * @param start Starting FlowNode
     * @param end   Ending FlowNode
     * @return List of node IDs representing the path, or null if no path found.
     */
    private static List<String> findPathBFS(FlowNode start, FlowNode end) {
        Queue<List<FlowNode>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        List<FlowNode> initialPath = new ArrayList<>();
        initialPath.add(start);
        queue.add(initialPath);
        visited.add(start.getId());

        while (!queue.isEmpty()) {
            List<FlowNode> currentPath = queue.poll();
            FlowNode lastNode = currentPath.get(currentPath.size() - 1);

            if (lastNode.equals(end)) {
                // Convert FlowNodes to their IDs
                List<String> pathIds = new ArrayList<>();
                for (FlowNode node : currentPath) {
                    pathIds.add(node.getId());
                }
                return pathIds;
            }

            for (SequenceFlow flow : lastNode.getOutgoing()) {
                FlowNode target = flow.getTarget();
                if (!visited.contains(target.getId())) {
                    visited.add(target.getId());
                    List<FlowNode> newPath = new ArrayList<>(currentPath);
                    newPath.add(target);
                    queue.add(newPath);
                }
            }
        }

        return null; // No path found
    }
}
