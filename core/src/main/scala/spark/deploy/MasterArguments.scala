package spark.deploy

import spark.util.IntParam
import spark.Utils

/**
 * Command-line parser for the master.
 */
class MasterArguments(args: Array[String]) {
  var ip: String = Utils.localIpAddress()
  var port: Int = 7077
  var webUiPort: Int = 8080

  parse(args.toList)

  def parse(args: List[String]): Unit = args match {
    case ("--ip" | "-i") :: value :: tail =>
      ip = value
      parse(tail)

    case ("--port" | "-p") :: IntParam(value) :: tail =>
      port = value
      parse(tail)

    case "--webui-port" :: IntParam(value) :: tail =>
      webUiPort = value
      parse(tail)

    case ("--help" | "-h") :: tail =>
      printUsageAndExit(0)

    case Nil => {}

    case _ =>
      printUsageAndExit(1)
  }

  /**
   * Print usage and exit JVM with the given exit code.
   */
  def printUsageAndExit(exitCode: Int) {
    System.err.println(
      "Usage: spark-master [options]\n" +
        "\n" +
        "Options:\n" +
        "  -i IP, --ip IP         IP address or DNS name to listen on\n" +
        "  -p PORT, --port PORT   Port to listen on (default: 7077)\n" +
        "  --webui-port PORT      Port for web UI (default: 8080)\n")
    System.exit(exitCode)
  }
}