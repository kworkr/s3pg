package cs.graphTranslation.npm;

import cs.Main;
import cs.utils.Constants;
import cs.utils.FilesUtil;
import cs.utils.TypesMapper;
import cs.utils.Utils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.jetbrains.annotations.NotNull;
import org.semanticweb.yars.nx.Literal;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.parser.NxParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DataTransUpdatesNpm {
    TypesMapper typesMapper;
    QueryUtilsNeo4j queryUtil;
    Map<String, String> prefixMap;
    long totalLitNodeCount = 0;
    int commitSize = 1000;

    public DataTransUpdatesNpm(String prefixFilePath, String db, String url, String username, String password) {
        this.queryUtil = new QueryUtilsNeo4j(db, url, username, password);
        this.totalLitNodeCount = queryUtil.getTotalLiteralNodeCount();
        Main.logger.info("Total Literal Node Count: " + totalLitNodeCount);
        this.prefixMap = FilesUtil.readCsvToMap(prefixFilePath);
        this.typesMapper = new TypesMapper();
    }

    // Method to read rdf NT file which contains added triples
    public void addData(String filePath) {
        Main.logger.info("Adding data using file: " + filePath);
        StopWatch watch = new StopWatch();
        watch.start();
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        try {
            Files.lines(Path.of(filePath)).forEach(line -> {
                try {
                    Node[] nodes = NxParser.parseNodes(line);
                    Node entityNode = nodes[0];
                    Node predicateNode = nodes[1];
                    Node objectNode = nodes[2];
                    Resource propAsResource = ResourceFactory.createResource(nodes[1].getLabel());
                    // In case this is a new entity, a node will be created.
                    /*if(!nodesSet.contains(entityNode)){
                        if (!isNodeExists(entityNode)) {
                            nodesSet.add(entityNode);
                            //queryUtil.createNodeWithIri(entityNode.getLabel());
                            queries.add(queryUtil.getCypherCreateNodeWithIri(entityNode.getLabel()));
                        }
                    }*/
                    queries.add(queryUtil.getCypherCreateNodeWithIri(entityNode.getLabel()));
                    // In case the objectNode is an IRI, a node and an edge will be created.
                    if (isIri(objectNode)) {
                        if (predicateNode.toString().equals(Constants.RDF_TYPE)) {
                            Resource objectAsResource = ResourceFactory.createResource(objectNode.getLabel());
                            String namespace = objectAsResource.getNameSpace();
                            String prefixedLabel;
                            if (prefixMap.containsKey(namespace)) {
                                prefixedLabel = prefixMap.get(namespace) + "_" + objectAsResource.getLocalName();
                                //System.out.println("Adding Label for Node: " + entityNode.getLabel() + " " + prefixedLabel);
                            } else {
                                String newPrefix = "ns" + (prefixMap.size() + 1);
                                prefixedLabel = newPrefix + "_" + objectAsResource.getLocalName();
                                prefixMap.put(namespace, newPrefix);
                                //System.out.println("Adding Label for Node: (Added new Namespace prefix -> )" + entityNode.getLabel() + " " + prefixedLabel);
                            }
                            //queryUtil.addLabelToNodeWithIri(entityNode.getLabel(), prefixedLabel);
                            queries.add(queryUtil.getCypherAddLabelToNodeWithIri(entityNode.getLabel(), prefixedLabel));
                        } else {
                            /*if(!nodesSet.contains(objectNode)){
                                if (!isNodeExists(objectNode)) {
                                    nodesSet.add(objectNode);
                                    //queryUtil.createNodeWithIri(objectNode.getLabel());
                                    queries.add(queryUtil.getCypherCreateNodeWithIri(objectNode.getLabel()));
                                }
                            }*/
                            queries.add(queryUtil.getCypherCreateNodeWithIri(objectNode.getLabel()));
                            //System.out.println("Create Edge for IRI:" + entityNode.getLabel() + " " + predicateNode.getLabel() + " " + objectNode.getLabel());
                            String prefixedEdge = getPrefixedEdge(propAsResource);
                            //queryUtil.createEdgeBetweenTwoNodes(entityNode.getLabel(), objectNode.getLabel(), prefixedEdge, "property", predicateNode.getLabel());
                            queries.add(queryUtil.getCypherCreateEdgeBetweenTwoNodes(entityNode.getLabel(), objectNode.getLabel(), prefixedEdge, "property", predicateNode.getLabel()));
                        }
                    } else {
                        ObjectParser object = parseObject(objectNode);
                        if (!predicateNode.toString().equals(Constants.RDF_TYPE)) {
                            String prefixedEdge = getPrefixedEdge(propAsResource);
                            //createEdgeForLiteralNode(entityNode, predicateNode, object.value(), object.dataType(), object.cypherType(), object.dataTypeLocalName(), prefixedEdge);
                            totalLitNodeCount++;
                            int id = (int) totalLitNodeCount + 1;
                            //queryUtil.createLiteralObjectNode(id, object.dataType(), object.value(), object.cypherType());
                            //queryUtil.createEdgeBetweenAnIriAndLitNode(entityNode.getLabel(), id, prefixedEdge, "property", predicateNode.getLabel());
                            queries.add(queryUtil.getCypherCreateLiteralObjectNode(id, object.dataType(), object.value(), object.cypherType()));
                            queries.add(queryUtil.getCypherCreateEdgeBetweenAnIriAndLitNode(entityNode.getLabel(), id, prefixedEdge, "property", predicateNode.getLabel()));
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        watch.stop();
        Utils.logTime("DataTransUpdatesNpm -- addData() ", TimeUnit.MILLISECONDS.toSeconds(watch.getTime()), TimeUnit.MILLISECONDS.toMinutes(watch.getTime()));
        System.out.println("Size of Queries: " + queries.size());
        //System.out.println("Batch Queries Execution");
        //queryUtil.executeQueriesInBatches(queries, commitSize);
    }

    //Method to read rdf NT file which contains deleted triples
    public void deleteData(String filePath) {
        Main.logger.info("Deleting data using file: " + filePath);
        StopWatch watch = new StopWatch();
        watch.start();
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        //LinkedHashSet<String> indexQueries = new LinkedHashSet<>();
        try {
            Files.lines(Path.of(filePath)).forEach(line -> {
                try {
                    Node[] nodes = NxParser.parseNodes(line);
                    Node entityNode = nodes[0];
                    Node predicateNode = nodes[1];
                    Node objectNode = nodes[2];
                    Resource propAsResource = ResourceFactory.createResource(predicateNode.getLabel());
                    String prefixedEdge = getPrefixedEdge(propAsResource);
                    if (isIri(objectNode)) {
                        //queryUtil.deleteRelationshipForIriNode(entityNode.getLabel(), predicateNode.getLabel(), prefixedEdge, objectNode.getLabel());
                        queries.add(queryUtil.getCypherDeleteRelationshipForIriNode(entityNode.getLabel(), predicateNode.getLabel(), prefixedEdge, objectNode.getLabel()));
                        //indexQueries.add(queryUtil.getCypherIndexDeleteRelationshipForIriNode(prefixedEdge));
                    }
                    /*else {
                        ObjectParser object = parseObject(objectNode);
                        //queryUtil.deleteRelationshipForLitNode(entityNode.getLabel(), predicateNode.getLabel(), prefixedEdge, object.value);
                        queries.add(queryUtil.getCypherDeleteRelationshipForLitNode(entityNode.getLabel(), predicateNode.getLabel(), prefixedEdge, object.value));
                        //indexQueries.add(queryUtil.getCypherIndexDeleteRelationshipForLitNode(prefixedEdge));
                    }*/
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        watch.stop();
        Utils.logTime("DataTransUpdatesNpm -- deleteData() ", TimeUnit.MILLISECONDS.toSeconds(watch.getTime()), TimeUnit.MILLISECONDS.toMinutes(watch.getTime()));
        System.out.println("Size of Delete Queries: " + queries.size());
        System.out.println("Batch Delete Queries Execution");
        queryUtil.executeQueriesInBatches(getFirstNQueriesAfterM(queries,1000, 10000) , commitSize);
    }

    //Method to read rdf NT files ( A. updated triples with Old values, B. updated triples with New values)
    public void updateData(String filePathA, String filePathB) {
        Main.logger.info("Updating data using files: " + filePathA + " and " + filePathB);
        StopWatch watch = new StopWatch();
        watch.start();
        //LinkedHashSet<String> indexQueries = new LinkedHashSet<>();
        Map<Pair<Node, Node>, List<Node>> updatedTriples = processTripleFiles(filePathA, filePathB);
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        updatedTriples.forEach((key, objectValues) -> {
            Node entityNode = key.getLeft();
            Node predicateNode = key.getRight();
            Resource propAsResource = ResourceFactory.createResource(predicateNode.getLabel());
            String prefixedEdge = getPrefixedEdge(propAsResource);
            if (isIri(objectValues.get(0))) {
                //FIXME:: Not sure this case will ever occur as it will be counted in added triples.
            } else {
                ObjectParser object0 = parseObject(objectValues.get(0));
                ObjectParser object1 = parseObject(objectValues.get(1));

                String oldVal = object0.value();
                String newVal = object1.value();
                if (Utils.isNotWithinDoubleQuotes(oldVal)) {
                    oldVal = "\"" + oldVal + "\"";
                }
                if (Utils.isNotWithinDoubleQuotes(newVal)) {
                    newVal = "\"" + newVal + "\"";
                }
                if (!predicateNode.toString().equals(Constants.RDF_TYPE)) {
                    //queryUtil.updateObjectValueForLitNode(entityNode.getLabel(), prefixedEdge, predicateNode.getLabel(), oldVal, newVal);
                    queries.add(queryUtil.getCypherUpdateObjectValueForLitNode(entityNode.getLabel(), prefixedEdge, predicateNode.getLabel(), oldVal, newVal));
                    //indexQueries.add(queryUtil.getCypherIndexUpdateObjectValueForLitNode( prefixedEdge));
                }
            }
        });
        watch.stop();
        Utils.logTime("DataTransUpdatesNpm -- updateData() ", TimeUnit.MILLISECONDS.toSeconds(watch.getTime()), TimeUnit.MILLISECONDS.toMinutes(watch.getTime()));
        System.out.println("Size of Update Queries: " + queries.size());
        System.out.println("Batch Update Queries Execution");
        queryUtil.executeQueriesInBatches(getFirstNQueriesAfterM(queries,1000, 10000), commitSize);
    }


    // ****************************** Helper Methods ******************************

    @NotNull
    private ObjectParser parseObject(Node objectNode) {
        String value = objectNode.toString();
        Resource dataTypeResource = extractDataType(objectNode);
        String dataType = dataTypeResource.getURI();
        String dataTypeLocalName = dataTypeResource.getLocalName();
        if (objectNode instanceof Literal) {
            if (((Literal) objectNode).getDatatype() != null) {
                value = objectNode.getLabel();
            }
            if (((Literal) objectNode).getLanguageTag() != null) {
                value = value.replaceAll("@" + ((Literal) objectNode).getLanguageTag(), "");
            }
        } else if ((ResourceFactory.createResource(objectNode.toString())).isURIResource()) {
            value = objectNode.getLabel();
            dataTypeLocalName = "IRI";
            dataType = "IRI";
        }
        String cypherType = typesMapper.getMap().get(dataType);
        if (cypherType == null) cypherType = "STRING";
        value = Utils.removeDoubleQuotes(value);
        return new ObjectParser(value, dataType, dataTypeLocalName, cypherType);
    }

    private record ObjectParser(String value, String dataType, String dataTypeLocalName, String cypherType) {
    }

    private Map<Pair<Node, Node>, List<Node>> processTripleFiles(String... filePaths) {
        Map<Pair<Node, Node>, List<Node>> updatedTriples = new HashMap<>();

        for (String filePath : filePaths) {
            try {
                Files.lines(Path.of(filePath)).forEach(line -> {
                    try {
                        Node[] nodes = NxParser.parseNodes(line);
                        Node entityNode = nodes[0];
                        Node predicateNode = nodes[1];
                        Node objectNode = nodes[2];

                        Pair<Node, Node> key = Pair.of(entityNode, predicateNode);
                        updatedTriples.computeIfAbsent(key, k -> new ArrayList<>()).add(objectNode);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return updatedTriples;
    }


    @NotNull
    private String getPrefixedEdge(Resource propAsResource) {
        String prefixedEdge;
        if (prefixMap.containsKey(propAsResource.getNameSpace())) {
            prefixedEdge = prefixMap.get(propAsResource.getNameSpace()) + "_" + propAsResource.getLocalName();
        } else {
            String newPrefix = "ns" + (prefixMap.size() + 1);
            prefixedEdge = newPrefix + "_" + propAsResource.getLocalName();
            prefixMap.put(propAsResource.getNameSpace(), newPrefix);
        }
        return prefixedEdge;
    }

    private boolean isNodeExists(Node node) {
        //System.out.println(node.getLabel() + " node exist? " + exists);
        return queryUtil.nodeExistsWithIri(node.getLabel());
    }

    private void createEdgeForLiteralNode(Node entityNode, Node predicateNode, String value, String dataType, String cypherType, String dataTypeLocalName, String prefixedEdge) {
        totalLitNodeCount++;
        int id = (int) totalLitNodeCount + 1;
        queryUtil.createLiteralObjectNode(id, dataType, value, cypherType);
        queryUtil.createEdgeBetweenAnIriAndLitNode(entityNode.getLabel(), id, prefixedEdge, "property", predicateNode.getLabel());
    }

    private boolean isIri(Node node) {
        return node.toString().startsWith("<") && node.toString().endsWith(">");
    }

    private Resource extractDataType(Node node) {
        Resource literalDataType = ResourceFactory.createResource("http://www.w3.org/2001/XMLSchema#string");

        try {
            if (node instanceof Literal) {
                Literal objAsLiteral = (Literal) node;
                if (objAsLiteral.getDatatype() != null) {
                    literalDataType = ResourceFactory.createResource(objAsLiteral.getDatatype().getLabel());
                }
            } else {
                // Handle the case when the node is not a Literal
                //System.err.println("Error: Node is not a Literal");
                return literalDataType;
            }
        } catch (NullPointerException e) {
            // Handle NullPointerException here
            System.err.println("Error: Node is null or does not have a datatype");
            e.printStackTrace();
            return literalDataType;
        }

        return literalDataType;
    }


    public static LinkedHashSet<String> getFirstNQueries(LinkedHashSet<String> queries, int n) {
        LinkedHashSet<String> q = new LinkedHashSet<>();
        int count = 0;
        for (String query : queries) {
            q.add(query);
            count++;

            if (count == n) {
                break;
            }
        }
        return q;
    }

    public static LinkedHashSet<String> getFirstNQueriesAfterM(LinkedHashSet<String> queries, int n, int m) {
        LinkedHashSet<String> q = new LinkedHashSet<>();
        int count = 0;

        // Iterate from the 10,000th index
        Iterator<String> iterator = queries.iterator();
        for (int i = 0; i < m && iterator.hasNext(); i++) {
            iterator.next();
        }

        while (iterator.hasNext() && count < n) {
            q.add(iterator.next());
            count++;
        }

        return q;
    }


}
