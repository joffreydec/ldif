package de.fuberlin.wiwiss.r2r

/**
 * Created by IntelliJ IDEA.
 * User: andreas
 * Date: 19.05.11
 * Time: 12:21
 * To change this template use File | Settings | File Templates.
 */

import org.scalatest.FlatSpec
import ldif.modules.r2r._
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import de.fuberlin.wiwiss.r2r._
import ldif.local.runtime.impl.{QuadQueue, EntityQueue}
import ldif.local.runtime.Quad
import ldif.entity._
import collection.mutable.HashSet
import CreatorHelperFunctions._

@RunWith(classOf[JUnitRunner])
class LDIFMappingTest extends FlatSpec with ShouldMatchers {
  val repository = new Repository(new FileOrURISource("ldif/modules/r2r/testMapping.ttl"))

  it should "be able to rename properties" in {
    val mapping =  getMapping("http://mappings.dbpedia.org/r2r/propertyRenamingMapping", repository)
    val entityQueue = createEntityQueue(mapping.entityDescription)
    val entity = createEntity("TestURI1", mapping)
    entity.addFactumRow(Node.createLiteral("testValue", "default"))
    val quadQueue = new QuadQueue
    mapping.executeMapping(entity, quadQueue)
    quadQueue.read.toString should equal ("Quad(<TestURI1>,p2,\"testValue\",default)")
  }

  it should "be able to convert URIs to Literals" in {
    val mapping =  getMapping("http://mappings.dbpedia.org/r2r/whateverToLiteralMapping", repository)
    val entityQueue = createEntityQueue(mapping.entityDescription)
    val entity = createEntity("TestURI1", mapping)
    entity.addFactumRow(Node.createUriNode("testValue", "default"))
    val quadQueue = new QuadQueue
    mapping.executeMapping(entity, quadQueue)
    quadQueue.read.toString should equal ("Quad(<TestURI1>,p2,\"testValue\",default)")
  }

  it should "pick the correct graph uri from the value nodes" in {
    val mapping =  getMapping("http://mappings.dbpedia.org/r2r/whateverToLiteralMapping", repository)
    val entityQueue = createEntityQueue(mapping.entityDescription)
    val entity = createEntity("TestURI1", mapping)
    entity.addFactumRow(Node.createUriNode("testValue", "myNameSpace"))
    val quadQueue = new QuadQueue
    mapping.executeMapping(entity, quadQueue)
    quadQueue.read.toString should equal ("Quad(<TestURI1>,p2,\"testValue\",myNameSpace)")
  }

  it should "generate blank nodes for the target graph" in {
    val mapping =  getMapping("http://mappings.dbpedia.org/r2r/blankNodeAndPathMapping", repository)
    val entityQueue = createEntityQueue(mapping.entityDescription)
    val entity = createEntity("TestURI1", mapping)
    entity.addFactumRow(Node.createLiteral("testValue", "myNameSpace"))
    val quadQueue = new QuadQueue
    mapping.executeMapping(entity, quadQueue)
    quadQueue.size should equal (2)
    val bn1 = quadQueue.read match{
      case Quad(_, _, bn, _) => bn }
    val bn2 = quadQueue.read match{
      case Quad(bn, _, _, _) => bn }
    bn1 should equal (bn2)
  }
}