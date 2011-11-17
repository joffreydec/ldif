/* 
 * Copyright 2011 Freie Universität Berlin and MediaEvent Services GmbH & Co. K 
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

package ldif.local.config

import java.util.logging.Logger
import java.io.File
import ldif.util.ValidatingXMLReader
import xml.{Node, XML}
import java.util.Properties

case class SchedulerConfig (importJobsDir : File, integrationJob : File, dataSourcesDir : File, dumpLocationDir : File, properties : Properties)  {}

object SchedulerConfig
{
  private val log = Logger.getLogger(getClass.getName)

  private val schemaLocation = "xsd/SchedulerConfig.xsd"

  def empty = SchedulerConfig(null, null, null, null, new Properties)

  def load = new ValidatingXMLReader(fromFile, schemaLocation)

  def fromFile(configFile : File) = {
    val baseDir = configFile.getParent
    val xml = XML.loadFile(configFile)

    // Read in properties
    val propertiesFile = getFile(xml, "properties", baseDir)
    var properties = new Properties
    if (propertiesFile != null)
      properties = ConfigProperties.loadProperties(propertiesFile)

    val dumpLocationDir = getFile(xml, "dumpLocation", baseDir, true)

    val importJobsDir = getFile(xml, "importJobs", baseDir)
    val integrationJobDir = getFile(xml, "integrationJob", baseDir)
    val datasourceJobDir = getFile(xml, "dataSources", baseDir)

    SchedulerConfig(
      importJobsDir,
      integrationJobDir,
      datasourceJobDir,
      dumpLocationDir,
      properties
    )
  }

  private def getFile (xml : Node, key : String, baseDir : String, forceMkdir : Boolean = false) : File = {
    val value : String = (xml \ key text)
    var file : File = null
    if (value != ""){
      val relativeFile = new File(baseDir + "/" + value)
      val absoluteFile = new File(value)
      if (relativeFile.exists || absoluteFile.exists) {
        if (relativeFile.exists)
          file = relativeFile
        else file = absoluteFile
      }
      else {
        log.warning("\'"+key+"\' path not found. Searched: " + relativeFile.getCanonicalPath + ", " + absoluteFile.getCanonicalPath)
        if (forceMkdir) {
          if (relativeFile.mkdirs) {
            file = relativeFile
            log.info("Created new directory at: "+ relativeFile.getCanonicalPath)
          }
          else log.severe("Error creating directory at: " + relativeFile.getCanonicalPath)
        }
      }
    }
    else{
      log.warning("\'"+key+"\' is not defined in the Scheduler config")
    }
    file
  }

}
