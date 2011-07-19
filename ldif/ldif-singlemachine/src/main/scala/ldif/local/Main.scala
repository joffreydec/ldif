package ldif.local

import datasources.dump.DumpExecutor
import runtime._
import impl._
import ldif.modules.silk.SilkModule
import ldif.datasources.dump.{DumpModule, DumpConfig}
import ldif.modules.silk.local.SilkLocalExecutor
import de.fuberlin.wiwiss.ldif.{EntityBuilderModule, EntityBuilderConfig}
import de.fuberlin.wiwiss.r2r._
import ldif.modules.r2r.local.R2RLocalExecutor
import ldif.modules.r2r._
import ldif.entity.EntityDescription
import de.fuberlin.wiwiss.ldif.local.EntityBuilderExecutor
import ldif.local.util.Const._
import java.util.Properties
import java.io._
import java.util.logging.Logger
import util.StringPool

object Main
{
  private val log = Logger.getLogger(getClass.getName)
  // Object to store all kinds of configuration data
  private var configParameters: ConfigParameters = null

  def main(args : Array[String])
  {
    var debug = false
    if(args.length<1) {
      println("No configuration file given.")
      System.exit(1)
    }
    else if(args.length>=2 && args(0)=="--debug")
      debug = true

    //val configUrl = getClass.getClassLoader.getResource("ldif/local/example/test2/config.xml")
    //val configFile = new File(configUrl.toString.stripPrefix("file:"))
    val configFile = new File(args(args.length-1))
    runIntegrationFlow(configFile, debug)
  }

  private def setupQuadReader(_clonedR2rReader: QuadReader): QuadReader = {
    var clonedR2rReader: QuadReader = _clonedR2rReader
    if (clonedR2rReader.isInstanceOf[FileQuadReader]) {
      clonedR2rReader.asInstanceOf[FileQuadReader].close()
      clonedR2rReader = new FileQuadReader(clonedR2rReader.asInstanceOf[FileQuadReader].inputFile)
    }
    clonedR2rReader
  }

  def runIntegrationFlow(configFile: File, debugMode: Boolean) {
    stopWatch.getTimeSpanInSeconds
    val config: LdifConfiguration = loadConfigFile(configFile)

    // Setup config properties file
    configProperties.loadPropertyFile(config.propertiesFile)

     // Validate configuration
    val fail = ConfigValidator.validateConfiguration(config)
    if(fail) {
      println("!- Validation phase failed")
      exit(1)
    } else {
      println("-- Validation phase succeeded in " + stopWatch.getTimeSpanInSeconds + "s")
    }

    // Quads that are not used in the integration flow, but should still be output
    val otherQuadsFile = File.createTempFile("ldif-other-quads", ".bin")
    setupConfigParameters(otherQuadsFile)

    val quadReaders = loadDump(config.sources)

    val r2rReader: QuadReader = executeMappingPhase(config, quadReaders)

    if(debugMode==true)
      writeDebugOutput("r2r", config.outputFile, r2rReader)

    var clonedR2rReader = r2rReader
    if(clonedR2rReader.isInstanceOf[QuadQueue])//TODO: Redesign QuadReader interface to be generally clonable
      clonedR2rReader = cloneQuadQueue(clonedR2rReader.asInstanceOf[QuadQueue])

    val linkReader: QuadReader = executeLinkingPhase(config, r2rReader)

    if(debugMode==true)
      writeDebugOutput("silk", config.outputFile, linkReader)

    configParameters.otherQuadsWriter.finish
    val otherQuadsReader = new FileQuadReader(otherQuadsFile)

    clonedR2rReader = setupQuadReader(clonedR2rReader)

    val allQuads = new MultiQuadReader(clonedR2rReader, otherQuadsReader)

    var integratedReader: QuadReader = allQuads

    if(configProperties.getPropertyValue("rewriteURIs", "true").toLowerCase=="true")
      integratedReader = executeURITranslation(allQuads, linkReader)

    //OutputValidator.compare(cloneQuadQueue(integratedReader),new File(configFile.getParent+"/../results/all-mes.nt"))

    writeOutput(config.outputFile, integratedReader)
  }



  def setupConfigParameters(outputFile: File) {
    var otherQuads: QuadWriter = new FileQuadWriter(outputFile)

    // Setup config parameters
    configParameters = ConfigParameters(configProperties, otherQuads)

    // Setup LocalNode (to pool strings etc.)
    LocalNode.reconfigure(configProperties)
  }

  private def executeMappingPhase(config: LdifConfiguration, quadReaders: Seq[QuadReader]): QuadReader = {
    val r2rReader: QuadReader = mapQuads(config.mappingFile, quadReaders)
    println("Time needed to map data: " + stopWatch.getTimeSpanInSeconds + "s")

    r2rReader
  }

  private def executeLinkingPhase(config: LdifConfiguration, r2rReader: QuadReader): QuadReader = {
    val linkReader = generateLinks(config.linkSpecDir, r2rReader)
    println("Time needed to link data: " + stopWatch.getTimeSpanInSeconds + "s")
    println("Number of links generated by silk: " + linkReader.size)
    linkReader
  }

  private def loadConfigFile(configFile: File): LdifConfiguration = {
    val config = LdifConfiguration.load(configFile)
    println("Time needed to load config file: " + stopWatch.getTimeSpanInSeconds + "s")
    config
  }

  private def executeURITranslation(inputQuadReader: QuadReader, linkReader: QuadReader): QuadReader = {
    val integratedReader = URITranslator.translateQuads(inputQuadReader, linkReader)

    println("Time needed to translate URIs: " + stopWatch.getTimeSpanInSeconds + "s")
    integratedReader
  }

  def runIntegrationFlow(configFile: File) {
    runIntegrationFlow(configFile, false)
  }

  /**
   * Loads the dump files.
   */
  def loadDump(sources : Traversable[String]) : Seq[QuadReader] =
  {
    val dumpModule = new DumpModule(new DumpConfig(sources))
    val dumpExecutor = new DumpExecutor

    val quadQueues = for (i <- 1 to dumpModule.tasks.size) yield new BlockingQuadQueue(DEFAULT_QUAD_QUEUE_CAPACITY)


      for((dumpTask, writer) <- dumpModule.tasks.toList zip quadQueues){
        runInBackground
          {
            dumpExecutor.execute(dumpTask, null, writer)
          }
      }
    quadQueues.toSeq
  }


  /**
   * Transforms the Quads
   */
  private def mapQuads(mappingFile: File, readers: Seq[QuadReader]) : QuadReader = {
    val repository = new Repository(new FileOrURISource(mappingFile))
    val executor = new R2RLocalExecutor
    val config = new R2RConfig(repository)
    val module = new R2RModule(config)

    val entityDescriptions = for(task <- module.tasks) yield task.mapping.entityDescription
    val entityReaders = buildEntities(readers, entityDescriptions.toSeq, configParameters)
    StringPool.reset
    println("Time needed to load dump and build entities for mapping phase: " + stopWatch.getTimeSpanInSeconds + "s")
    //println("Number of triples after loading the dump: " + (quadReaders.foldLeft(0)(_ + _.totalSize)))

    val outputFile = File.createTempFile("ldif-mapped-quads", ".bin")
    outputFile.deleteOnExit
    val writer = new FileQuadWriter(outputFile)

    //runInBackground
    {
      for((r2rTask, reader) <- module.tasks.toList zip entityReaders)
        executor.execute(r2rTask, Seq(reader), writer)
    }
    writer.finish
    new FileQuadReader(outputFile)
  }

  /**
   * Generates links.
   */
  private def generateLinks(linkSpecDir : File, reader : QuadReader) : QuadReader =
  {
    val silkModule = SilkModule.load(linkSpecDir)
    val silkExecutor = new SilkLocalExecutor

    val entityDescriptions = silkModule.tasks.toIndexedSeq.map(silkExecutor.input).flatMap{ case StaticEntityFormat(ed) => ed }
    val entityReaders = buildEntities(Seq(reader), entityDescriptions, ConfigParameters(configProperties, otherQuadsWriter = null))
    println("Time needed to build entities for linking phase: " + stopWatch.getTimeSpanInSeconds + "s")

    val outputQueue = new QuadQueue

    //runInBackground
    {
      for((silkTask, readers) <- silkModule.tasks.toList zip entityReaders.grouped(2).toList)
      {
        silkExecutor.execute(silkTask, readers, outputQueue)
      }
    }

    outputQueue
  }

  /**
   * Build Entities.
   */
  private def buildEntities(readers : Seq[QuadReader], entityDescriptions : Seq[EntityDescription], configParameters: ConfigParameters) : Seq[EntityReader] =
  {
    val entityQueues = entityDescriptions.map(new EntityQueue(_))

    try
    {
      val entityBuilderConfig = new EntityBuilderConfig(entityDescriptions.toIndexedSeq)
      val entityBuilderModule = new EntityBuilderModule(entityBuilderConfig)
      val entityBuilderTask = entityBuilderModule.tasks.head
      val entityBuilderExecutor = new EntityBuilderExecutor(configParameters)

      entityBuilderExecutor.execute(entityBuilderTask, readers, entityQueues)
    } catch {
      case e: Throwable => {
        e.printStackTrace
        exit(2)
      }
    }

    entityQueues
  }

  /**
   * Evaluates an expression in the background.
   */
  private def runInBackground(function : => Unit) {
    val thread = new Thread {
      private val listener: FatalErrorListener = fatalErrorListener

      override def run() {
        try {
          function
        } catch {
          case e: Exception => listener.reportError(e)
        }
      }
    }
    thread.start()
  }

  //TODO we don't have an output module yet...
  private def writeOutput(outputFile : File, reader : QuadReader)    {
    val writer = new FileWriter(outputFile)
    var count = 0

    while(reader.hasNext) {
      writer.write(reader.read().toNQuadFormat + " .\n")
      count += 1
    }

    writer.close()
    println(count + " Quads written")
  }

  private def writeDebugOutput(phase: String, outputFile: File, reader: QuadReader) {
    if(reader.isInstanceOf[QuadQueue] && !reader.isInstanceOf[BlockingQuadQueue]) {
      val newOutputFile = new File(outputFile.getAbsolutePath + "." + phase)
      val clonedReader = cloneQuadQueue(reader.asInstanceOf[QuadQueue])
      if(clonedReader!=null)
        writeOutput(newOutputFile, clonedReader)
    }
  }

  // Only clone QuadQueue implementation of QuadReader
  def cloneQuadQueue(queue: QuadQueue): QuadReader = {
      queue.clone
  }
}

object stopWatch {
  private var lastTime = System.currentTimeMillis

  def getTimeSpanInSeconds(): Double = {
    val newTime = System.currentTimeMillis
    val span = newTime - lastTime
    lastTime = newTime
    span / 1000.0
  }
}

object configProperties extends ConfigProperties {
  private val log = Logger.getLogger(getClass.getName)

  override def loadPropertyFile(propertyFile: File) {
    properties = new Properties()
    if(propertyFile!=null)
      try {
        val stream = new BufferedInputStream(new FileInputStream(propertyFile));
        properties.load(stream);
        stream.close();
      } catch {
        case e: IOException => {
          log.severe("No property file found at: " + propertyFile.getAbsoluteFile)
          System.exit(1)
        }
      }
  }

  override def getPropertyValue(property: String): String = {
    properties.getProperty(property)
  }

  override def getPropertyValue(property: String, default: String): String = {
    properties.getProperty(property, default)
  }
}

object fatalErrorListener extends FatalErrorListener {
  def reportError(e: Exception) {
    e.printStackTrace
    System.exit(1)
  }
}

trait FatalErrorListener {
  def reportError(e: Exception)
}