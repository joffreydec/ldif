package ldif.local.datasources.dump

import java.util.regex.Pattern

object ContentTypes {

  val CONTENTTYPES_RDFXML = Seq("application/rdf+xml" /* official */, "text/rdf+xml", "text/xml", "application/xml")
  val CONTENTTYPES_RDFN3 = Seq("text/n3" /* proposed */, "application/n3", "text/rdf+n3")
  val CONTENTTYPES_RDFTTL = Seq("text/turtle" /* proposed */, "application/x-turtle" /* pre-registration */, "application/turtle")
  val CONTENTTYPES_RDFNTRIPLE = Seq("text/plain")
  /* Note: This is provided redundantly so that it qualifies as a constant expression */
	val CONTENTTYPES_RDF = "application/rdf+xml,text/rdf+xml,text/xml,application/xml,text/n3,application/n3,text/rdf+n3,text/turtle,application/x-turtle,application/turtle,text/plain"


  // from com.hp.hpl.jena.util.FileUtils
  val langXML = "RDF/XML"
  val langXMLAbbrev = "RDF/XML-ABBREV"
  val langNTriple = "N-TRIPLE"
  val langN3 = "N3"
  val langTurtle = "TURTLE"

  val HTTP_ACCEPT_CONTENT_TYPES =  "text/plain;q=1," +  /* N-TRIPLE */
          "text/n3;q=0.9,application/n3;q=0.9,text/rdf+n3;q=0.9,"+  /* N3 */
          "text/turtle;q=0.9,application/x-turtle;q=0.9,application/turtle;q=0.9," +  /* TURTLE */
          "application/rdf+xml;q=0.7,text/rdf+xml;q=0.7,application/xml;q=0.5,text/xml;q=0.5";  /* RDF/XML */
  
  private def check(needle:String, haystack:Traversable[String]) = {
		var result:Boolean = false
    for (element <- haystack) {
			if (needle.startsWith(element)) {
				result = true
			}
		}
		result
	}

  /**
	 * see http://www.w3.org/TR/REC-rdf-syntax/#section-MIME-Type
	 * @param contentType
	 * @return	true, if the provided content type is indicative of RDF/XML content
	 */
	def isRDFXML(contentType:String) = check(contentType, CONTENTTYPES_RDFXML)
	
	/**
	 * @see	http://www.w3.org/DesignIssues/Notation3.html
	 * @param contentType
	 * @return	true, if the provided content type is indicative of RDF/N3 content
	 */
	def isRDFN3(contentType:String) = check(contentType, CONTENTTYPES_RDFN3)
	
	/**
	 * @see	http://www.w3.org/TeamSubmission/turtle/
	 * @param contentType
	 * @return	true, if the provided content type is indicative of RDF/TTL content
	 */
	def isRDFTTL(contentType:String) = check(contentType, CONTENTTYPES_RDFTTL)

  /**
	 * @param contentType
	 * @return	true, if the provided content type is indicative of RDF content
	 */
	def isRDFNTriple(contentType:String) = check(contentType, CONTENTTYPES_RDFNTRIPLE)

	 /**
	 * Translates a given content type to Jena lang
	 * @param contentType
	 * @return
	 */
	def jenaLangFromContentType(contentType:String):String = {
		var result:String = null 
    if (contentType == null || isRDFXML(contentType))
			result = langXML
		else if (isRDFN3(contentType))
			result = langN3
		else if (isRDFNTriple(contentType))
			result = langNTriple
		else if (isRDFTTL(contentType))
			result = langTurtle
    result
	}

  	/**
	 * Guesses a content type based on the file extension in an URL or a local path
	 * Supports chained extensions, e.g. somefile.nt.bz2
	 * @param urlOrPath
	 */
	def jenaLangFromExtension(urlOrPath:String):String = {
		var result:String = null
    if (Pattern.matches(".*\\.(rdf|rdfxml|xml)(\\..*)?", urlOrPath))
			result = langXML
		else if (Pattern.matches(".*\\.n3(\\..*)?", urlOrPath))
			result = langN3
		else if (Pattern.matches(".*\\.nt(\\..*)?", urlOrPath))
			result = langNTriple
		else if (Pattern.matches(".*\\.ttl(\\..*)?", urlOrPath))
      result = langTurtle
		result
  }

}
