package ldif.local.datasources.dump

import ldif.datasources.dump.DumpTask
import ldif.module.Executor
import org.semanticweb.yars.nx.parser.NxParser
import ldif.local.runtime._
import ldif.local.util.StringPool

/**
 * Executor for the dump data source.
 */
class DumpExecutor() extends Executor
{
  type TaskType = DumpTask
  type InputFormat = NoDataFormat
  type OutputFormat = GraphFormat

  /**
   * Determines the accepted input format of a specific task.
   */
  override def input(task : DumpTask) : NoDataFormat = NoDataFormat()

  /**
   * Determines the output format of a specific task.
   */
  override def output(task : DumpTask) : GraphFormat = GraphFormat()

  /**
   * Executes a specific task.
   *
   * @param task The task to be executed
   * @param reader The reader of the input data
   * @param writer The writer of the output data
   */
  override def execute(task : DumpTask, reader : Null, writer : QuadWriter)
  {
    val inputStream = new DumpLoader(task.sourceLocation).getStream

    val nxp:NxParser = new NxParser(inputStream)
    while (nxp.hasNext) {
      val ns:Array[org.semanticweb.yars.nx.Node] = nxp.next
      val graph = StringPool.getCanonicalVersion(task.name)
      val subj = LocalNode.fromNxNode(ns(0),graph)                           
      val prop = StringPool.getCanonicalVersion(ns(1).toString)
      val obj = LocalNode.fromNxNode(ns(2),graph)
      writer.write(new Quad(subj,prop,obj,graph))
    }
    
    writer.finish
  }
}