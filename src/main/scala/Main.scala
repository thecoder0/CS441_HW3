package com.natalia

import NetGraphAlgebraDefs.{NetGraph, NodeObject}
import com.google.common.graph.EndpointPair
import com.lsc.Main.logger
import org.apache.spark.graphx.{Graph, *}
import org.apache.spark.{SparkConf, SparkContext}
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.RichOptional

import java.util.stream.Collectors

object Main {
  def main(args: Array[String]): Unit = {
//    val conf = new SparkConf().setAppName("GraphGame").setMaster("local[4]")
//    val sc = new SparkContext(conf)
//    loadGraphsAndStartGame(sc)
//
//    sc.stop()

  }

//  def loadGraphsAndStartGame(sc: SparkContext): Unit = {
//    // 1.) Load original NetGraph
//    val originalNetGraph = loadNetGraph(true)
//
//    // 2.) Convert original NetGraph into GraphX object
//    val originalGraphX = loadGraphX(originalNetGraph, sc, true)
//
//    // 3.) Find all nodes in the original graph that have valuable data
//    val originalValuableNodes: List[VertexId] = originalNetGraph.sm.nodes().stream()
//      .filter(node => node.valuableData)
//      .map(node => node.id.toLong)
//      .collect(Collectors.toList[VertexId])
//      .asScala.toList
//
//    logger.info(s"Nodes with valuable data from ORIGINAL (total ${originalValuableNodes.size}): ${originalValuableNodes}")
//
//    // 4.) Load perturbed NetGraph
//    val perturbedNetGraph = loadNetGraph(false)
//
//    // 5.) Convert perturbed NetGraph into GraphX object
//    val perturbedGraphX = loadGraphX(perturbedNetGraph, sc, false)
//
//    val broadcastOriginalGraphX = sc.broadcast(originalGraphX)
//    val broadcastPerturbedGraphX = sc.broadcast(perturbedGraphX)
//
//    val random = new scala.util.Random
//    val numNodesToPlace = 5 // Adjust this based on your requirements
//
//    // Get random nodes from the original graph
//    val originalVertices = broadcastOriginalGraphX.value.vertices.collect()
//    val randomNodes = Seq.fill(numNodesToPlace)(originalVertices(random.nextInt(originalVertices.length))._1)
//
//    // Place Policeman at selected nodes in broadcastOriginalGraphX
//    val policemanNodes = randomNodes.map(nodeId => (nodeId, "Policeman"))
//
//    // Place Thief at random nodes in broadcastOriginalGraphX
//    val thiefNodes = Seq.fill(numNodesToPlace) {
//      val randomNode = originalVertices(random.nextInt(originalVertices.length))._1
//      (randomNode, "Thief")
//    }
//  }
//
//  def loadNetGraph(isOriginal: Boolean): NetGraph = {
//    val resourcePath = if (isOriginal) Configuration.getOriginalGraphPath else Configuration.getPerturbedGraphPath
//    val netGraphPath = Main.getClass.getResource(resourcePath).getPath
//    val netGraphLoaded = NetGraph.load(netGraphPath, dir = "")
//    val originalOrPerturbedText = if (isOriginal) "ORIGINAL" else "PERTURBED"
//
//    if (netGraphLoaded.isEmpty) {
//      val errorMessage = s"Loading ${originalOrPerturbedText} NetGraph FAILED - failing the job."
//      logger.error(errorMessage)
//      throw new RuntimeException(errorMessage)
//    }
//
//    logger.info(s"Loading ${originalOrPerturbedText} NetGraph SUCCEEDED")
//
//    val netGraph = netGraphLoaded.get
//    logger.info(s"${originalOrPerturbedText} graph has ${netGraph.totalNodes} nodes")
//    logger.info(s"${originalOrPerturbedText} graph has ${netGraph.sm.edges().size()} edges")
//
//    netGraph
//  }
//
//  def loadGraphX(netGraph: NetGraph, sc: SparkContext, isOriginal: Boolean): Graph[NodeObject, Double] = {
//    // get GraphX vertices
//
//    val verticesList = netGraph.sm.nodes().asScala.toList
//      .map((node) => (node.id.toLong, node))
//
//    val verticesRDD = sc.parallelize(verticesList)
//
//    // Get perturbed GraphX edges
//    val edgesList = netGraph.sm.edges().asScala.toList
//      .map((edgeAsEndpointPair) => generateGraphXEdge(netGraph, edgeAsEndpointPair))
//
//    val edgesRDD = sc.parallelize(edgesList)
//
//    // Construct GraphX graph
//    val graphX = Graph(verticesRDD, edgesRDD)
//
//    graphX.subgraph()
//
//    // Confirm that we've successfully constructed a GraphX graph
//    val originalOrPerturbedText = if (isOriginal) "ORIGINAL" else "PERTURBED"
//    logger.info(s"the ${originalOrPerturbedText} GraphX has ${graphX.vertices.count()} nodes")
//    logger.info(s"the ${originalOrPerturbedText} GraphX has ${graphX.edges.count()} edges")
//
//    graphX
//  }
//
//  def generateGraphXEdge(originalGraph: NetGraph, edgeAsEndpointPair: EndpointPair[NodeObject]): Edge[Double] = {
//    val sourceNodeId = edgeAsEndpointPair.source().id
//    val destinationNodeId = edgeAsEndpointPair.target().id
//    val edgeValue = originalGraph.sm.edgeValue(edgeAsEndpointPair)
//
//    if (edgeValue.toScala.isEmpty) {
//      val errorMessage = s"Failed to read edge value from node ${sourceNodeId} to node ${destinationNodeId}"
//      logger.error(errorMessage)
//      throw new RuntimeException(errorMessage)
//    }
//
//    Edge(sourceNodeId, destinationNodeId, edgeValue.get().cost)
//  }
}
