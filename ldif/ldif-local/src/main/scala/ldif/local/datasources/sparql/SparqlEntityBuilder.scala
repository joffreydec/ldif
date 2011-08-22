package ldif.local.datasources.sparql

import ldif.util.EntityDescriptionToSparqlConverter
import ldif.local.runtime.EntityWriter
import ldif.entity.EntityDescription
import com.hp.hpl.jena.query.{ARQ, QueryExecutionFactory, QueryFactory}
import ldif.local.util.JenaResultSetEntityBuilderHelper

class SparqlEntityBuilder(endpointUrl : String) {

  // Build entities and write those into the EntityWriter
  def buildEntities (ed : EntityDescription, writer : EntityWriter) {
    // workaround - see http://stackoverflow.com/questions/5581769/sparql-xml-results-from-dbpedia-and-jena
    ARQ.getContext().setTrue(ARQ.useSAX)

    val queriesString = EntityDescriptionToSparqlConverter.convert(ed,true)
    val queries = for(queryString <- queriesString) yield  QueryFactory.create(queryString._1)

    val resultSets = for(query <- queries) yield QueryExecutionFactory.sparqlService(endpointUrl, query).execSelect
    JenaResultSetEntityBuilderHelper.buildEntitiesFromResultSet(resultSets, ed, writer, "<http://default>")
    //writer.finish
  }

}