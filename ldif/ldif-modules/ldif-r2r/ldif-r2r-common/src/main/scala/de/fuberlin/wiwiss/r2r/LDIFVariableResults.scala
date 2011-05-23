package de.fuberlin.wiwiss.r2r

import scala.collection.mutable.Map
import ldif.entity.{Node, FactumRow}
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by IntelliJ IDEA.
 * User: andreas
 * Date: 10.05.11
 * Time: 14:57
 * To change this template use File | Settings | File Templates.
 */

class LDIFVariableResults() {
	private val variableValues: Map[String, List[Node]] = Map()
  private var bNodes: Map[String, Node] = Map()

	def addVariableResult(varName: String, results: List[Node]): Boolean = {
		if(!variableValues.contains(varName)) {
			variableValues.put(varName, results);
			return true;
		}
		return false;
	}

  def addVariableResult(varName: String, result: Node): Boolean = {
		if(!variableValues.contains(varName)) {
			variableValues.put(varName, List(result));
			return true;
		}
		return false;
	}

	def getResults(varName: String): Option[List[Node]] = {
		variableValues.get(varName)
	}

  def getLexicalResults(varName: String): List[String] = {
    for(node <- variableValues(varName)) yield node.value
  }

  /**
   * returns a unique blank node and binds it to the blank node identifier parameter.
   * Repeated request for the same identifier get the same blank node.
   */
  def getBlankNode(identifier: String, graph: String): Node = {
    if(bNodes.contains(identifier))
      bNodes.get(identifier).get
    else {
      val bNode = Node.createBlankNode(LDIFVariableResults.getBlankNodeID(identifier), graph)
      bNodes.put(identifier, bNode)
      bNode
    }
  }
}

object LDIFVariableResults {
  def counter = new AtomicInteger(1)

  def getBlankNodeID(name: String): String = {
    val id = new StringBuilder
    id ++= System.nanoTime.toString
    id ++= name
    id ++= counter.getAndIncrement.toString
    id.toString
  }

  def getBlankNodeID(): String = {
    val id = new StringBuilder
    id ++= System.nanoTime.toString
    id ++= counter.getAndIncrement.toString
    id.toString
  }
}