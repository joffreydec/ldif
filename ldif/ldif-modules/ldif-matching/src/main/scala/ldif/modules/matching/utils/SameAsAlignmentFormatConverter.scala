/*
 * LDIF
 *
 * Copyright 2011-2012 Freie Universität Berlin, MediaEvent Services GmbH & Co. KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ldif.modules.matching.utils

import ldif.local.runtime.QuadReader
import ldif.local.datasources.dump.QuadFileLoader
import ldif.util.Consts
import collection.mutable.HashSet
import java.io.{FileInputStream, FileWriter, BufferedWriter, File}

/**
 * Created by IntelliJ IDEA.
 * User: andreas
 * Date: 3/1/12
 * Time: 12:15 PM
 * To change this template use File | Settings | File Templates.
 */

object SameAsAlignmentFormatConverter {
  def convertToTTL(input: QuadReader): (Int, String) = {
    var counter = 0
    val sb = new StringBuilder
    sb.append(generateHeaderTTL)
    var first = true
    for(quad<-input) {
      if(quad.predicate==Consts.SAMEAS_URI && quad.subject.value!=quad.value.value) {
        counter += 1
        sb.append(generateCellTTL(quad.subject.value, quad.value.value, first))
        first=false
      }
    }

    sb.append(generateFooterTTL)
    (counter, sb.toString)
  }

  def convertToXML(input: QuadReader): (Int,String) = {
    var counter = 0
    val entitySet = new HashSet[String]
    val sb = new StringBuilder
    sb.append(generateHeaderXML)

    for(quad<-input) {
      val entity1 = quad.subject.value
      val entity2 = quad.value.value
      if(quad.predicate==Consts.SAMEAS_URI) {
        if(!entitySet.contains(entity1) && !entitySet.contains(entity2) && entity1!=entity2) {
          counter += 1
          sb.append(generateCellXML(entity1, entity2))
          entitySet.add(entity1)
          entitySet.add(entity2)
        }
      }
    }

    sb.append(generateFooterXML)
    (counter,sb.toString)
  }

  private def generateHeaderXML: String = {
    val sb = new StringBuilder
    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>").append("\n")
    sb.append("<rdf:RDF xmlns=\"http://knowledgeweb.semanticweb.org/heterogeneity/alignment\"").append("\n")
    sb.append("         xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"").append("\n")
    sb.append("         xmlns:xsd=\"http://www.w3.org/2001/XMLSchema#\">")
    sb.append("\n")
    sb.append("<Alignment>").append("\n")
    sb.append("<xml>yes</xml>").append("\n")
    sb.append("<level>0</level>").append("\n")
    sb.append("<type>??</type>").append("\n")
    sb.append("\n")
    sb.toString()
  }

  private def generateFooterXML: String = {
    val sb = new StringBuilder
    sb.append("</Alignment>").append("\n")
    sb.append("</rdf:RDF>").append("\n")
    sb.toString()
  }

  private def generateCellXML(entity1: String, entity2: String): String = {
    val sb = new StringBuilder
    sb.append("<map>\n    <Cell>\n")
    sb.append("        <entity1 rdf:resource=\"").append(entity1).append("\"/>\n")
    sb.append("        <entity2 rdf:resource=\"").append(entity2).append("\"/>\n")
    sb.append("        <measure rdf:datatype=\"xsd:float\">1.0</measure>\n")
    sb.append("        <relation>=</relation>\n")
    sb.append("    </Cell>\n</map>\n")
    sb.toString()
  }

  def convertToAlignmentFormat(input: QuadReader, outputFile: String, outputXML: Boolean =true) {
    val writer = new BufferedWriter(new FileWriter(outputFile))
    val (count, outputString) = if(outputXML) convertToXML(input) else convertToTTL(input)
    println("Number of alignments found: " + count)
    writer.append(outputString)
    writer.flush()
    writer.close()
  }

  def convertToSameAsNTriples(input: QuadReader, outputFile: String) {
    val writer = new BufferedWriter(new FileWriter(outputFile))
    val matchSet = new HashSet[(String, String)]
    var counter = 0
    for(quad <- input) {
      val subj = quad.subject.value
      val obj = quad.value.value
      if(subj!=obj && !(matchSet.contains((subj,obj)) || matchSet.contains((obj,subj)))) {
        writer.append(quad.toNTripleFormat).append("\n")
        counter += 1
        matchSet.add((subj,obj))
      }
    }
    println("Number of alignments found: " + counter)
    writer.flush()
    writer.close()
  }

  private def generateHeaderTTL: String = {
    val sb = new StringBuilder
    sb.append("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .").append("\n")
    sb.append("@prefix : <http://knowledgeweb.semanticweb.org/heterogeneity/alignment> .").append("\n")
    sb.append("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .").append("\n")
    sb.append("\n")
    sb.append("[]").append("\n")
    sb.append("   a :Alignment ;").append("\n")
    sb.append("   :level \"0\" ;").append("\n")
    sb.append("   :type \"??\" ;").append("\n")
    sb.append("   :xml \"yes\" ;").append("\n")
    sb.append("   :map ")
    sb.toString()
  }

  private def generateFooterTTL: String = {
    " .\n"
  }

  private def generateCellTTL(entity1: String, entity2: String, first: Boolean): String = {
    val sb = new StringBuilder
    if(first)
      sb.append("[\n")
    else
      sb.append(", [\n")
    sb.append("     :entity1 <").append(entity1).append("> ;\n")
    sb.append("     :entity2 <").append(entity2).append("> ;\n")
    sb.append("     :measure \"1.0\"^^<xsd:float> ;\n")
    sb.append("     :relation \"=\" ;\n")
    sb.append("     a :Cell\n")
    sb.append("    ]")
    sb.toString()
  }

  def main(args: Array[String]) {
    if(args.length<2) {
      println("Parameters: input-file output-file")
      sys.exit(1)
    }
    val startTime = System.currentTimeMillis()
    val quadReader = QuadFileLoader.loadQuadsIntoTempFileQuadQueue(new FileInputStream(args(0)))
    convertToAlignmentFormat(quadReader, args(1))
    println("File converted in " + (System.currentTimeMillis()-startTime)/1000.0 + "s")
  }
}

/*output '@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .'
output '@prefix : <http://knowledgeweb.semanticweb.org/heterogeneity/alignment> .'
output '@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .'
output ''

output '[]'
output '   a :Alignment ;'
output '   :level "0" ;'
output '   :type "??" ;'
output '   :xml "yes" ;'
output '   :map ['

count=0;

while read line
do
  if [ `awk '{print $1}' $line` -eq "<http://www.w3.org/2002/07/owl#sameAs>" ]
  then
    if [ $count -eq 0 ]
    then
      output '       ['
    else
      output '     ],['
    fi
    entity1=`awk '{print $1}' $line`
    entity2=`awk '{print $2}' $line`
    output "       :entity1 $entity1"
    output "       :entity2 $entity2"
    output '       :measure "1.0"^^<xsd:float> ;'
    output '       :relation "=" ;'
    output '       a :Cell'
  fi
done <"$input"

output '   ] .'
                             */